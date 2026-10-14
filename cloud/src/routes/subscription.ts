import { authFailure, authenticate, touch } from '../auth';
import { limitsFrom, type Env } from '../config';
import { PLUS_PRODUCT_ID, mapSubscription } from '../entitlement';
import { fetchSubscription } from '../google';
import {
  acknowledge,
  buildEntitlement,
  refreshWalletEntitlement,
  subscriptionOwner,
  upsertSubscription,
} from '../subscriptions';
import { apiError, json, sha256 } from '../util';
import { createWallet, issueDeviceToken } from './wallet';

/**
 * `POST /v1/subscription/verify` — a Play subscription becomes an account with Plus.
 *
 * Replaces `/v1/wallet/redeem`. The same rule holds: the token the app sends is not believed, only
 * Google's answer is (rule 5). And the token is the primary key, so verifying the same purchase
 * twice returns the same account — which is not an edge case but the design: the app calls this at
 * every start for whatever Play lists, and that is also how a reinstall finds its account again.
 *
 * Which account a token lands on, in order:
 *  1. The one it is already recorded on. A caller without a token for it gets one (the restore
 *     path, subject to the device limit) — whoever holds the purchase token holds the Play account
 *     that paid.
 *  2. For a new token that Google links to an earlier one (upgrade, resubscribe): that one's.
 *  3. The caller's own, when it sends a valid token.
 *  4. Otherwise a new account, with a token and a recovery code, handed out this once.
 *
 * `obfuscatedExternalAccountId` is deliberately *not* used to pick an account. It is the wallet id
 * the app attached at purchase time — the client's claim, echoed by Google, not proof of anything —
 * and acting on it would let anyone who learned a wallet id buy their way into it.
 */
export async function handleVerify(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  let payload: { purchaseToken?: string; productId?: string; walletId?: string; label?: string };
  try {
    payload = await request.json();
  } catch {
    return apiError(400, 'Request is not valid JSON.', 'bad_request', 'invalid_request_error');
  }

  const purchaseToken = payload.purchaseToken?.trim();
  const productId = payload.productId?.trim() || PLUS_PRODUCT_ID;
  if (!purchaseToken) {
    return apiError(400, 'purchaseToken is required.', 'bad_request', 'invalid_request_error');
  }
  if (productId !== PLUS_PRODUCT_ID) {
    return apiError(400, `Unknown product: ${productId}`, 'unknown_product', 'invalid_request_error');
  }

  // Optional: a first purchase has no token yet. A token that does not work is treated as none,
  // rather than refused — the purchase is the stronger credential here.
  const session = await authenticate(request, env);
  if (session) touch(env, session, ctx);

  const now = Date.now();
  let google;
  try {
    google = await fetchSubscription(env, purchaseToken);
  } catch {
    // Our problem or Google's, not the purchase's: the app keeps it and asks again.
    return apiError(503, 'Purchase verification is unavailable right now.', 'google_unavailable', 'server_error');
  }
  const facts = google ? mapSubscription(google, now, productId) : null;
  if (!facts) {
    return apiError(404, 'Google does not know this subscription.', 'purchase_unknown', 'invalid_request_error');
  }
  if (facts.state === 'pending') {
    return apiError(409, 'The purchase is not complete yet.', 'purchase_pending', 'invalid_request_error');
  }

  const known = await subscriptionOwner(env, purchaseToken);
  if (known?.state === 'voided') {
    return apiError(403, 'This purchase was refunded.', 'purchase_voided', 'invalid_request_error');
  }
  // A token never seen before that grants nothing — long expired — is not worth an account. A
  // known one is answered whatever its state, so the app can show "expired" or "fix your payment".
  if (!known && facts.state === 'expired') {
    return apiError(403, 'This subscription has ended.', 'purchase_expired', 'invalid_request_error');
  }

  let walletId: string | null = null;
  let isNewWallet = false;

  if (known && known.walletStatus !== 'deleted') {
    walletId = known.walletId;
  } else if (!known && facts.linkedPurchaseToken) {
    const linked = await subscriptionOwner(env, facts.linkedPurchaseToken);
    if (linked && linked.walletStatus !== 'deleted') walletId = linked.walletId;
  }
  if (!walletId && session) walletId = session.walletId;

  const owner = walletId
    ? await env.DB.prepare('SELECT status FROM wallets WHERE id = ?').bind(walletId).first<{ status: string }>()
    : null;
  if (owner?.status === 'blocked') {
    return apiError(403, 'This account is blocked.', 'wallet_blocked', 'invalid_request_error');
  }

  let issued: { token: string; recoveryCode?: string } | null = null;
  if (!walletId) {
    const created = await createWallet(env, 0, payload.label, facts.isTest ? 'license_tester' : null);
    walletId = created.walletId;
    isNewWallet = true;
    issued = { token: created.token, recoveryCode: created.recoveryCode };
  } else if (session?.walletId !== walletId) {
    // The restore path: this device has no token for the account the subscription is on.
    const device = await issueDeviceToken(env, walletId, payload.label, ctx);
    if ('refusal' in device) return device.refusal;
    issued = { token: device.token };
  }

  if (known && known.walletStatus === 'deleted') {
    // The account was deleted while Play went on billing. The subscription follows the person,
    // so it moves to the new account rather than keeping a dead one entitled.
    await env.DB.prepare('UPDATE subscriptions SET wallet_id = ?, updated_at = ? WHERE purchase_token = ?')
      .bind(walletId, now, purchaseToken).run();
  }

  await upsertSubscription(env, purchaseToken, walletId, facts, now);

  // Within three days or Google refunds it. Awaited rather than left to run behind the response:
  // it is one request, and the reconcile only catches a failure up to an hour later.
  if (!facts.acknowledged) await acknowledge(env, purchaseToken, facts.productId);

  await markBuyer(env, walletId, facts.isTest, facts.obfuscatedAccountId, isNewWallet);
  if (facts.linkedPurchaseToken) {
    const previous = await subscriptionOwner(env, facts.linkedPurchaseToken);
    if (previous && previous.walletId !== walletId) await refreshWalletEntitlement(env, previous.walletId, now);
  }

  const entitlement = await buildEntitlement(env, walletId, limitsFrom(env), now);
  return json({
    wallet_id: walletId,
    ...(issued ? { token: issued.token } : {}),
    ...(issued?.recoveryCode ? { recovery_code: issued.recoveryCode } : {}),
    entitlement,
  });
}

/**
 * The same bookkeeping the credit packs did: a licence tester's account stays out of the money
 * figures, a real purchase on a tester's account clears the mark, and the hashed wallet id the app
 * attached is kept so a refund history survives a reinstall.
 */
async function markBuyer(
  env: Env,
  walletId: string,
  isTest: boolean,
  obfuscatedAccountId: string | null,
  isNewWallet: boolean,
): Promise<void> {
  const statements: D1PreparedStatement[] = [];
  if (isTest && !isNewWallet) {
    statements.push(
      env.DB.prepare("UPDATE wallets SET is_test = 1, test_reason = COALESCE(test_reason, 'license_tester') WHERE id = ?")
        .bind(walletId),
    );
  }
  if (!isTest) {
    statements.push(
      env.DB.prepare("UPDATE wallets SET is_test = 0, test_reason = NULL WHERE id = ? AND test_reason = 'license_tester'")
        .bind(walletId),
    );
  }
  if (obfuscatedAccountId) {
    statements.push(
      env.DB.prepare('UPDATE wallets SET play_account_hash = ? WHERE id = ?')
        .bind(await sha256(obfuscatedAccountId), walletId),
    );
  }
  if (statements.length) await env.DB.batch(statements);
}

/**
 * `GET /v1/entitlement` — what this account may do today (billing spec, section 6).
 *
 * Recomputed from the ledger on every read rather than served from the wallet's copy alone, so
 * opening the subscription screen also repairs a copy that missed a write. The 401 says which kind
 * of "no", like `GET /v1/wallet` does: a signed-out device keeps its recovery code, a deleted
 * account does not.
 */
export async function handleEntitlement(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const session = await authenticate(request, env);
  if (!session) {
    return apiError(401, 'No valid credit token.', await authFailure(request, env), 'invalid_request_error');
  }
  touch(env, session, ctx);
  return json(await buildEntitlement(env, session.walletId, limitsFrom(env), Date.now()));
}
