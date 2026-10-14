/**
 * Who may use the paid routes, and how much of today's allowance is left.
 *
 * Pure functions only — no database, no Durable Object, no fetch — so every rule in the billing
 * spec (sections 5, 7 and 8) can be tested on its own. The glue that reads and writes lives in
 * `subscriptions.ts`; the counter that is actually spent lives in `wallet.ts`.
 *
 * Two words worth keeping apart:
 *  - a **state** is what one subscription is doing (`trial`, `grace`, `on_hold`, …), mapped from
 *    Google's own state and never from the notification type;
 *  - **entitled until** is the one number a request is checked against: the latest moment any
 *    subscription or grant on the account still grants access.
 */

/** The Play subscription product. Must match the Play Console character for character. */
export const PLUS_PRODUCT_ID = 'plus';

/** The free-trial offers, and their length in days (billing spec, section 4). */
export const TRIAL_OFFERS: Record<string, number> = { trial7: 7, trial30: 30 };

/** What a Plus account unlocks. Computed here so the app never hard-codes who gets what. */
export const PLUS_FEATURES = ['managed_transcription', 'managed_cleanup', 'dictionary_sync'] as const;

/**
 * Our states (billing spec, section 7), plus two the app never sees:
 *  - `voided`   a refund or chargeback. Access ends at once and stays ended.
 *  - `replaced` an upgrade or downgrade made a new purchase token; the old one stops counting.
 * Both are terminal: nothing Google says later about the same token moves it back.
 */
export type SubState =
  | 'trial'
  | 'active'
  | 'canceled_active'
  | 'grace'
  | 'on_hold'
  | 'expired'
  | 'pending'
  | 'voided'
  | 'replaced';

export const ACCESS_STATES: ReadonlySet<SubState> = new Set(['trial', 'active', 'canceled_active', 'grace']);
export const TERMINAL_STATES: ReadonlySet<SubState> = new Set(['voided', 'replaced']);

const DAY_MS = 86_400_000;

/**
 * How long a grace period is assumed to last past a stale expiry.
 *
 * Google keeps access during grace and is expected to move `expiryTime` to the end of it, but if a
 * reply ever carries the old expiry, the user would be cut off while Google still says "pay later".
 * A day is enough: the hourly reconcile re-reads every subscription in grace.
 */
export const GRACE_SLACK_MS = DAY_MS;

/** The parts of Google's `SubscriptionPurchaseV2` this service reads. */
export interface GoogleSubscription {
  subscriptionState?: string;
  startTime?: string;
  regionCode?: string;
  linkedPurchaseToken?: string;
  latestOrderId?: string;
  acknowledgementState?: string;
  /** Present (as an empty object) only for a licence tester's purchase. */
  testPurchase?: object;
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
  lineItems?: Array<{
    productId?: string;
    expiryTime?: string;
    latestSuccessfulOrderId?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
    offerDetails?: { basePlanId?: string; offerId?: string; offerTags?: string[] };
    /**
     * Which phase of the offer the item is in. **VERIFY**: a newer field of the v2 resource; when
     * it is absent the trial is inferred from the offer id and the period length instead.
     */
    offerPhase?: { freeTrial?: object; introductoryPrice?: object; basePrice?: object };
  }>;
}

/** One subscription, as this service stores it. */
export interface SubscriptionFacts {
  state: SubState;
  productId: string;
  basePlanId: string;
  offerId: string | null;
  /** Unix ms. End of the current paid or trial period. */
  expiresAt: number;
  autoRenewing: boolean;
  linkedPurchaseToken: string | null;
  latestOrderId: string | null;
  acknowledged: boolean;
  isTest: boolean;
  /** The wallet id the app attached at purchase time. The client's claim, not Google's. */
  obfuscatedAccountId: string | null;
  startedAt: number;
}

/**
 * Google's answer, in our words. Null when the purchase holds no line item for [productId] — a
 * token for some other product is not a Plus subscription, whatever the app claims.
 *
 * The notification type is never consulted: it only says *that* something changed. What changed
 * is read here, from the current resource, which is what makes duplicate and out-of-order
 * notifications harmless.
 */
export function mapSubscription(
  google: GoogleSubscription,
  now: number,
  productId: string = PLUS_PRODUCT_ID,
): SubscriptionFacts | null {
  const item = google.lineItems?.find((i) => i.productId === productId);
  if (!item) return null;

  const expiresAt = parseTime(item.expiryTime);
  const startedAt = parseTime(google.startTime) || now;
  const offerId = item.offerDetails?.offerId ?? null;

  return {
    state: stateOf(google.subscriptionState, expiresAt, now, isTrial(item, offerId, startedAt, expiresAt)),
    productId,
    basePlanId: item.offerDetails?.basePlanId ?? '',
    offerId,
    expiresAt,
    autoRenewing: item.autoRenewingPlan?.autoRenewEnabled === true,
    linkedPurchaseToken: google.linkedPurchaseToken ?? null,
    latestOrderId: item.latestSuccessfulOrderId ?? google.latestOrderId ?? null,
    acknowledged: google.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
    isTest: google.testPurchase !== undefined,
    obfuscatedAccountId: google.externalAccountIdentifiers?.obfuscatedExternalAccountId ?? null,
    startedAt,
  };
}

function stateOf(google: string | undefined, expiresAt: number, now: number, trial: boolean): SubState {
  switch (google) {
    case 'SUBSCRIPTION_STATE_ACTIVE':
      return trial ? 'trial' : 'active';
    case 'SUBSCRIPTION_STATE_CANCELED':
      // Cancelled means "will not renew", not "ends now": access runs to the end of what was paid.
      return expiresAt > now ? 'canceled_active' : 'expired';
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      return 'grace';
    case 'SUBSCRIPTION_STATE_ON_HOLD':
    case 'SUBSCRIPTION_STATE_PAUSED':
      return 'on_hold';
    case 'SUBSCRIPTION_STATE_PENDING':
      return 'pending';
    case 'SUBSCRIPTION_STATE_EXPIRED':
    case 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED':
      return 'expired';
    default:
      // A state Google added after this was written. Rule 6 decides it: a paying user is not
      // locked out over something on our side, so a period that is still running keeps access.
      return expiresAt > now ? 'active' : 'expired';
  }
}

/**
 * Whether the current period is a free trial.
 *
 * Google's `offerPhase` says so outright where it is sent. Without it, a trial offer counts as a
 * trial only while the subscription is still in its first period: once the expiry lies further
 * from the start than the trial lasts (plus a day of slack), it has renewed into a paid period.
 */
function isTrial(
  item: NonNullable<GoogleSubscription['lineItems']>[number],
  offerId: string | null,
  startedAt: number,
  expiresAt: number,
): boolean {
  if (item.offerPhase) return item.offerPhase.freeTrial !== undefined;
  const days = offerId ? TRIAL_OFFERS[offerId] : undefined;
  if (!days) return false;
  return expiresAt - startedAt <= (days + 1) * DAY_MS;
}

function parseTime(value: string | undefined): number {
  const t = value ? Date.parse(value) : NaN;
  return Number.isFinite(t) ? t : 0;
}

/** One subscription row, as far as entitlement is concerned. */
export interface SubscriptionRow {
  state: SubState;
  expiresAt: number;
  autoRenewing: boolean;
  source: string;
  updatedAt: number;
}

/** Free time that is not a purchase (billing spec, section 5). */
export interface GrantRow {
  startsAt: number;
  endsAt: number;
}

/** What one subscription contributes to "entitled until": 0 when it grants nothing. */
export function accessUntil(row: Pick<SubscriptionRow, 'state' | 'expiresAt'>, now: number): number {
  if (!ACCESS_STATES.has(row.state)) return 0;
  if (row.state === 'grace') return Math.max(row.expiresAt, now + GRACE_SLACK_MS);
  return row.expiresAt;
}

/**
 * **Entitled until** = the later of every subscription's expiry (only while its state grants
 * access) and every grant's end. 0 means never entitled, or not any more.
 *
 * A grant that has not started yet does not count: a referral reward booked for next month is not
 * access today.
 */
export function entitledUntil(subs: SubscriptionRow[], grants: GrantRow[], now: number): number {
  let until = 0;
  for (const s of subs) until = Math.max(until, accessUntil(s, now));
  for (const g of grants) if (g.startsAt <= now) until = Math.max(until, g.endsAt);
  return until;
}

/**
 * The subscription the app should be told about: the one granting access the longest, or else
 * the one most recently changed — that is the one a "fix your payment" or "expired" refers to.
 */
export function primarySubscription(subs: SubscriptionRow[], now: number): SubscriptionRow | null {
  const granting = subs
    .filter((s) => accessUntil(s, now) > now)
    .sort((a, b) => accessUntil(b, now) - accessUntil(a, now));
  if (granting[0]) return granting[0];
  const visible = subs.filter((s) => s.state !== 'replaced');
  return [...visible].sort((a, b) => b.updatedAt - a.updatedAt)[0] ?? null;
}

/** The daily counter as the wallet stores it. */
export interface DailyBucket {
  /** UTC day, `YYYY-MM-DD`. */
  day: string;
  usedSeconds: number;
}

export function utcDay(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

/** 00:00 UTC after [now] — when today's allowance starts again. No rollover. */
export function nextUtcMidnight(now: number): number {
  return (Math.floor(now / DAY_MS) + 1) * DAY_MS;
}

/** Seconds used today. A bucket from an earlier day is simply empty: that is the whole reset. */
export function usedToday(bucket: DailyBucket | undefined, now: number): number {
  return bucket && bucket.day === utcDay(now) ? bucket.usedSeconds : 0;
}

/**
 * Whether another request may start.
 *
 * Refused only once the allowance is fully used, not when the next request would cross it: a
 * five-minute dictation with two minutes left goes through, and the overshoot is bounded by the
 * longest request there is. Refusing it instead would turn the last minutes of every day into a
 * guessing game about how long one may talk.
 */
export function allowanceLeft(bucket: DailyBucket | undefined, allowanceSeconds: number, now: number): boolean {
  return usedToday(bucket, now) < allowanceSeconds;
}

/** Books [seconds] onto today's bucket, starting a fresh one when the day has turned. */
export function bookDaily(bucket: DailyBucket | undefined, seconds: number, now: number): DailyBucket {
  const day = utcDay(now);
  const used = bucket && bucket.day === day ? bucket.usedSeconds : 0;
  return { day, usedSeconds: Math.max(0, used + seconds) };
}

/**
 * Corrects a booking made on [day]. A refund or settlement that lands after midnight belongs to a
 * bucket that no longer exists, and is dropped rather than taken off the new day.
 */
export function correctDaily(bucket: DailyBucket | undefined, day: string, deltaSeconds: number): DailyBucket | undefined {
  if (!bucket || bucket.day !== day) return bucket;
  return { day, usedSeconds: Math.max(0, bucket.usedSeconds + deltaSeconds) };
}

/** `GET /v1/entitlement`, as the app reads it (billing spec, section 6). */
export interface EntitlementView {
  plan: 'plus' | 'free';
  state: string;
  expires_at: number;
  auto_renewing: boolean;
  source: string;
  daily?: { allowance_seconds: number; used_seconds: number; resets_at: number };
  features: string[];
}

/**
 * Builds the record the app caches. `voided` and `replaced` are reported as `expired`: they are
 * ours, the app knows the section 7 vocabulary and nothing else.
 */
export function entitlementView(
  input: {
    subs: SubscriptionRow[];
    /** From [entitledUntil]. */
    until: number;
    daily: DailyBucket | undefined;
    allowanceSeconds: number;
  },
  now: number,
): EntitlementView {
  const plus = input.until > now;
  const primary = primarySubscription(input.subs, now);
  const fromSub = primary ? accessUntil(primary, now) : 0;

  let state: string = primary ? publicState(primary.state) : 'none';
  let source = primary?.source ?? '';
  // Access that no subscription explains comes from a grant.
  if (plus && fromSub < input.until && fromSub <= now) {
    state = 'active';
    source = 'grant';
  }

  return {
    plan: plus ? 'plus' : 'free',
    state,
    expires_at: plus ? input.until : primary?.expiresAt ?? 0,
    auto_renewing: primary?.autoRenewing ?? false,
    source,
    ...(plus
      ? {
          daily: {
            allowance_seconds: input.allowanceSeconds,
            used_seconds: usedToday(input.daily, now),
            resets_at: nextUtcMidnight(now),
          },
        }
      : {}),
    features: plus ? [...PLUS_FEATURES] : [],
  };
}

export function publicState(state: SubState): string {
  return state === 'voided' || state === 'replaced' ? 'expired' : state;
}
