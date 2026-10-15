# Billing spec: Plus subscription

Status: draft v0.1, 2026-10-13. Audience: coding agent. Owner: founder.
Items marked **VERIFY** are assumptions to confirm against Google's current documentation before building on them.

## 1. Goal

Replace the credit packs in Dictate Cloud with one Google Play subscription, "Plus", that unlocks managed dictation and cleanup (our server runs Whisper and a small cleanup model). Everything else in the app stays free.

Out of scope for this spec (but the design must leave room for them):
- Referral rewards and welcome offers (stub: the `grants` table, section 5).
- Web or desktop billing through a merchant of record (stub: the `source` column).
- iOS, cloud translation, speaker recognition, voice output.

## 2. Rules

1. **Free is never weaker than the original app.** Anything that arrives from upstream is free forever. Last upstream commit at fork time: `30c2cab` (2026-10-01); tag it `upstream-baseline`. We gate only code we write that depends on our server.
2. **The server is the only authority.** The client is open source and can be patched, so client-side checks only decide what to show. Every paid server route checks entitlement itself.
3. **Anonymous by default.** No email or name on an account. Free users (own key, on-device) never contact our server.
4. **Google is the seller.** Payment, tax, refunds and receipts are Google's. We never see card data.
5. **Believe Google, not the app.** A purchase token sent by the app is verified with Google's API before it counts (same principle as `cloud/src/routes/redeem.ts`).
6. **Never lock out a paying user because of our outage.** If Google cannot be reached, keep the last known entitlement until its expiry date.

## 3. Free versus paid

| Capability | Free | Plus |
| --- | --- | --- |
| Everything in the original app (keyboard, themes, clipboard, floating bubble, on-device models) | Yes | Yes |
| Dictation and rewording with the user's own key, any provider | Yes, unlimited | Yes |
| Local extras we add (dictionary, formatting rules, per-app profiles, bubble skins) | Yes | Yes |
| Managed dictation (provider `cloud`) | Trial only | Daily allowance |
| Managed cleanup and rewording (provider `cloud`), including one-tap cleanup with list formatting (`cleanup.md`) | Trial only | Same allowance |
| Dictionary sync and device pairing | No | Yes |

"Trial" means the Play free-trial offer (section 4), not a separate no-card trial.

## 4. Play Console setup (manual, not code)

0. **Staging first.** Real Play billing cannot be tested without an app in a Play Console, and the organization account may take weeks. Use a personal developer account as an unpublished test lab: upload builds to internal testing under a separate package ID (final ID plus `.staging`), create the same products, and point a staging copy of the server at it (`PACKAGE_NAME` and the Google service account are environment settings). Never publish from the staging account.
1. Create the production app under its final package ID in the organization account (permanent; do this only after the brand name clears). The setup below is identical in both accounts.
2. Subscription product `plus` with two auto-renewing base plans:
   - `monthly`: $3.99
   - `yearly`: $29.99
   Let Google convert prices per country, then review the big markets. Grace period and account hold: use Google's defaults (**VERIFY** the current values).
3. Offers on each base plan:
   - `trial7`: 7-day free trial, new customers. The app's default.
   - `trial30`: 30-day free trial, developer-determined eligibility. The app shows it only when a referral code is present (**VERIFY** how developer-determined eligibility is set in the console).
4. Real-time developer notifications: a Pub/Sub topic pushing to `POST /v1/rtdn?key=<RTDN_SECRET>`, as today.
5. A service account with Play Developer API access (the existing `GOOGLE_SERVICE_ACCOUNT` secret) and the permission to view orders.
6. License testers for all QA. Their purchases are not charged; test subscriptions renew on a shortened schedule (**VERIFY** the schedule).
7. Do not create the old `credits_*` one-time products.

## 5. Data model (D1)

Keep: `wallets`, `tokens`, `usage_log`, `admin_log`. Stop using the credit columns (`seconds_left`, `seconds_bought`, and so on); leave them in place so the admin dashboard keeps running.

Replace `purchases` with:

```sql
CREATE TABLE subscriptions (
  purchase_token  TEXT PRIMARY KEY,              -- Google's token
  wallet_id       TEXT NOT NULL REFERENCES wallets(id),
  source          TEXT NOT NULL DEFAULT 'google_play',  -- google_play | web (reserved)
  product_id      TEXT NOT NULL,                 -- 'plus'
  base_plan_id    TEXT NOT NULL,                 -- monthly | yearly
  offer_id        TEXT,                          -- trial7 | trial30 | NULL
  state           TEXT NOT NULL,                 -- see section 7
  expires_at      INTEGER NOT NULL,              -- unix ms, end of the current paid or trial period
  auto_renewing   INTEGER NOT NULL,
  linked_purchase_token TEXT,                    -- upgrade or resubscribe chain
  latest_order_id TEXT,
  acknowledged    INTEGER NOT NULL DEFAULT 0,
  is_test         INTEGER NOT NULL DEFAULT 0,
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL
);
CREATE INDEX idx_subscriptions_wallet ON subscriptions(wallet_id);
CREATE INDEX idx_subscriptions_expiry ON subscriptions(expires_at);

CREATE TABLE grants (                            -- free time that is not a purchase
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  wallet_id  TEXT NOT NULL REFERENCES wallets(id),
  kind       TEXT NOT NULL,                      -- referral_reward | referral_welcome | gift
  starts_at  INTEGER NOT NULL,
  ends_at    INTEGER NOT NULL,
  note       TEXT,
  created_at INTEGER NOT NULL
);
```

**Entitled until** = the later of: the Play expiry (only while the state grants access, section 7) and the latest `grants.ends_at`.

D1 is the ledger. The `Wallet` Durable Object is what request handling reads, so the hot path stays one call. It stores `entitledUntil`, `plan` and the daily bucket `{day, usedSeconds}`. Every event that changes entitlement (verify, notification, cron, grant) writes the new `entitledUntil` into the Durable Object.

## 6. Server API

All JSON. Errors keep the existing shape (`apiError(status, message, code, type)`).

### `POST /v1/subscription/verify` (replaces `/v1/wallet/redeem`)

Body: `{ purchaseToken, productId: "plus", label?, walletId? }`. Auth is optional: a first purchase has no token yet.

1. Ask Google (`purchases.subscriptionsv2.get`) for the token. The package name must match. Refuse states that grant no access.
2. Token already known: return its wallet. If the caller holds no device token (new phone, reinstall), issue one for that wallet, subject to the existing `MAX_DEVICES` limit. This is the restore path; the recovery code stays as a fallback.
3. Token new: attach to the caller's wallet if authenticated, otherwise create a wallet, device token and recovery code (reuse `createWallet`).
4. If Google reports it unacknowledged, acknowledge it server-side (it must happen within 3 days or Google refunds the purchase). **VERIFY** the acknowledge endpoint for subscriptions.
5. Upsert `subscriptions`, set `entitledUntil` in the Durable Object.
6. Return `{ wallet_id, token?, recovery_code?, entitlement }`.

Errors reuse today's codes: `purchase_pending` (409), `purchase_unknown` (404), `purchase_voided` (403), `google_unavailable` (503, retry).

### `GET /v1/entitlement` (auth)

```json
{
  "plan": "plus",
  "state": "active",
  "expires_at": 1767225600000,
  "auto_renewing": true,
  "source": "google_play",
  "daily": { "allowance_seconds": 5400, "used_seconds": 1200, "resets_at": 1767312000000 },
  "features": ["managed_transcription", "managed_cleanup", "dictionary_sync"]
}
```

`plan` is `plus` or `free`. `features` is computed on the server from the plan, so the app never hard-codes who gets what. Unknown features are ignored by older apps.

### Paid routes (`/v1/audio/transcriptions`, `/v1/chat/completions`, `/v1/cleanup`)

`/v1/cleanup` (one-tap cleanup that formats spoken lists) is specified in `cleanup.md`. It is gated and metered like the other two and shares the daily allowance.

Check entitlement before the upstream call. Two new refusal reasons next to today's `blocked`, `insufficient`, `rate_limited` in `debitError`:
- Not entitled: `402`, code `not_entitled`.
- Daily allowance used: `429`, code `daily_limit`, with `resets_at` in the body and a `Retry-After` header.

The rule "deduct before the upstream call, refund on failure" stays. It now debits the daily bucket instead of a balance.

### `POST /v1/rtdn` (extend)

Add handling for `subscriptionNotification` next to the existing `voidedPurchaseNotification`. Do not trust the notification body for state: read the token from it, re-fetch with `purchases.subscriptionsv2.get`, and map the result (section 7). Duplicate and out-of-order notifications must be harmless. A voided purchase ends access immediately.

### Cron reconcile (extend `sweep.ts` or the hourly cron)

Re-fetch every subscription that is in grace, on hold, or expires within the next 24 hours. This catches lost notifications. Mark a subscription expired once `expires_at` has passed and Google confirms.

### Account deletion (`/v1/wallet/delete`)

Warn when a subscription is active and give the Play cancel link. Keep purchase records as `delete.ts` does today (**VERIFY** retention rules with an accountant).

## 7. State mapping

Map Google's `subscriptionState` to ours. Notification type only triggers a re-fetch.

| Google state | Ours | Access |
| --- | --- | --- |
| Active, in a free-trial offer | `trial` | Yes |
| Active | `active` | Yes |
| Canceled, expiry still ahead | `canceled_active` | Yes, until `expires_at` |
| In grace period | `grace` | Yes (Google keeps access during grace) |
| On hold, or paused | `on_hold` | No. App shows "fix your payment" |
| Expired | `expired` | No |
| Pending | `pending` | No |

Detect a trial from the offer id (`trial7`, `trial30`) while now is before the first renewal.

## 8. Daily allowance

- `DAILY_ALLOWANCE_SECONDS` environment variable, default 5,400 (90 minutes). Reset at 00:00 UTC. No rollover.
- Unit: the same cost-weighted seconds the wallet uses today (transcription seconds plus the cost of a rewording).
- Constraint: allowance x 30 days x cost per second must stay under the yearly plan's net revenue per month (about $2.00). At Whisper's list price, 90 minutes a day is about $1.80 a month before cleanup. Tune from `usage_log` data after the beta.
- Keep the existing global spend ceiling, per-minute rate limit and abuse throttles.

## 9. App changes

All under `app/src/main/kotlin/dev/patrickgold/florisboard/dictate/cloud/` unless noted.

- `DictateCloudBilling.kt`: switch `ProductType.INAPP` to `SUBS` in `products()` and `purchases()`. Query product `plus`. Replace `offerTokenOf` with a picker: base plan by user choice; offer `trial7` by default; `trial30` only when a referral code is stored. Delete `consume()`; subscriptions are acknowledged by the server. Keep `launch()` and the `obfuscatedAccountId` = wallet id.
- `DictateCloudPack.kt`: delete the four credit packs and the `rewords` estimate. Add `DictateCloudPlan` (monthly, yearly). Prices come from Play, never hard-coded.
- `DictateCloud.kt`: replace `redeemPending` with `syncSubscription()`, run at app start and after a purchase: `queryPurchases(SUBS)`, send each token to `/v1/subscription/verify`. Replace `refreshBalance` with `refreshEntitlement`. Keep restore by recovery code, device revoke and delete. Replace the `Settlement` and `PurchaseResult` minute fields with entitlement.
- `DictateCloudApi.kt`: add `verifySubscription()` and `entitlement()`; map `not_entitled` to the paywall state and `daily_limit` to a message with the reset time. Today the app turns 402 into `QUOTA_EXCEEDED`; keep that path and add the new codes.
- `app/.../app/settings/dictate/DictateCloudScreen.kt`: new screen. Plan cards with Play prices, the trial line, current state, a "Manage subscription" link to Play, "Restore", and the recovery code section. No minutes balance. Show trial length, renewal price and how to cancel next to the buy button (**VERIFY** Play's subscription disclosure requirements).
- New `Entitlements` store and feature flag (section 10).

## 10. Paid feature flag

```kotlin
enum class PaidFeature { MANAGED_TRANSCRIPTION, MANAGED_CLEANUP, DICTIONARY_SYNC }

object Entitlements {
    val current: StateFlow<Entitlement>          // cached record from GET /v1/entitlement
    fun has(feature: PaidFeature): Boolean       // for UI decisions only
}
```

- Cache the last record in preferences. Refresh at app start, after a purchase, and on any `not_entitled` response.
- Offline: trust the cache until `expires_at` plus 3 days.
- `has()` decides what to show (paywall versus dictate button). It never gates anything that runs on the device or with the user's own key (rule 1).
- Debug builds only: a developer override to force Plus or free for testing.
- Feature names come from the server list; an unknown name is ignored.

## 11. Edge cases to handle

- **New phone or reinstall:** `queryPurchases` finds the subscription, `verify` issues a device token for the linked wallet. The recovery code is the fallback.
- **Monthly to yearly (or back):** Google issues a new token with `linked_purchase_token`. End the old row, keep one wallet.
- **Resubscribe after expiry:** new token, same wallet (match on the obfuscated account id).
- **Refund or chargeback:** voided notification, access ends at once.
- **Grace and hold:** banner in the app linking to Play's payment settings.
- **Outage on our side:** never downgrade a paying user (rule 6). The existing alerts apply.
- **Many devices:** existing `MAX_DEVICES` and revoke flow.
- **Test purchases:** Google marks them; set `is_test` so they stay out of revenue figures.
- **Price changes:** Google handles consent; ignore the notification.
- **Deleting the account with an active subscription:** warn and link to cancel.
- **Time:** use server time only.

## 12. Tests and acceptance

Server (mocked Google responses): one test per state in section 7; `verify` is idempotent; duplicate and out-of-order notifications; daily bucket resets at 00:00 UTC; the 402 and 429 shapes.

App: offer selection (default, referral, none); cache expiry; paywall states.

Manual, with a license tester:
1. Buy monthly with the trial, dictate through the server.
2. Cancel; access remains until expiry, then ends.
3. Refund; access ends within minutes.
4. Reinstall; the subscription restores without the recovery code.
5. Switch monthly to yearly; one wallet, one active row.

## 13. Order of work

1. Server: migration, Google `subscriptionsv2` client, `/v1/subscription/verify`, `/v1/entitlement`, the `not_entitled` gate. Tests.
2. Server: `subscriptionNotification` handling and the cron reconcile.
3. Server: daily allowance in the `Wallet` Durable Object.
4. App: billing client on `SUBS`, verify, entitlement store, feature flag.
5. App: subscription screen and error states.
6. License-tester pass, then closed beta.
7. Referrals: see `referrals.md`.

## 14. Open questions

- Play-managed trial (card required) versus a no-card trial through `grants`. Current choice: Play-managed. A no-card trial would need Play Integrity to limit repeat abuse.
- Server-side versus app-side acknowledgement. Current choice: server.
- Allowance default (90 minutes) and the reset time (UTC versus the user's local midnight).
- For subscribers who earn a referral reward: use Play's subscription defer function to push their renewal back (**VERIFY**), or bank the days until the subscription lapses.
- Accounting and retention rules for purchase records.
