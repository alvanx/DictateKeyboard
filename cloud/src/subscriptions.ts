import type { Env, Limits } from './config';
import {
  PLUS_PRODUCT_ID,
  entitledUntil,
  entitlementView,
  mapSubscription,
  type EntitlementView,
  type GrantRow,
  type SubState,
  type SubscriptionFacts,
  type SubscriptionRow,
} from './entitlement';
import { acknowledgeSubscription, fetchSubscription } from './google';
import { walletStub } from './meter';
import type { DebitResult } from './wallet';

/**
 * Plus subscriptions: the ledger in D1, Google as the authority, the wallet as the copy the hot
 * path reads (billing spec, sections 5 to 7).
 *
 * Every path that learns something — the app's verify call, a notification, the hourly reconcile,
 * a refused request rechecking — ends in the same two steps: write what Google said into
 * `subscriptions`, then recompute "entitled until" from the whole ledger and hand it to the wallet.
 * Recomputing from the ledger rather than patching the wallet is what keeps the two from drifting:
 * the wallet's number is always a function of rows that can be read and checked.
 */

const MINUTE_MS = 60_000;
const DAY_MS = 86_400_000;

/**
 * How long past expiry an auto-renewing subscription is still believed while Google cannot be
 * asked. The same three days the app trusts its own cache for (billing spec, section 10): a renewal
 * that happened during our outage must not show up as a paywall.
 */
const OFFLINE_GRACE_MS = 3 * DAY_MS;

/** What a refused request may borrow while Google is unreachable. Corrected by the next reconcile. */
const BRIDGE_MS = 60 * MINUTE_MS;

/** A refused request asks Google again at most this often per subscription. */
const RECHECK_EVERY_MS = 10 * MINUTE_MS;

/**
 * Writes what Google says about one purchase token.
 *
 * `wallet_id` is set on the first write and never moved by a later one — a subscription belongs
 * to the account that first verified it. `voided` and `replaced` are terminal: a late or repeated
 * answer about the same token cannot bring it back, which is half of what makes duplicate and
 * out-of-order notifications harmless. The other half is that every answer is Google's current
 * state, never the notification's.
 */
export async function upsertSubscription(
  env: Env,
  purchaseToken: string,
  walletId: string,
  facts: SubscriptionFacts,
  now: number,
): Promise<void> {
  const statements = [
    env.DB.prepare(
      `INSERT INTO subscriptions (purchase_token, wallet_id, source, product_id, base_plan_id, offer_id, state,
                                  expires_at, auto_renewing, linked_purchase_token, latest_order_id,
                                  acknowledged, is_test, created_at, updated_at)
       VALUES (?, ?, 'google_play', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(purchase_token) DO UPDATE SET
         state = CASE WHEN subscriptions.state IN ('voided', 'replaced') THEN subscriptions.state
                      ELSE excluded.state END,
         base_plan_id          = excluded.base_plan_id,
         offer_id              = excluded.offer_id,
         expires_at            = excluded.expires_at,
         auto_renewing         = excluded.auto_renewing,
         linked_purchase_token = COALESCE(excluded.linked_purchase_token, subscriptions.linked_purchase_token),
         latest_order_id       = COALESCE(excluded.latest_order_id, subscriptions.latest_order_id),
         acknowledged          = MAX(subscriptions.acknowledged, excluded.acknowledged),
         is_test               = excluded.is_test,
         updated_at            = excluded.updated_at`,
    ).bind(
      purchaseToken,
      walletId,
      facts.productId,
      facts.basePlanId,
      facts.offerId,
      facts.state,
      facts.expiresAt,
      facts.autoRenewing ? 1 : 0,
      facts.linkedPurchaseToken,
      facts.latestOrderId,
      facts.acknowledged ? 1 : 0,
      facts.isTest ? 1 : 0,
      now,
      now,
    ),
  ];
  // Monthly to yearly, or back: Google issues a new token that names the old one. The old row ends
  // here rather than whenever Google gets round to calling it expired, so one wallet never counts
  // two plans at once.
  if (facts.linkedPurchaseToken && facts.linkedPurchaseToken !== purchaseToken) {
    statements.push(
      env.DB.prepare(
        `UPDATE subscriptions SET state = 'replaced', auto_renewing = 0, updated_at = ?
          WHERE purchase_token = ? AND state NOT IN ('voided', 'replaced')`,
      ).bind(now, facts.linkedPurchaseToken),
    );
  }
  await env.DB.batch(statements);
}

/** The wallet a purchase token is recorded on, with that wallet's status. */
export async function subscriptionOwner(
  env: Env,
  purchaseToken: string,
): Promise<{ walletId: string; state: SubState; walletStatus: string | null } | null> {
  return env.DB.prepare(
    `SELECT s.wallet_id AS walletId, s.state, w.status AS walletStatus
       FROM subscriptions s LEFT JOIN wallets w ON w.id = s.wallet_id
      WHERE s.purchase_token = ?`,
  ).bind(purchaseToken).first<{ walletId: string; state: SubState; walletStatus: string | null }>();
}

async function ledgerFor(env: Env, walletId: string): Promise<{ subs: SubscriptionRow[]; grants: GrantRow[] }> {
  const [subs, grants] = await env.DB.batch([
    env.DB.prepare(
      `SELECT state, expires_at AS expiresAt, auto_renewing AS autoRenewing, source, updated_at AS updatedAt
         FROM subscriptions WHERE wallet_id = ?`,
    ).bind(walletId),
    env.DB.prepare('SELECT starts_at AS startsAt, ends_at AS endsAt FROM grants WHERE wallet_id = ?').bind(walletId),
  ]);
  return {
    subs: ((subs?.results ?? []) as Array<SubscriptionRow & { autoRenewing: number | boolean }>).map((r) => ({
      state: r.state,
      expiresAt: Number(r.expiresAt),
      autoRenewing: Number(r.autoRenewing) === 1,
      source: r.source,
      updatedAt: Number(r.updatedAt),
    })),
    grants: ((grants?.results ?? []) as GrantRow[]).map((g) => ({ startsAt: Number(g.startsAt), endsAt: Number(g.endsAt) })),
  };
}

/**
 * Recomputes "entitled until" from the ledger and writes it into the wallet.
 *
 * A deleted account is left alone: its Durable Object was erased on purpose, and writing to it
 * would bring storage back for an account nobody can reach.
 */
export async function refreshWalletEntitlement(
  env: Env,
  walletId: string,
  now: number,
): Promise<{ until: number; subs: SubscriptionRow[] }> {
  const { subs, grants } = await ledgerFor(env, walletId);
  const until = entitledUntil(subs, grants, now);
  const wallet = await env.DB.prepare('SELECT status FROM wallets WHERE id = ?')
    .bind(walletId).first<{ status: string }>();
  if (wallet && wallet.status !== 'deleted') {
    await walletStub(env, walletId).setEntitlement(until);
  }
  return { until, subs };
}

/** `GET /v1/entitlement`'s body, fresh from the ledger and the wallet's daily counter. */
export async function buildEntitlement(
  env: Env,
  walletId: string,
  limits: Limits,
  now: number,
): Promise<EntitlementView> {
  const { until, subs } = await refreshWalletEntitlement(env, walletId, now);
  const state = await walletStub(env, walletId).state();
  return entitlementView(
    {
      subs,
      until,
      daily: { day: new Date(now).toISOString().slice(0, 10), usedSeconds: state.dailyUsedSeconds },
      allowanceSeconds: limits.dailyAllowanceSeconds,
    },
    now,
  );
}

export type RefreshOutcome = 'updated' | 'gone' | 'not_ours';

/**
 * Asks Google about one token and records the answer.
 *
 * Throws when Google cannot be reached, so the caller can keep what is known (rule 6) instead of
 * mistaking an outage for an expiry.
 *
 * A token this service has never seen is recorded only when Google links it to one it has — an
 * upgrade or a resubscribe whose notification arrived before the app got round to verifying it.
 * Anything else waits for the app: without its call there is no account to attach it to.
 */
export async function refreshFromGoogle(env: Env, purchaseToken: string, now: number): Promise<RefreshOutcome> {
  const known = await env.DB.prepare(
    'SELECT wallet_id AS walletId, product_id AS productId FROM subscriptions WHERE purchase_token = ?',
  ).bind(purchaseToken).first<{ walletId: string; productId: string }>();

  const google = await fetchSubscription(env, purchaseToken);
  if (!google) {
    if (!known) return 'not_ours';
    // Google forgets a token some weeks after it expired. A period that has run out is over; one
    // that has not is left as it is rather than ended on the strength of a missing record.
    await env.DB.prepare(
      `UPDATE subscriptions SET state = 'expired', auto_renewing = 0, updated_at = ?
        WHERE purchase_token = ? AND state NOT IN ('voided', 'replaced') AND expires_at <= ?`,
    ).bind(now, purchaseToken, now).run();
    await refreshWalletEntitlement(env, known.walletId, now);
    return 'gone';
  }

  const facts = mapSubscription(google, now, known?.productId ?? PLUS_PRODUCT_ID);
  if (!facts) return 'not_ours';

  let walletId = known?.walletId ?? null;
  let previousWallet: string | null = null;
  if (facts.linkedPurchaseToken) {
    const linked = await subscriptionOwner(env, facts.linkedPurchaseToken);
    if (linked && linked.walletStatus === 'active') {
      if (!walletId) walletId = linked.walletId;
      if (linked.walletId !== walletId) previousWallet = linked.walletId;
    }
  }
  if (!walletId) return 'not_ours';

  await upsertSubscription(env, purchaseToken, walletId, facts, now);
  if (!facts.acknowledged && facts.state !== 'pending') await acknowledge(env, purchaseToken, facts.productId);
  await refreshWalletEntitlement(env, walletId, now);
  if (previousWallet) await refreshWalletEntitlement(env, previousWallet, now);
  return 'updated';
}

/**
 * Acknowledges with Google and records that it worked. A failure is left for the reconcile, which
 * picks up every row still marked unacknowledged; Google gives three days.
 */
export async function acknowledge(env: Env, purchaseToken: string, productId: string): Promise<boolean> {
  const ok = await acknowledgeSubscription(env, productId, purchaseToken).catch(() => false);
  if (ok) {
    await env.DB.prepare('UPDATE subscriptions SET acknowledged = 1 WHERE purchase_token = ?')
      .bind(purchaseToken).run();
  }
  return ok;
}

/**
 * A refund or chargeback on a subscription: access ends at once (billing spec, section 11).
 * Returns false when the token is not a subscription of ours or was already voided — which is
 * what lets the nightly sweep offer the same token again and again at no cost.
 */
export async function voidSubscription(env: Env, purchaseToken: string, actor: string, now: number): Promise<boolean> {
  const row = await subscriptionOwner(env, purchaseToken);
  if (!row || row.state === 'voided') return false;

  await env.DB.batch([
    env.DB.prepare(
      "UPDATE subscriptions SET state = 'voided', auto_renewing = 0, updated_at = ? WHERE purchase_token = ?",
    ).bind(now, purchaseToken),
    env.DB.prepare('UPDATE wallets SET void_count = COALESCE(void_count, 0) + 1 WHERE id = ?').bind(row.walletId),
    env.DB.prepare(
      `INSERT INTO admin_log (ts, actor, wallet_id, action, delta_secs, delta_words, note)
       VALUES (?, ?, ?, 'void', 0, 0, ?)`,
    ).bind(now, actor, row.walletId, `Abo erstattet (${purchaseToken.slice(0, 12)}…) — Zugang sofort beendet.`),
  ]);
  await refreshWalletEntitlement(env, row.walletId, now);
  return true;
}

/**
 * The hourly safety net under the notifications (billing spec, section 6, "Cron reconcile").
 *
 * Re-reads every subscription whose state can change without us hearing about it: in grace, on
 * hold or pending, not yet acknowledged, or running out within a day. A lost notification about a
 * renewal is then at worst an hour late — and never a lockout, because a refused request rechecks
 * on its own (`debitPaid`).
 *
 * Stops at the first failure to reach Google: an outage is not a reason to ask a hundred times,
 * and nothing is changed for a token that could not be checked.
 */
export async function reconcileSubscriptions(
  env: Env,
  now: number,
  limit = 100,
): Promise<{ checked: number; failed: boolean }> {
  const due = await env.DB.prepare(
    `SELECT purchase_token AS token FROM subscriptions
      WHERE state NOT IN ('voided', 'replaced', 'expired')
        AND (state IN ('grace', 'on_hold', 'pending') OR acknowledged = 0 OR expires_at < ?)
      ORDER BY expires_at ASC LIMIT ?`,
  ).bind(now + DAY_MS, limit).all<{ token: string }>();

  let checked = 0;
  for (const row of due.results ?? []) {
    try {
      await refreshFromGoogle(env, row.token, now);
      checked++;
    } catch (error) {
      console.log(`subscription reconcile stopped: ${String(error).slice(0, 200)}`);
      return { checked, failed: true };
    }
  }
  return { checked, failed: false };
}

/**
 * Books a paid request, rechecking with Google once before saying "not entitled".
 *
 * The wallet only knows what it was last told. If the notification about a renewal or a fixed
 * payment was lost, the paying user would meet a paywall until the next reconcile — so a refusal
 * for want of Plus first looks at the ledger, then asks Google about the subscriptions that could
 * plausibly have changed, at most once every ten minutes each. If Google cannot be reached about
 * one that was renewing, the request is let through on a short bridge: rule 6, never lock out a
 * paying user because of an outage, ours or Google's.
 */
export async function debitPaid(
  env: Env,
  walletId: string,
  seconds: number,
  limits: Limits,
  now = Date.now(),
): Promise<DebitResult> {
  const wallet = walletStub(env, walletId);
  const first = await wallet.debit(seconds, limits.rateLimitPerMinute);
  if (first.ok || first.reason !== 'not_entitled') return first;
  if (!(await recheckEntitlement(env, walletId, now))) return first;
  return wallet.debit(seconds, limits.rateLimitPerMinute);
}

async function recheckEntitlement(env: Env, walletId: string, now: number): Promise<boolean> {
  // The ledger may already know better than the wallet — a write that did not reach it.
  const fromLedger = await refreshWalletEntitlement(env, walletId, now);
  if (fromLedger.until > now) return true;

  const candidates = await env.DB.prepare(
    `SELECT purchase_token AS token, state, auto_renewing AS autoRenewing, expires_at AS expiresAt
       FROM subscriptions
      WHERE wallet_id = ? AND state NOT IN ('voided', 'replaced') AND updated_at < ?
        AND ((auto_renewing = 1 AND expires_at > ?) OR state IN ('grace', 'on_hold', 'pending'))
      ORDER BY expires_at DESC LIMIT 2`,
  ).bind(walletId, now - RECHECK_EVERY_MS, now - OFFLINE_GRACE_MS)
    .all<{ token: string; state: SubState; autoRenewing: number; expiresAt: number }>();

  for (const row of candidates.results ?? []) {
    try {
      await refreshFromGoogle(env, row.token, now);
    } catch {
      const wasRenewing = Number(row.autoRenewing) === 1 && !['on_hold', 'pending'].includes(row.state);
      if (wasRenewing && Number(row.expiresAt) > now - OFFLINE_GRACE_MS) {
        await walletStub(env, walletId).setEntitlement(now + BRIDGE_MS);
        return true;
      }
    }
  }
  const after = await walletStub(env, walletId).state();
  return after.entitledUntil > now;
}
