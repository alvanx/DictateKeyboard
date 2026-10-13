# Cleanup spec: one-tap cleanup with list formatting

Status: draft for the paid server (built separately) and the app session. Read `billing.md` first; this spec adds one route and one app option to it.

## 1. Goal

Turn a raw dictation into text that is ready to send, in one call: fix punctuation and capitalisation, drop filler words, and **turn spoken lists into real lists**. The server owns the instructions, so cleanup improves without an app release. It is part of the Plus plan, not a separate purchase.

## 2. Rules

- **Never invent or lose content.** Same words, same order. Cleanup may remove "um" and repeated words; it may not add facts, merge two ideas, or "improve" meaning.
- **Fail soft.** If the model call fails or times out, return the original text unchanged with `formatted_as: "plain"`, and do not charge. A dictation is never lost to a cleanup error.
- **No text is stored or logged.** Log only: wallet id, ms, input and output token counts, status, `formatted_as`. Same retention rule as dictation.
- **Parity rule.** The same instruction text ships in the app as a free built-in prompt, so bring-your-own-key users get the same result. The paid part is that we run it for you (section 7).

## 3. Route

`POST /v1/cleanup` (auth: wallet token, like the other routes). Gated and metered exactly like `/v1/chat/completions` (`billing.md` section 6): entitlement check first, debit before the upstream call, refund on failure, `402 not_entitled`, `429 daily_limit`, `413 input_too_long`. It draws on the **same daily allowance** as dictation; there is no second meter.

Request:

```json
{
  "text": "um so for the trip we need first passports second chargers and third the tickets",
  "locale": "en-US",
  "lists": "auto",
  "list_style": "auto",
  "context": "notes",
  "terms": ["Lisbon", "Schiphol"]
}
```

| Field | Values | Default | Meaning |
| --- | --- | --- | --- |
| `text` | string | required | The raw dictation. Capped at `maxChatInputTokens`. |
| `locale` | BCP 47 | device locale | Language of the text. Output stays in the same language. |
| `lists` | `auto`, `off`, `force` | `auto` | `auto`: format only when the speech clearly signals a list. `force`: the user pressed "make this a list". `off`: punctuation and filler only. |
| `list_style` | `auto`, `bullets`, `numbered`, `checklist` | `auto` | See section 4. |
| `context` | `messaging`, `email`, `notes`, `code`, `other` | `other` | Where the text will go. `messaging` keeps lists short and plain; `email` and `notes` allow a one-line lead-in; `code` never reformats. |
| `terms` | up to 100 strings | none | The user's dictionary. Spell these exactly. |

Response:

```json
{
  "text": "For the trip we need:\n1. Passports\n2. Chargers\n3. Tickets",
  "formatted_as": "list",
  "items": 3,
  "usage": { "seconds_charged": 2 }
}
```

`formatted_as` is `plain` or `list`. `items` is present only for a list. The route also reports the remaining daily allowance in the same way the other paid routes do.

Keep `/v1/chat/completions`: it still serves the user's own custom prompts. `/v1/cleanup` is the opinionated preset.

## 4. List rules (the instruction text)

The server holds one instruction text per behaviour. It must say, in plain words:

1. **Explicit commands win.** "Bullet", "new bullet", "number one", "next item", "end of list" are commands, not content. Remove the cue words, keep the items.
2. **Cues that start a list in `auto`:** an ordinal run ("first… second… third", "one… two… three"), a stated count ("three things: …"), or a repeated item rhythm after a colon-like lead-in ("we need X, Y and Z" does **not** qualify; a comma series inside one sentence stays a sentence). When unsure, do not make a list.
3. **Style:** ordinals or numbers spoken → `numbered`. "To-do", "checklist", "tasks" → `checklist`. Anything else → `bullets`. An explicit `list_style` overrides.
4. **Output markers are plain characters**, so they work in any text field: `1. `, `- `, `- [ ] `. Do not output rich-text markup. (The app may swap `- ` for `• ` per field; the server does not.)
5. **Items stay short and in the speaker's words.** Trim filler and trailing "and". Capitalise the first letter of each item. No full stops after short items; keep them for full sentences.
6. **A lead-in line is allowed** when the speech had one ("For the trip we need:") and `context` is `email` or `notes`. In `messaging`, keep the lead-in only if the speaker said it.
7. **Nested lists:** one level only in version 1. If the speech nests, flatten and keep the order.
8. **Language:** handle the cue words of the locale (not only English). Test at least English, German, Spanish, French, Portuguese and Dutch. The model does this, not a word list; a small word list may exist as a cheap pre-check to skip the call entirely when `lists` is `off` and the text is already clean.

## 5. Model and cost

- Use the cheapest model that passes section 8; start from the model already set for rewording (`limits.chatModel`) and compare one step down before committing.
- Reserve-then-settle exactly as `chat.ts` does today: reserve input plus the largest permitted output, settle on the real count. Output cap for this route: 1.5 times the input tokens, at most `maxChatOutputTokens`.
- Typical cost is under two cost-weighted seconds per call. Log it so the allowance (`billing.md` section 8) can be tuned.

## 6. App side (for the app session)

- A settings option **Cleanup** with: Off, Punctuation only, Auto lists (default), and a one-shot "Make this a list" action on the result bar that sends `lists: "force"`.
- Send `context` from the editor's input type (`messaging` for chat apps, `email` for email fields, `code` for code editors, otherwise `other`).
- Send the user's dictionary as `terms`.
- Use `PaidFeature.MANAGED_CLEANUP` to show or hide the managed path; without it the same option runs through the user's own provider using the built-in prompt (section 7).
- On `402 not_entitled` or `429 daily_limit`, insert the raw text and show the usual message. Never block the insert.

## 7. Free path (parity)

Add the section 4 instruction text to the app as a built-in prompt named **Clean up and format lists**, available to anyone with their own key or an on-device model. Keep it in one file so server and app share wording. The paid difference is convenience (no key, no setup, one allowance), not capability.

## 8. Tests and acceptance

Table-driven tests on the server (mock the model call for the plumbing, run a small real-model suite before each release):

| Spoken | Expected |
| --- | --- |
| "first passports second chargers third tickets" | numbered list of three |
| "we need milk eggs and bread" | one sentence, no list |
| "bullet milk bullet eggs bullet bread" | bullets, cue words removed |
| "to do call the bank book the dentist" with `lists: force` | checklist of two |
| same text with `lists: off` | punctuation only |
| a list spoken in German | German list, German lead-in |
| `context: code` | text untouched except filler |
| model times out | original text, `formatted_as: plain`, no charge |

Acceptance: no test may add or drop a word that is not filler or a cue; a 10,000-character input is refused with `413`, not truncated; a user with no entitlement gets `402` before any model call.

## 9. Open questions

- Should "Make this a list" also exist for text the user already typed (not dictated)? Same route, no change needed; it is an app decision.
- One level of nesting is enough for launch? Decide after beta feedback.
- Do we offer a user-editable style (for example always use dashes)? Out of scope for version 1.
