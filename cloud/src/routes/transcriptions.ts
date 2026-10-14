import { raise } from '../alerts';
import { transcribe } from '../ai';
import { estimateSeconds, probeDuration, shortestPossibleSeconds } from '../audio';
import { authenticate, touch } from '../auth';
import { neuronsToNano, transcribeCostNano, type Env, type Limits } from '../config';
import { budgetAllows, logUsage, settleBudget, walletStub } from '../meter';
import { debitPaid } from '../subscriptions';
import { NO_STORE, apiError } from '../util';
import type { DebitReason, WalletState } from '../wallet';

/**
 * `POST /v1/audio/transcriptions` — the path the money flows down.
 *
 * The order is intent, not taste:
 *   1. Who are you            → otherwise 401
 *   2. How long is the audio  → from the WAV header, not from a client claim
 *   3. May the service still  → daily cap
 *   4. Deduct                 → **before** the model, or every abort is on the operator: from
 *                               today's Plus allowance, or from credit bought before Plus existed
 *   5. Forward
 *   6. Refund on failure, correct to the real duration on success
 *
 * Step 5 goes to Groq or to Workers AI, whichever the model name belongs to (see ai.ts).
 * Everything around it is the same for both, because none of it ever depended on who did the
 * transcribing.
 */
export async function handleTranscription(
  request: Request,
  env: Env,
  ctx: ExecutionContext,
  limits: Limits,
): Promise<Response> {
  const started = Date.now();
  const session = await authenticate(request, env);
  if (!session) {
    return apiError(401, 'No valid credit token.', 'invalid_token', 'invalid_request_error');
  }
  touch(env, session, ctx);

  // `formData()` holds the file in memory. At ten minutes that is about 19 MB against a
  // 128 MB budget — acceptable, and it saves writing a multipart parser just to reach the
  // first 44 bytes.
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    return apiError(400, 'Request is not valid multipart/form-data.', 'bad_request', 'invalid_request_error');
  }

  const file = form.get('file');
  if (!(file instanceof File)) {
    return apiError(400, 'Field "file" is missing.', 'missing_file', 'invalid_request_error');
  }

  // Read from the file itself where the format says so — WAV, MP3, MP4/M4A, Ogg/Opus, FLAC all
  // state it in a header and none of them needs decoding. Only an unrecognised container falls back
  // to the estimate, and then only to decide how much credit to hold.
  const duration = (await probeDuration(file)) ?? estimateSeconds(file.size);

  // Refused only when the file cannot be within the limit however it is encoded — for a WAV that is
  // the header's own figure, for anything else the shortest length its size allows. Using the
  // generous estimate here turned an ordinary three-minute song into "eighteen minutes, too long".
  const tooLong = duration.exact
    ? duration.seconds > limits.maxAudioSeconds
    : shortestPossibleSeconds(file.size) > limits.maxAudioSeconds;

  if (tooLong) {
    // The app turns 413 into CONTENT_SIZE_LIMIT and offers to keep the recording — exactly
    // the right response to "too long".
    logRefusal(env, session, 'transcribe', 413, started, ctx);
    return apiError(
      413,
      `Recording is longer than ${Math.floor(limits.maxAudioSeconds / 60)} minutes.`,
      'audio_too_long',
      'invalid_request_error',
    );
  }

  // Held up front, and never more than the longest recording allowed: the estimate assumes speech
  // at 32 kbit/s, so a better-encoded file reads several times its true length and would demand
  // credit for minutes it does not contain. Corrected to the real duration once the model reports it.
  const chargedSeconds = Math.min(Math.ceil(duration.seconds), limits.maxAudioSeconds);
  const estimateNano = transcribeCostNano(chargedSeconds, limits.transcribeModel);

  if (!(await budgetAllows(env, limits, estimateNano, ctx))) {
    // Recorded like any other outcome. A refusal is the one event a support message is most
    // likely to be about — "it stopped working on Tuesday" is answerable from a row and not from
    // an absence — and it costs nothing to keep: no seconds, no money, and not counted as a fault.
    logRefusal(env, session, 'transcribe', 503, started, ctx);
    return apiError(
      503,
      'Voice Mode Cloud is unavailable right now. Please try again later.',
      'service_paused',
      'server_error',
    );
  }

  const wallet = walletStub(env, session.walletId);
  const debit = await debitPaid(env, session.walletId, chargedSeconds, limits);
  if (!debit.ok) {
    settleBudget(env, -estimateNano, ctx);
    const refusal = debitError(debit.reason, debit.state);
    logRefusal(env, session, 'transcribe', refusal.status, started, ctx, debit.state);
    return refusal;
  }

  const field = (key: string): string | undefined => {
    const value = form.get(key);
    return typeof value === 'string' && value.trim() ? value : undefined;
  };
  const upstream = await transcribe(env, limits, file, { language: field('language'), prompt: field('prompt') });

  if (upstream.kind === 'unreachable') {
    await wallet.refund(chargedSeconds, debit.booking);
    settleBudget(env, -estimateNano, ctx);
    return apiError(502, 'The transcription service is unreachable.', 'upstream_unreachable', 'server_error');
  }

  if (upstream.kind === 'failed') {
    // Nothing delivered, nothing charged.
    const state = await wallet.refund(chargedSeconds, debit.booking);
    settleBudget(env, -estimateNano, ctx);
    logUsage(env, {
      walletId: session.walletId,
      tokenHash: session.tokenHash,
      isTest: session.isTest,
      kind: 'transcribe',
      seconds: 0,
      costNano: 0,
      status: upstream.status,
      ms: Date.now() - started,
      secondsLeft: state.secondsLeft,
      rewordsLeft: state.rewordsLeft,
      secondsUsedTotal: state.secondsUsed,
    }, ctx);
    return upstreamFailure(upstream.status);
  }

  const body = upstream.body;

  // For WAV the duration was already exact. Other formats were estimated generously — if
  // the provider reports the real length, the difference goes back.
  let finalSeconds = chargedSeconds;
  let state = debit.state;
  if (!duration.exact) {
    const reported = upstream.reportedSeconds;
    if (reported !== null) {
      const actual = Math.ceil(reported);
      const delta = actual - chargedSeconds;
      if (delta !== 0) {
        state = await wallet.adjust(delta, debit.booking);
        finalSeconds = actual;
      }
    } else {
      // The whole billing of non-WAV audio rests on this field. Without it the estimate stands, and
      // the estimate assumes 32 kbit/s — generous for an ordinary recording, so honest uploads are
      // simply over-charged and the correction that would have refunded them never runs. A file
      // deliberately encoded below that rate goes the other way and is billed short.
      //
      // Either way the number is wrong and nothing else would say so, which is why this reports
      // rather than guesses. Once every six hours per model is enough: this is a change at
      // Cloudflare, not an event — it either happens for every request or for none.
      ctx.waitUntil(raise(env, {
        kind: 'audio_duration_missing',
        severity: 'critical',
        value: chargedSeconds,
        title: 'Das Diktiermodell meldet die Audiolänge nicht mehr',
        detail:
          `Eine Aufnahme, deren Länge sich nicht aus dem Dateikopf lesen ließ, wurde nach Größe ` +
          `geschätzt (${chargedSeconds} s) — und die Antwort enthielt kein ` +
          `Längenfeld (\`duration\`), mit dem sich das hätte richtigstellen lassen. Bis dahin galt dieses ` +
          `Feld als gesetzt; fehlt es dauerhaft, wird jede Datei außer WAV falsch abgerechnet: ` +
          `gewöhnliche Aufnahmen zu teuer, absichtlich niedrig kodierte zu billig. Betroffen ist ` +
          `nur, was über die Dateiauswahl kommt — die App selbst nimmt WAV auf, und dort steht die ` +
          `Länge exakt im Kopf. Zu prüfen: das Antwortformat von \`${limits.transcribeModel}\` und ` +
          `ob der Anbieter die Angabe umbenannt hat.`,
        dedupeKey: `audio_duration_missing:${limits.transcribeModel}`,
      }, ctx).then(() => undefined));
    }
  }

  // What it really cost to buy. Workers AI reports the neurons it spent, and that is the figure to
  // keep: a measurement rather than our multiplication. Groq reports no price, so its cost is the
  // length times its price list, ten-second minimum included (config.ts, GROQ_PRICES).
  //
  // The day's budget is settled against exactly this number rather than against the duration, so
  // the guard and the ledger cannot drift apart.
  const actualNano = upstream.neurons > 0
    ? neuronsToNano(upstream.neurons)
    : transcribeCostNano(finalSeconds, limits.transcribeModel);
  settleBudget(env, actualNano - estimateNano, ctx);

  logUsage(env, {
    walletId: session.walletId,
    tokenHash: session.tokenHash,
    isTest: session.isTest,
    kind: 'transcribe',
    seconds: finalSeconds,
    neuronsMicro: Math.round(upstream.neurons * 1_000_000),
    costNano: actualNano,
    status: 200,
    ms: Date.now() - started,
    secondsLeft: state.secondsLeft,
    rewordsLeft: state.rewordsLeft,
    secondsUsedTotal: state.secondsUsed,
  }, ctx);

  return new Response(body, {
    status: 200,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      ...NO_STORE,
      // So the app can show the balance later without asking separately.
      ...usageHeaders(state),
    },
  });
}

/**
 * A request that was turned away, in the ledger.
 *
 * Zero seconds and zero cost, because nothing was bought — the row exists so the refusal can be
 * found later. Shares `logUsage` rather than a table of its own: the traffic view is where anyone
 * would look, and a refusal that lives somewhere else is a refusal nobody finds.
 */
export function logRefusal(
  env: Env,
  session: { walletId: string; tokenHash: string; isTest: boolean },
  kind: 'transcribe' | 'reword' | 'cleanup',
  status: number,
  started: number,
  ctx: ExecutionContext,
  state?: { secondsLeft: number; rewordsLeft: number; secondsUsed: number },
): void {
  logUsage(env, {
    walletId: session.walletId,
    tokenHash: session.tokenHash,
    isTest: session.isTest,
    kind,
    seconds: 0,
    costNano: 0,
    status,
    ms: Date.now() - started,
    secondsLeft: state?.secondsLeft,
    rewordsLeft: state?.rewordsLeft,
    secondsUsedTotal: state?.secondsUsed,
  }, ctx);
}

export function debitError(reason: DebitReason, state?: WalletState): Response {
  switch (reason) {
    case 'blocked':
      return apiError(403, 'This credit account is blocked.', 'wallet_blocked', 'invalid_request_error');
    case 'rate_limited':
      return apiError(429, 'Too many requests in a short time.', 'rate_limited', 'rate_limit_error');
    case 'not_entitled':
      // 402 as before, so older apps still read it as QUOTA_EXCEEDED; the code is what tells a
      // current one to open the subscription screen rather than say "quota reached".
      return apiError(
        402,
        'Dictate Cloud needs an active Plus subscription.',
        'not_entitled',
        'insufficient_quota',
      );
    case 'daily_limit': {
      // Nothing to buy and nothing to retry until midnight UTC, so the reply says when that is —
      // in the body for the app's message, and as Retry-After for anything generic in between.
      const now = Date.now();
      const resetsAt = state?.dailyResetsAt ?? (Math.floor(now / 86_400_000) + 1) * 86_400_000;
      return new Response(
        JSON.stringify({
          error: {
            message: "Today's Dictate Cloud allowance is used up. It resets at 00:00 UTC.",
            type: 'rate_limit_error',
            code: 'daily_limit',
          },
          resets_at: resetsAt,
        }),
        {
          status: 429,
          headers: {
            'content-type': 'application/json; charset=utf-8',
            ...NO_STORE,
            'retry-after': String(Math.max(1, Math.ceil((resetsAt - now) / 1000))),
          },
        },
      );
    }
  }
}

/**
 * What every paid route reports alongside its answer, so the app can show the balance or the day's
 * allowance without asking separately. The `daily` trio only while Plus is active.
 */
export function usageHeaders(state: WalletState): Record<string, string> {
  return {
    'x-dictate-seconds-left': String(state.secondsLeft),
    'x-dictate-rewords-left': String(state.rewordsLeft),
    ...(state.plan === 'plus'
      ? {
          'x-dictate-daily-used': String(state.dailyUsedSeconds),
          'x-dictate-daily-allowance': String(state.dailyAllowanceSeconds),
          'x-dictate-daily-resets-at': String(state.dailyResetsAt),
        }
      : {}),
  };
}

/**
 * An upstream error is passed on, but not its wording.
 *
 * The user has no contract with the model provider and can do nothing with "your organization has
 * been blocked" — and the message could give away internals. Kept although the binding throws
 * rather than returning a status: the shape stays ready for a status that does arrive.
 */
function upstreamFailure(status: number): Response {
  if (status === 429) {
    return apiError(503, 'The service is busy right now. Please try again shortly.', 'upstream_busy', 'server_error');
  }
  if (status >= 500) {
    return apiError(502, 'The transcription service reported an error.', 'upstream_error', 'server_error');
  }
  return apiError(502, 'The recording could not be transcribed.', 'upstream_rejected', 'server_error');
}
