/**
 * One-tap cleanup: the rules around the model call (cleanup spec).
 *
 * Pure functions, so every rule can be tested without a model: reading the request, building the
 * messages, deciding whether the call can be skipped, and checking the answer before it replaces
 * anything the user said. The model is only trusted as far as these checks let it be — "never
 * invent or lose content" is a promise, and a promise about a model's output needs a check on the
 * output, not just a sentence in the prompt.
 */

export type ListsMode = 'auto' | 'off' | 'force';
export type ListStyle = 'auto' | 'bullets' | 'numbered' | 'checklist';
export type CleanupContext = 'messaging' | 'email' | 'notes' | 'code' | 'other';

export interface CleanupRequest {
  text: string;
  locale: string | null;
  lists: ListsMode;
  listStyle: ListStyle;
  context: CleanupContext;
  terms: string[];
}

const LISTS: readonly ListsMode[] = ['auto', 'off', 'force'];
const STYLES: readonly ListStyle[] = ['auto', 'bullets', 'numbered', 'checklist'];
const CONTEXTS: readonly CleanupContext[] = ['messaging', 'email', 'notes', 'code', 'other'];

const MAX_TERMS = 100;
const MAX_TERM_CHARS = 64;

/**
 * Reads the body. Only `text` is required; an unknown value for one of the settings falls back to
 * its default rather than failing the request — a newer app sending a style this server does not
 * know yet should still get its text cleaned.
 */
export function parseCleanupRequest(body: unknown): CleanupRequest | { error: string } {
  if (!body || typeof body !== 'object') return { error: 'Request body must be a JSON object.' };
  const b = body as Record<string, unknown>;
  if (typeof b.text !== 'string') return { error: 'Field "text" is missing.' };

  const pick = <T extends string>(value: unknown, allowed: readonly T[], fallback: T): T =>
    typeof value === 'string' && (allowed as readonly string[]).includes(value) ? (value as T) : fallback;

  const terms = Array.isArray(b.terms)
    ? b.terms
        .filter((t): t is string => typeof t === 'string')
        // A term is a word or a name. Line breaks inside one would let it reach into the prompt.
        .map((t) => t.replace(/[\r\n\t]+/g, ' ').trim().slice(0, MAX_TERM_CHARS))
        .filter((t) => t.length > 0)
        .slice(0, MAX_TERMS)
    : [];

  const locale = typeof b.locale === 'string' && /^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$/.test(b.locale.trim())
    ? b.locale.trim().replace('_', '-')
    : null;

  return {
    text: b.text,
    locale,
    lists: pick(b.lists, LISTS, 'auto'),
    listStyle: pick(b.list_style, STYLES, 'auto'),
    context: pick(b.context, CONTEXTS, 'other'),
    terms,
  };
}

/** Code is never reformatted (cleanup spec, section 3), whatever `lists` says. */
export function effectiveLists(req: CleanupRequest): ListsMode {
  return req.context === 'code' ? 'off' : req.lists;
}

/**
 * The two messages for the model. The rules go in the system message, the user's text in the user
 * message between tags, so a transcript that says "ignore the above" is text to clean rather than
 * an instruction — and a closing tag inside it cannot end the transcript early.
 */
export function buildCleanupMessages(
  instructions: string,
  req: CleanupRequest,
): Array<{ role: 'system' | 'user'; content: string }> {
  const settings = [
    `lists: ${effectiveLists(req)}`,
    `list_style: ${req.listStyle}`,
    `context: ${req.context}`,
    `language: ${req.locale ?? 'unknown'}`,
    `terms: ${req.terms.length ? req.terms.join(', ') : 'none'}`,
  ].join('\n');
  const text = req.text.replace(/<\/?transcript>/gi, '');
  return [
    { role: 'system', content: instructions.trim() },
    { role: 'user', content: `Settings:\n${settings}\n\n<transcript>\n${text}\n</transcript>` },
  ];
}

/**
 * The output cap: one and a half times the input (list markers and line breaks add a little), with
 * a floor for very short texts, and never above the route-wide maximum (cleanup spec, section 5).
 */
export function cleanupMaxTokens(text: string, maxChatOutputTokens: number): number {
  const inputTokens = Math.ceil(text.length / 4);
  return Math.min(maxChatOutputTokens, Math.max(64, Math.ceil(inputTokens * 1.5) + 32));
}

/** Strips what a model sometimes wraps an answer in, despite being told not to. */
export function cleanOutput(raw: string, original: string): string {
  let out = raw.trim();
  const fenced = /^```[a-zA-Z]*\n([\s\S]*?)\n?```$/.exec(out);
  if (fenced) out = fenced[1]!.trim();
  out = out.replace(/^<transcript>\s*/i, '').replace(/\s*<\/transcript>$/i, '').trim();
  const quoted = /^(["“„«])([\s\S]*)(["”“»])$/.exec(out);
  if (quoted && !/^["“„«]/.test(original.trim())) out = quoted[2]!.trim();
  return out;
}

/** A line that starts with one of the three plain markers (cleanup spec, section 4, rule 5). */
const MARKER = /^\s*(?:\d{1,3}[.)]|[-*•])\s+(?:\[[ xX]?\]\s+)?/;

export function analyzeOutput(text: string): { formattedAs: 'plain' | 'list'; items?: number } {
  const items = text.split('\n').filter((line) => MARKER.test(line) && line.replace(MARKER, '').trim()).length;
  return items >= 2 ? { formattedAs: 'list', items } : { formattedAs: 'plain' };
}

function segmenter(locale: string | null): Intl.Segmenter {
  try {
    return new Intl.Segmenter(locale ?? undefined, { granularity: 'word' });
  } catch {
    return new Intl.Segmenter(undefined, { granularity: 'word' });
  }
}

/**
 * The words of a text, lower-cased, by the locale's own word boundaries — so the check below works
 * for languages written without spaces too.
 */
export function wordsOf(text: string, locale: string | null): string[] {
  const out: string[] = [];
  for (const s of segmenter(locale).segment(text)) {
    if (s.isWordLike) out.push(s.segment.toLocaleLowerCase(locale ?? undefined));
  }
  return out;
}

export function hasNoWords(text: string): boolean {
  return wordsOf(text, null).length === 0;
}

/**
 * Whether the answer keeps to "never invent or lose content" (cleanup spec, rule 1), checked on the
 * words rather than trusted to the prompt.
 *
 * - **Invented:** words in the answer that were not in the transcript or the user's terms. Numbers
 *   are allowed — "three things" may become a list numbered 1 to 3. A few others are tolerated,
 *   because fixing "gonna" or a split compound is not invention, but not many.
 * - **Lost:** distinct words of the transcript missing from the answer. Some go legitimately —
 *   filler, repeated words, the cue words of a spoken list ("first", "second", "and third") — and
 *   in a short list those can be half of what was said, so the bar is high: six words, or 45 %.
 *   A summary loses far more than that.
 * - **Both at once:** an answer that adds words *and* drops a good share is a rewrite, and that is
 *   the shape of a model replying to the text instead of cleaning it.
 *
 * Generous on purpose. Its job is to catch the answer that summarised, translated, replied to the
 * text or invented an item, not to second-guess punctuation.
 */
export function keepsContent(original: string, answer: string, terms: string[], locale: string | null): boolean {
  const said = wordsOf(original, locale);
  const written = wordsOf(answer.split('\n').map((l) => l.replace(MARKER, '')).join('\n'), locale);
  if (said.length === 0) return written.length === 0;
  if (written.length === 0) return false;

  const saidSet = new Set(said);
  const termSet = new Set(wordsOf(terms.join(' '), locale));
  const invented = written.filter((w) => !saidSet.has(w) && !termSet.has(w) && !/^\d+$/.test(w));
  if (invented.length > Math.max(3, Math.ceil(written.length * 0.15))) return false;

  const writtenSet = new Set(written);
  const distinct = [...saidSet];
  const lost = distinct.filter((w) => !writtenSet.has(w));
  if (lost.length > Math.max(6, Math.ceil(distinct.length * 0.45))) return false;
  // Words both added and dropped is rewriting rather than cleaning — an answer to a question in the
  // text looks exactly like this, and is short enough to slip under both bars on its own.
  return invented.length === 0 || lost.length <= Math.max(3, Math.ceil(distinct.length * 0.25));
}

/**
 * Filler that is never a word in its own right, in the languages the cleanup is tested in. Only
 * used to decide whether the model call can be skipped — the model does the real work.
 */
const FILLER = new Set(['um', 'umm', 'uh', 'uhm', 'erm', 'hmm', 'mm', 'mmm', 'äh', 'ähm', 'öh', 'öhm', 'euh', 'eh', 'ehm', 'eeh', 'ehh']);

/**
 * Whether there is nothing for the model to do: lists are off, and the text already starts with a
 * capital, ends with a sentence mark, and holds no filler and no doubled word. Then the call is
 * skipped and nothing is charged (cleanup spec, section 4, rule 8).
 */
export function isAlreadyClean(req: CleanupRequest): boolean {
  if (effectiveLists(req) !== 'off') return false;
  const text = req.text.trim();
  if (!text) return true;
  const first = text[0]!;
  if (first.toLocaleLowerCase() !== first.toLocaleUpperCase() && first !== first.toLocaleUpperCase()) return false;
  if (!/[.!?…。！？]["”»)]?$/.test(text)) return false;
  const words = wordsOf(text, req.locale);
  for (let i = 0; i < words.length; i++) {
    if (FILLER.has(words[i]!)) return false;
    if (i > 0 && words[i] === words[i - 1]) return false;
  }
  return true;
}
