# Voice Mode Cloud

The server behind the app's optional Plus subscription — "subscribe instead of bringing your own
API key". A single Cloudflare Worker: it verifies a Google Play subscription, meters a daily
allowance, and passes dictation, rewording and one-tap cleanup to Groq (Whisper Large v3 Turbo and a
small Llama) while metering what they cost. The specs it is built from are `docs/specs/billing.md`
and `docs/specs/cleanup.md`.

**Why Groq first, and why it runs on Cloudflare's free plan.** Groq bills per request with no
monthly floor, so a service with few users costs next to nothing. Cloudflare's own Workers AI is
about as cheap per minute but only usable on the $5-a-month plan: it wants audio as a base64 string,
which costs far more CPU than the free plan's 10 ms per request. Sent to Groq, the audio is passed on
as it arrived, and the whole service fits the free plan. Once traffic covers the $5, switching is
`AI_PROVIDER = "workers-ai"` plus the paid plan — no code change. Which model does the work is
`TRANSCRIBE_MODEL`, `CHAT_MODEL` and `CLEANUP_MODEL`.

Groq bills **at least ten seconds per dictation**. Users are charged real seconds against their
allowance; the floor is a cost to the operator, visible in the ledger.

Using the app does not require any of this. Bring an API key from a provider of your choice and the
app never speaks to this server at all — see the app's provider settings. This exists so that
someone who does not want a provider account has a way in.

## Why the source is here

Because the privacy policy makes claims about it that ought to be checkable. It says recordings and
transcripts are never written to disk, that only numbers are stored, that no name or email address
is attached to an account. Those are statements about *this* code, and a claim you can read the
source of is worth more than one you have to take on trust.

`src/meter.ts` is the place to start if that is what brought you here: it is the only file that
writes anything, and it writes wallet id, timestamp, duration, token counts, status code and
milliseconds. Nothing else.

## What is not here

**`wrangler.jsonc`.** It carries the alert recipient's address, the database id and every threshold
the watchdog uses. The thresholds guard nothing on their own — they only decide when a warning is
sent — but knowing them tells you exactly how to stay underneath. `wrangler.example.jsonc` has the
same structure with the values removed.

**Secrets**, of course. The Play service account, the notification secret and the Groq key live in
Cloudflare's secret store and never in a file. The source is written so that publishing it gives
nothing away.

**The business papers.** Record of processing, technical and organisational measures, the tax notes,
the third-country documentation, the abuse test plan. They belong to the operator, not to the code.

## Why half of this is in German

Code, comments, configuration and everything a user of the app can ever see are **English** — API
errors, the public deletion page at `/delete`, this file.

The dashboard under `/admin` and the alert mails are **German**, and that is a decision rather than
an unfinished translation. Both have exactly one reader: the dashboard sits behind Cloudflare Access
bound to a single address, and the mails go to a single inbox. Translating a surface with one reader
into a language that reader does not think in buys consistency for people reading the source and
costs clarity for the one person who has to act on a warning at seven in the morning.

The line runs at the boundary, not through the middle of a file: `src/admin/*` and the alert texts
in `notify/`, `rules.ts`, `rtdn.ts`, `throttle.ts`, `meter.ts`, `sweep.ts`, `redeem.ts`,
`transcriptions.ts` and `wallet.ts` are German; their comments, and everything else, are not. Anything
that can reach an app user is English wherever it lives.

## Plus, in one paragraph

The app reports a Play subscription to `POST /v1/subscription/verify`; the server asks Google
(`subscriptionsv2`), acknowledges it, records it in `subscriptions` and works out "entitled until".
That one number is copied into the account's Durable Object, which is what every paid request checks:
entitled means today's allowance (`DAILY_ALLOWANCE_SECONDS`, reset at 00:00 UTC) is booked; not
entitled means `402 not_entitled`; allowance used means `429 daily_limit`. Google's notifications,
an hourly re-read of anything that can change unannounced, and a recheck on every refusal keep the
number current — and if Google is unreachable at the moment of renewal, the paying user is let
through rather than locked out. Seconds bought as credit packs before Plus stay spendable.

## Endpoints

| Route | What it does |
| --- | --- |
| `POST /v1/subscription/verify` | A Play subscription becomes an account with Plus, or finds its account again |
| `GET /v1/entitlement` | Plan, state, expiry, today's allowance and the unlocked features |
| `POST /v1/audio/transcriptions` | Dictation (OpenAI format) |
| `POST /v1/chat/completions` | The user's own rewording prompts (OpenAI format) |
| `POST /v1/cleanup` | One-tap cleanup that turns spoken lists into lists; fails soft to the original text |
| `POST /v1/rtdn` | Google's notifications: refunds and subscription changes |
| `/v1/wallet/*`, `/delete` | Restore by recovery code, sign a device out, delete the account |

## Layout

```
src/
  index.ts          the router, and the three cron rhythms
  wallet.ts         one account, as a Durable Object — where "check and book" is atomic
  entitlement.ts    subscription states, "entitled until", the daily bucket — pure rules
  subscriptions.ts  Google ↔ ledger ↔ wallet: verify, refresh, void, reconcile, recheck
  cleanup.ts        the cleanup rules around the model, including the never-invent check
  guard.ts          the daily spend ceiling and the code-guessing counter, one object for all
  meter.ts          the ledger; the only thing that writes usage
  ai.ts             the two model calls, for Groq and for Workers AI
  routes/           transcriptions, chat, cleanup, subscription, restore, delete, notifications
  config.ts         prices, limits — and the neuron table the reported figures are checked against
  admin/            the dashboard behind Cloudflare Access
  notify/           alert rules, digest, the mails
prompts/cleanup.txt the cleanup instructions — one file, meant to be shared with the app
migrations/         applied by hand, in order — see the note in each file
schema.sql          the shape of a fresh database
test/               end-to-end tests under Node (`npm test`)
```

## Running it

```sh
npm install
cp wrangler.example.jsonc wrangler.jsonc   # then fill in the placeholders
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT
npx wrangler secret put GROQ_API_KEY
npx wrangler d1 create dictate-cloud --location weur
npx wrangler d1 execute dictate-cloud --remote --file schema.sql
npx tsc --noEmit && npm test && npx wrangler deploy
```

An existing database needs `npm run db:migrate8:remote` once, before the Plus code is deployed.

## Tests

`npm test` runs the real routes, SQL and Durable Object classes under Node, against an in-memory
SQLite loaded from `schema.sql`, a fake Google (the service-account token is really signed, with a
throwaway key) and a fake model. It covers every subscription state, verify being idempotent,
duplicate and out-of-order notifications, the midnight reset, the 402 and 429 shapes, refunds, and
the cleanup route's fail-soft paths.

What it cannot cover is the model itself. `npm run eval:cleanup` asks the deployed one, with the
spec's table in six languages — run it before each release and before switching `CLEANUP_MODEL`.

The database is created in Western Europe and the Durable Objects are pinned to the EU
(`meter.ts`, `eu()`), so the balances stay there. That is about where the bytes sit; recordings and
text go to Groq in the United States, and both Cloudflare and Groq are processors.

## The one rule worth repeating

Credit is deducted **before** the upstream call, never after. Bill last and every dropped connection
is a giveaway; bill first and a failure costs a refund instead. Every route in `routes/` follows it,
and `wallet.ts` exists so that "is there enough, and take it" is a single indivisible step.
