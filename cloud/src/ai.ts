import type { Env, Limits } from './config';
import { isWorkersAiModel } from './config';

/**
 * The two model calls this service makes — speech to text, and text to text — for either provider.
 *
 * Everything around them (who may call, what it costs, refunds, the ledger) lives in the routes and
 * does not care who ran the model. What comes back here is the same shape for both: the body the
 * client receives, and what was used, so the cost can be settled.
 *
 * Which provider runs a call is decided by the model's name: `@cf/…` goes to the Workers AI
 * binding, anything else to Groq. `AI_PROVIDER` only picks the default names (config.ts).
 */

const GROQ_BASE = 'https://api.groq.com/openai/v1';

/**
 * One transcription. `body` is `{ "text": … }` and nothing else: the app reads nothing more, and
 * handing back segments and timings would publish which model is behind Voice Mode Cloud for no
 * one's benefit.
 */
export type Transcription =
  | { kind: 'ok'; body: string; reportedSeconds: number | null; neurons: number }
  | { kind: 'failed'; status: number }
  | { kind: 'unreachable' };

/** One chat completion, trimmed to the OpenAI shape the app parses: the choice and the usage. */
export type Upstream =
  | { kind: 'ok'; body: string; neurons: number }
  | { kind: 'failed'; status: number }
  | { kind: 'unreachable' };

export async function transcribe(
  env: Env,
  limits: Limits,
  file: File,
  opts: { language?: string; prompt?: string },
): Promise<Transcription> {
  return isWorkersAiModel(limits.transcribeModel)
    ? transcribeWorkersAi(env, limits, file, opts)
    : transcribeGroq(env, limits.transcribeModel, file, opts);
}

export async function chatComplete(
  env: Env,
  messages: unknown[],
  maxTokens: number,
  model: string,
): Promise<Upstream> {
  return isWorkersAiModel(model)
    ? chatWorkersAi(env, model, messages, maxTokens)
    : chatGroq(env, model, messages, maxTokens);
}

/**
 * Groq, over its OpenAI-compatible endpoint. The file is handed on as it arrived — no decoding, no
 * re-encoding — which is what keeps a dictation inside the free plan's CPU budget.
 *
 * `verbose_json` for the `duration` it carries: the length is read from the file header where the
 * format states it, and this is the fallback that corrects an estimate for the formats that do not.
 */
async function transcribeGroq(
  env: Env,
  model: string,
  file: File,
  opts: { language?: string; prompt?: string },
): Promise<Transcription> {
  const form = new FormData();
  form.set('file', file, file.name || 'audio.wav');
  form.set('model', model);
  form.set('response_format', 'verbose_json');
  form.set('temperature', '0');
  if (opts.language) form.set('language', opts.language);
  if (opts.prompt) form.set('prompt', opts.prompt);

  const response = await groq(env, '/audio/transcriptions', form, 120_000);
  if (!response) return { kind: 'unreachable' };
  if (!response.ok) {
    console.log(`groq transcribe answered ${response.status}`);
    return { kind: 'failed', status: response.status };
  }
  const result = (await response.json().catch(() => ({}))) as { text?: string; duration?: number };
  const duration = result.duration;
  return {
    kind: 'ok',
    body: JSON.stringify({ text: result.text ?? '' }),
    reportedSeconds: typeof duration === 'number' && duration > 0 ? duration : null,
    neurons: 0,
  };
}

/**
 * Groq's chat. The client's own model, temperature and the rest are never passed on — the server
 * decides the model and the length, or the costing would be wide open.
 */
async function chatGroq(env: Env, model: string, messages: unknown[], maxTokens: number): Promise<Upstream> {
  const response = await groq(
    env,
    '/chat/completions',
    JSON.stringify({ model, messages, max_completion_tokens: maxTokens, temperature: 0.2 }),
    60_000,
  );
  if (!response) return { kind: 'unreachable' };
  if (!response.ok) {
    console.log(`groq chat answered ${response.status}`);
    return { kind: 'failed', status: response.status };
  }
  const result = (await response.json().catch(() => ({}))) as {
    choices?: { message?: { content?: string | null }; finish_reason?: string }[];
    usage?: { prompt_tokens?: number; completion_tokens?: number };
  };
  return { kind: 'ok', body: trimmedChat(result), neurons: 0 };
}

/** A request to Groq, or null when it never answered — network failure, timeout, no key. */
async function groq(env: Env, path: string, body: BodyInit, timeoutMs: number): Promise<Response | null> {
  if (!env.GROQ_API_KEY) {
    console.log('groq: GROQ_API_KEY is not set');
    return null;
  }
  try {
    return await fetch(`${GROQ_BASE}${path}`, {
      method: 'POST',
      headers: {
        authorization: `Bearer ${env.GROQ_API_KEY}`,
        ...(typeof body === 'string' ? { 'content-type': 'application/json' } : {}),
      },
      body,
      signal: AbortSignal.timeout(timeoutMs),
    });
  } catch (error) {
    console.log(`groq ${path} unreachable: ${String(error).slice(0, 200)}`);
    return null;
  }
}

function trimmedChat(result: {
  choices?: { message?: { content?: string | null }; finish_reason?: string }[];
  usage?: { prompt_tokens?: number; completion_tokens?: number };
}): string {
  const choice = result.choices?.[0];
  return JSON.stringify({
    choices: [{
      message: { role: 'assistant', content: choice?.message?.content ?? '' },
      finish_reason: choice?.finish_reason ?? 'stop',
    }],
    usage: {
      prompt_tokens: result.usage?.prompt_tokens ?? 0,
      completion_tokens: result.usage?.completion_tokens ?? 0,
    },
  });
}

/**
 * Workers AI transcription, over the binding.
 *
 * Four things about it are worth keeping in mind, and each has a reason:
 *
 *  1. **Base64, not multipart.** `whisper-large-v3-turbo` takes the audio as a string. The older
 *     `@cf/openai/whisper` wants `number[]` — nineteen million array entries for a ten-minute file
 *     against a 128 MB worker — which is why turbo is not merely the better model here but the only
 *     usable one. It is also why this path needs Cloudflare's paid plan: building the string costs
 *     far more CPU than the free plan's 10 ms.
 *  2. **`duration`, never `duration_after_vad`.** Both come back. The second is the audio with the
 *     silence taken out, and billing by it would charge a recording with pauses for less than it is.
 *  3. **`vad_filter` stays off.** The app already runs a local Silero VAD (#93) and never uploads a
 *     silent recording. A second one here would only change the duration that comes back — see 2.
 *  4. **It throws instead of returning a status**, so the failure arrives as an exception and the
 *     refund path is a `catch`.
 */
async function transcribeWorkersAi(
  env: Env,
  limits: Limits,
  file: File,
  opts: { language?: string; prompt?: string },
): Promise<Transcription> {
  let result: {
    text?: string;
    transcription_info?: { duration?: number };
    usage?: { neurons?: number };
  };
  try {
    result = await env.AI.run(limits.transcribeModel as keyof AiModels, {
      audio: base64(new Uint8Array(await file.arrayBuffer())),
      task: 'transcribe',
      language: opts.language,
      initial_prompt: opts.prompt,
    } as never) as typeof result;
  } catch (error) {
    console.log(`workers-ai transcribe failed: ${String(error).slice(0, 200)}`);
    return { kind: 'unreachable' };
  }

  const duration = result.transcription_info?.duration;
  return {
    kind: 'ok',
    body: JSON.stringify({ text: result.text ?? '' }),
    reportedSeconds: typeof duration === 'number' && duration > 0 ? duration : null,
    // Reported rather than calculated (see `NEURONS` in config.ts). Absent, it falls back to the
    // per-minute table, which is why this stays 0 instead of guessing here.
    neurons: typeof result.usage?.neurons === 'number' ? result.usage.neurons : 0,
  };
}

/**
 * Workers AI chat, and it does not think.
 *
 * `chat_template_kwargs.enable_thinking: false` is the switch that works; thinking is on by default
 * for the Gemma model. Measured on 30.08.2026, same sentence: 20 output tokens against 777. Whether
 * it stays off is checked on every response (`reportReasoning` in routes/chat.ts).
 */
async function chatWorkersAi(env: Env, model: string, messages: unknown[], maxTokens: number): Promise<Upstream> {
  let result: {
    choices?: { message?: { content?: string | null }; finish_reason?: string }[];
    usage?: { prompt_tokens?: number; completion_tokens?: number; neurons?: number };
  };
  try {
    result = await env.AI.run(model as keyof AiModels, {
      messages,
      max_completion_tokens: maxTokens,
      chat_template_kwargs: { enable_thinking: false },
    } as never) as typeof result;
  } catch (error) {
    console.log(`workers-ai chat failed: ${String(error).slice(0, 200)}`);
    return { kind: 'unreachable' };
  }
  return {
    kind: 'ok',
    body: trimmedChat(result),
    neurons: typeof result.usage?.neurons === 'number' ? result.usage.neurons : 0,
  };
}

/**
 * Base64 for the audio, in chunks. `String.fromCharCode(...bytes)` on a ten-minute recording spreads
 * nineteen million arguments across the stack; thirty-two kilobytes at a time is safe. Measured on
 * 19 MB: about two seconds of CPU.
 */
function base64(bytes: Uint8Array): string {
  const step = 0x8000;
  let binary = '';
  for (let i = 0; i < bytes.length; i += step) {
    binary += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(binary);
}
