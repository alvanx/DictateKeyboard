import { authenticate, touch } from '../auth';
import {
  analyzeOutput,
  buildCleanupMessages,
  cleanOutput,
  cleanupMaxTokens,
  hasNoWords,
  isAlreadyClean,
  keepsContent,
  parseCleanupRequest,
  type CleanupRequest,
} from '../cleanup';
import { chatCostNano, costToSeconds, neuronsToNano, type Env, type Limits } from '../config';
import { budgetAllows, logUsage, settleBudget, walletStub } from '../meter';
import { debitPaid } from '../subscriptions';
import { NO_STORE, apiError, estimateTokens } from '../util';
import type { WalletState } from '../wallet';
import { chatComplete, type Upstream } from '../ai';
import { answerTextOf, parseUsage, reportReasoning, wasTruncated } from './chat';
import { debitError, logRefusal, usageHeaders } from './transcriptions';
import INSTRUCTIONS from '../../prompts/cleanup.txt';

/**
 * `POST /v1/cleanup` — raw dictation in, text ready to send out, spoken lists made into lists
 * (cleanup spec).
 *
 * Gated, metered and reserved exactly like `/v1/chat/completions`, and drawing on the same daily
 * allowance — there is no second meter. What differs is the failure mode: **this route fails soft.**
 * If the model errors or times out, the original text comes back unchanged as `plain`, uncharged,
 * with a 200. A cleanup is an improvement on a dictation, and a dictation is never lost to one.
 *
 * The instructions live in `prompts/cleanup.txt`, one file, so the app can ship the same wording as
 * its free built-in prompt (cleanup spec, section 7) and both stay in step.
 *
 * Nothing of the text is logged: wallet, timing, token counts, status and whether it became a list.
 */
export async function handleCleanup(
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

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return apiError(400, 'Request is not valid JSON.', 'bad_request', 'invalid_request_error');
  }
  const req = parseCleanupRequest(body);
  if ('error' in req) return apiError(400, req.error, 'bad_request', 'invalid_request_error');

  // Refused, never truncated: a cleanup that silently dropped the end of a dictation would be the
  // one failure this route exists to rule out.
  if (req.text.length > limits.cleanupMaxChars || estimateTokens(req.text) > limits.maxChatInputTokens) {
    logRefusal(env, session, 'cleanup', 413, started, ctx);
    return apiError(413, 'The text is too long to clean up through Dictate Cloud.', 'input_too_long', 'invalid_request_error');
  }

  const wallet = walletStub(env, session.walletId);

  // Nothing for a model to do. Still only for an account that may use the route: the answer would
  // be free, but "who is entitled" should not depend on what the text happens to look like.
  if (hasNoWords(req.text) || isAlreadyClean(req)) {
    const state = await wallet.state();
    if (state.plan !== 'plus' && state.secondsLeft <= 0) return debitError('not_entitled', state);
    return reply({ text: req.text, formattedAs: 'plain', seconds: 0 }, state);
  }

  const messages = buildCleanupMessages(INSTRUCTIONS, req);
  const inputTokens = messages.reduce((sum, m) => sum + estimateTokens(m.content), 0);
  const maxTokens = cleanupMaxTokens(req.text, limits.maxChatOutputTokens);
  const model = limits.cleanupModel;

  const worstCaseNano = chatCostNano(model, inputTokens, maxTokens);
  if (!(await budgetAllows(env, limits, worstCaseNano, ctx))) {
    logRefusal(env, session, 'cleanup', 503, started, ctx);
    return apiError(503, 'Voice Mode Cloud is unavailable right now. Please try again later.', 'service_paused', 'server_error');
  }

  // Entitlement before anything reaches a model: a 402 here means no model call was made.
  const reservedSeconds = costToSeconds(worstCaseNano);
  const debit = await debitPaid(env, session.walletId, reservedSeconds, limits);
  if (!debit.ok) {
    settleBudget(env, -worstCaseNano, ctx);
    const refusal = debitError(debit.reason, debit.state);
    logRefusal(env, session, 'cleanup', refusal.status, started, ctx, debit.state);
    return refusal;
  }

  const upstream = await withTimeout(
    chatComplete(env, messages, maxTokens, model),
    limits.cleanupTimeoutMs,
  );

  if (upstream.kind !== 'ok') {
    // Fail soft: the original text, nothing charged.
    const state = await wallet.refund(reservedSeconds, debit.booking);
    settleBudget(env, -worstCaseNano, ctx);
    logUsage(env, {
      walletId: session.walletId,
      tokenHash: session.tokenHash,
      isTest: session.isTest,
      kind: 'cleanup',
      formattedAs: 'plain',
      costNano: 0,
      status: upstream.kind === 'failed' ? upstream.status : 504,
      ms: Date.now() - started,
      secondsLeft: state.secondsLeft,
      rewordsLeft: state.rewordsLeft,
      secondsUsedTotal: state.secondsUsed,
    }, ctx);
    return reply({ text: req.text, formattedAs: 'plain', seconds: 0 }, state);
  }

  // Settled to what it really cost, as rewording is.
  const usage = parseUsage(upstream.body);
  const actualNano = upstream.neurons > 0
    ? neuronsToNano(upstream.neurons)
    : chatCostNano(model, usage.in || inputTokens, usage.out);
  const actualSeconds = costToSeconds(actualNano);
  settleBudget(env, actualNano - worstCaseNano, ctx);
  let state = debit.state;
  if (actualSeconds !== reservedSeconds) {
    state = await wallet.adjust(actualSeconds - reservedSeconds, debit.booking);
  }
  const answer = answerTextOf(upstream.body);
  ctx.waitUntil(reportReasoning(env, usage.out, answer, model));

  // An answer that ran out of room, or one that does not keep to the speaker's words, is not used:
  // the original goes back instead. Charged all the same, at its real cost — the model did the
  // work, and a refund the client could provoke at will would be a way to spend the shared daily
  // budget for nothing.
  const cleaned = cleanOutput(answer, req.text);
  const result = wasTruncated(upstream.body) ? null : acceptable(cleaned, req);

  logUsage(env, {
    walletId: session.walletId,
    tokenHash: session.tokenHash,
    isTest: session.isTest,
    kind: 'cleanup',
    formattedAs: result?.formattedAs ?? 'plain',
    model,
    seconds: actualSeconds,
    tokensIn: usage.in || inputTokens,
    tokensOut: usage.out,
    neuronsMicro: Math.round(upstream.neurons * 1_000_000),
    costNano: actualNano,
    // 422 marks an answer that was thrown away, so the rate of them can be watched per model.
    status: result ? 200 : 422,
    ms: Date.now() - started,
    secondsLeft: state.secondsLeft,
    rewordsLeft: state.rewordsLeft,
    secondsUsedTotal: state.secondsUsed,
  }, ctx);

  return reply(result ?? { text: req.text, formattedAs: 'plain', seconds: actualSeconds }, state, actualSeconds);
}

/** The answer, if it may replace what was said. */
function acceptable(
  text: string,
  req: CleanupRequest,
): { text: string; formattedAs: 'plain' | 'list'; items?: number; seconds: number } | null {
  if (!text || !keepsContent(req.text, text, req.terms, req.locale)) return null;
  const shape = analyzeOutput(text);
  // A list where none was allowed is the model not following the settings.
  if (shape.formattedAs === 'list' && (req.lists === 'off' || req.context === 'code')) return null;
  return { text, ...shape, seconds: 0 };
}

function reply(
  result: { text: string; formattedAs: 'plain' | 'list'; items?: number; seconds: number },
  state: WalletState,
  charged = result.seconds,
): Response {
  return new Response(
    JSON.stringify({
      text: result.text,
      formatted_as: result.formattedAs,
      ...(result.formattedAs === 'list' && result.items ? { items: result.items } : {}),
      usage: { seconds_charged: charged },
    }),
    {
      status: 200,
      headers: { 'content-type': 'application/json; charset=utf-8', ...NO_STORE, ...usageHeaders(state) },
    },
  );
}

/**
 * The model call, given up on after [ms]. The call itself is not cancelled — the binding offers no
 * way to — but nobody waits for it, and its cost is refunded like any failure's.
 */
async function withTimeout(call: Promise<Upstream>, ms: number): Promise<Upstream> {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const timeout = new Promise<Upstream>((resolve) => {
    timer = setTimeout(() => resolve({ kind: 'unreachable' }), ms);
  });
  try {
    return await Promise.race([call, timeout]);
  } finally {
    if (timer) clearTimeout(timer);
  }
}
