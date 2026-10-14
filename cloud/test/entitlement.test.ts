import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  bookDaily,
  correctDaily,
  entitledUntil,
  entitlementView,
  mapSubscription,
  nextUtcMidnight,
  usedToday,
  allowanceLeft,
  GRACE_SLACK_MS,
  type SubscriptionRow,
} from '../src/entitlement';
import { DAY, googleSub } from './harness';

const NOW = Date.parse('2026-10-14T12:00:00Z');

function stateFor(opts: Parameters<typeof googleSub>[0]) {
  return mapSubscription(googleSub({ now: NOW, ...opts }), NOW)!.state;
}

// Billing spec, section 7: one test per state.
test('active maps to active', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_ACTIVE' }), 'active');
});
test('active inside the 7-day trial offer maps to trial', () => {
  assert.equal(stateFor({ offerId: 'trial7', startedAgo: 2 * DAY, expiresIn: 5 * DAY }), 'trial');
});
test('a trial offer that has renewed into a paid period is active, not trial', () => {
  assert.equal(stateFor({ offerId: 'trial7', startedAgo: 20 * DAY, expiresIn: 17 * DAY }), 'active');
});
test("Google's offerPhase, where sent, decides the trial outright", () => {
  const g = googleSub({ now: NOW, offerId: 'trial30', startedAgo: DAY, expiresIn: 29 * DAY });
  g.lineItems![0]!.offerPhase = { basePrice: {} };
  assert.equal(mapSubscription(g, NOW)!.state, 'active');
  g.lineItems![0]!.offerPhase = { freeTrial: {} };
  assert.equal(mapSubscription(g, NOW)!.state, 'trial');
});
test('canceled with time left maps to canceled_active', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_CANCELED', expiresIn: 3 * DAY }), 'canceled_active');
});
test('canceled after its expiry maps to expired', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_CANCELED', expiresIn: -DAY }), 'expired');
});
test('grace period maps to grace', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD' }), 'grace');
});
test('on hold and paused both map to on_hold', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_ON_HOLD' }), 'on_hold');
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_PAUSED' }), 'on_hold');
});
test('expired maps to expired', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_EXPIRED', expiresIn: -DAY }), 'expired');
});
test('pending maps to pending, a cancelled pending purchase to expired', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_PENDING' }), 'pending');
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED' }), 'expired');
});
test('a state Google adds later keeps a running period (rule 6)', () => {
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_SOMETHING_NEW', expiresIn: DAY }), 'active');
  assert.equal(stateFor({ state: 'SUBSCRIPTION_STATE_SOMETHING_NEW', expiresIn: -DAY }), 'expired');
});
test('a token without a Plus line item is not a Plus subscription', () => {
  const g = googleSub({ now: NOW });
  g.lineItems![0]!.productId = 'something_else';
  assert.equal(mapSubscription(g, NOW), null);
});
test('test purchases, links and the account id are carried over', () => {
  const facts = mapSubscription(googleSub({ now: NOW, test: true, linked: 'old', accountId: 'w1', basePlan: 'yearly' }), NOW)!;
  assert.equal(facts.isTest, true);
  assert.equal(facts.linkedPurchaseToken, 'old');
  assert.equal(facts.obfuscatedAccountId, 'w1');
  assert.equal(facts.basePlanId, 'yearly');
  assert.equal(facts.acknowledged, false);
});

const row = (state: SubscriptionRow['state'], expiresAt: number, updatedAt = NOW): SubscriptionRow =>
  ({ state, expiresAt, autoRenewing: true, source: 'google_play', updatedAt });

test('entitled until: access states count, others do not', () => {
  assert.equal(entitledUntil([row('active', NOW + DAY)], [], NOW), NOW + DAY);
  assert.equal(entitledUntil([row('canceled_active', NOW + 2 * DAY)], [], NOW), NOW + 2 * DAY);
  for (const s of ['on_hold', 'expired', 'pending', 'voided', 'replaced'] as const) {
    assert.equal(entitledUntil([row(s, NOW + DAY)], [], NOW), 0, s);
  }
});
test('entitled until: a grace period is never cut short by a stale expiry', () => {
  assert.equal(entitledUntil([row('grace', NOW - 1000)], [], NOW), NOW + GRACE_SLACK_MS);
});
test('entitled until: the later of subscription and grant; a future grant does not count yet', () => {
  assert.equal(entitledUntil([row('active', NOW + DAY)], [{ startsAt: NOW - DAY, endsAt: NOW + 5 * DAY }], NOW), NOW + 5 * DAY);
  assert.equal(entitledUntil([], [{ startsAt: NOW + DAY, endsAt: NOW + 5 * DAY }], NOW), 0);
});

// Billing spec, section 8: the daily bucket.
test('the daily bucket resets at 00:00 UTC, with no rollover', () => {
  const lateEvening = Date.parse('2026-10-14T23:59:00Z');
  const bucket = bookDaily(undefined, 5000, lateEvening);
  assert.equal(usedToday(bucket, lateEvening), 5000);
  assert.equal(allowanceLeft(bucket, 5400, lateEvening), true);
  const full = bookDaily(bucket, 400, lateEvening);
  assert.equal(allowanceLeft(full, 5400, lateEvening), false);
  const justAfter = Date.parse('2026-10-15T00:00:01Z');
  assert.equal(usedToday(full, justAfter), 0);
  assert.equal(allowanceLeft(full, 5400, justAfter), true);
  assert.equal(bookDaily(full, 10, justAfter).usedSeconds, 10);
  assert.equal(nextUtcMidnight(lateEvening), Date.parse('2026-10-15T00:00:00Z'));
});
test('a refund that lands after midnight does not touch the new day', () => {
  const bucket = { day: '2026-10-15', usedSeconds: 100 };
  assert.deepEqual(correctDaily(bucket, '2026-10-14', -50), bucket);
  assert.equal(correctDaily(bucket, '2026-10-15', -50)!.usedSeconds, 50);
  assert.equal(correctDaily(bucket, '2026-10-15', -500)!.usedSeconds, 0);
});

test('entitlement view: Plus carries the daily allowance and the features', () => {
  const view = entitlementView({
    subs: [row('active', NOW + DAY)],
    until: NOW + DAY,
    daily: { day: '2026-10-14', usedSeconds: 1200 },
    allowanceSeconds: 5400,
  }, NOW);
  assert.deepEqual(view, {
    plan: 'plus',
    state: 'active',
    expires_at: NOW + DAY,
    auto_renewing: true,
    source: 'google_play',
    daily: { allowance_seconds: 5400, used_seconds: 1200, resets_at: Date.parse('2026-10-15T00:00:00Z') },
    features: ['managed_transcription', 'managed_cleanup', 'dictionary_sync'],
  });
});
test('entitlement view: on hold is free, with the state the app needs for "fix your payment"', () => {
  const view = entitlementView({ subs: [row('on_hold', NOW - DAY)], until: 0, daily: undefined, allowanceSeconds: 5400 }, NOW);
  assert.equal(view.plan, 'free');
  assert.equal(view.state, 'on_hold');
  assert.deepEqual(view.features, []);
  assert.equal(view.daily, undefined);
});
test('entitlement view: voided reads as expired; nothing at all reads as none', () => {
  assert.equal(entitlementView({ subs: [row('voided', NOW + DAY)], until: 0, daily: undefined, allowanceSeconds: 1 }, NOW).state, 'expired');
  assert.equal(entitlementView({ subs: [], until: 0, daily: undefined, allowanceSeconds: 1 }, NOW).state, 'none');
});
test('entitlement view: access from a grant alone says so', () => {
  const view = entitlementView({ subs: [row('expired', NOW - DAY)], until: NOW + DAY, daily: undefined, allowanceSeconds: 1 }, NOW);
  assert.equal(view.plan, 'plus');
  assert.equal(view.source, 'grant');
});
