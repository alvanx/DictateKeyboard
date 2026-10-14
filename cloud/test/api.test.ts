import { test, mock } from 'node:test';
import assert from 'node:assert/strict';
import { DAY, googleSub, makeHarness, rtdn, transcribeForm, type Harness } from './harness';

const HOURLY = '23 * * * *';
const NIGHTLY = '17 3 * * *';

/** A transcription model that answers [seconds] of audio. */
function whisper(h: Harness): void {
  h.ai.handler = async (_model, input) => {
    if ('audio' in input) return { text: 'hello world', transcription_info: { duration: 2 }, usage: { neurons: 1.5 } };
    throw new Error('unexpected chat call');
  };
}

/** Subscribes through the verify route and returns the device token. */
async function subscribe(h: Harness, token = 'tok-1', sub = googleSub()): Promise<{ wallet: string; device: string; body: any }> {
  h.google.subscriptions.set(token, sub);
  const res = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: token, productId: 'plus', label: 'Pixel' } });
  assert.equal(res.status, 200, JSON.stringify(res.body));
  return { wallet: res.body.wallet_id, device: res.body.token, body: res.body };
}

// --- POST /v1/subscription/verify -------------------------------------------------------------

test('verify: a first purchase creates an account, acknowledges it and grants Plus', async () => {
  const h = await makeHarness();
  const { body } = await subscribe(h, 'tok-1', googleSub({ offerId: 'trial7', startedAgo: DAY, expiresIn: 6 * DAY }));
  assert.match(body.token, /^dk_/);
  assert.match(body.recovery_code, /^DICT-/);
  assert.equal(body.entitlement.plan, 'plus');
  assert.equal(body.entitlement.state, 'trial');
  assert.deepEqual(body.entitlement.features, ['managed_transcription', 'managed_cleanup', 'dictionary_sync']);
  assert.equal(body.entitlement.daily.allowance_seconds, 5400);
  assert.deepEqual(h.google.acknowledged, ['tok-1']);
  const [row] = h.sql<{ state: string; acknowledged: number; offer_id: string }>('SELECT * FROM subscriptions');
  assert.equal(row!.state, 'trial');
  assert.equal(row!.acknowledged, 1);
  assert.equal(row!.offer_id, 'trial7');
});

test('verify is idempotent: the same token returns the same account and no new credentials', async () => {
  const h = await makeHarness();
  const first = await subscribe(h);
  const again = await h.call('POST', '/v1/subscription/verify', { token: first.device, json: { purchaseToken: 'tok-1', productId: 'plus' } });
  assert.equal(again.status, 200);
  assert.equal(again.body.wallet_id, first.wallet);
  assert.equal(again.body.token, undefined);
  assert.equal(again.body.recovery_code, undefined);
  assert.equal(h.sql('SELECT * FROM subscriptions').length, 1);
  assert.equal(h.sql('SELECT * FROM wallets').length, 1);
});

test('verify after a reinstall restores the account with a new device token, within the device limit', async () => {
  const h = await makeHarness({ MAX_DEVICES: '2' });
  const first = await subscribe(h);
  const reinstall = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'tok-1', productId: 'plus' } });
  assert.equal(reinstall.status, 200);
  assert.equal(reinstall.body.wallet_id, first.wallet);
  assert.match(reinstall.body.token, /^dk_/);
  assert.notEqual(reinstall.body.token, first.device);
  assert.equal(reinstall.body.recovery_code, undefined);
  const third = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'tok-1', productId: 'plus' } });
  assert.equal(third.status, 409);
  assert.equal(third.body.error.code, 'device_limit');
  assert.equal(third.body.devices.length, 2);
});

test('verify refuses what Google refuses, with the codes the app knows', async () => {
  const h = await makeHarness();
  assert.equal((await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'nope', productId: 'plus' } })).body.error.code, 'purchase_unknown');
  h.google.subscriptions.set('pend', googleSub({ state: 'SUBSCRIPTION_STATE_PENDING' }));
  const pending = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'pend', productId: 'plus' } });
  assert.equal(pending.status, 409);
  assert.equal(pending.body.error.code, 'purchase_pending');
  assert.equal((await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'x', productId: 'credits_pro' } })).body.error.code, 'unknown_product');
  h.google.down = true;
  const down = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'pend', productId: 'plus' } });
  assert.equal(down.status, 503);
  assert.equal(down.body.error.code, 'google_unavailable');
  assert.equal(h.sql('SELECT * FROM wallets').length, 0);
});

test('verify does not hand out an account because the purchase names its id', async () => {
  const h = await makeHarness();
  const victim = await subscribe(h, 'tok-victim');
  // Someone who learned the wallet id buys a subscription with it attached.
  h.google.subscriptions.set('tok-attacker', googleSub({ accountId: victim.wallet }));
  const res = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'tok-attacker', productId: 'plus', walletId: victim.wallet } });
  assert.equal(res.status, 200);
  assert.notEqual(res.body.wallet_id, victim.wallet);
});

test('monthly to yearly: one wallet, one active row', async () => {
  const h = await makeHarness();
  const first = await subscribe(h, 'tok-monthly');
  h.google.subscriptions.set('tok-yearly', googleSub({ basePlan: 'yearly', linked: 'tok-monthly', expiresIn: 365 * DAY }));
  const up = await h.call('POST', '/v1/subscription/verify', { token: first.device, json: { purchaseToken: 'tok-yearly', productId: 'plus' } });
  assert.equal(up.body.wallet_id, first.wallet);
  const rows = h.sql<{ purchase_token: string; state: string; wallet_id: string }>('SELECT * FROM subscriptions ORDER BY purchase_token');
  assert.deepEqual(rows.map((r) => [r.purchase_token, r.state, r.wallet_id === first.wallet]), [
    ['tok-monthly', 'replaced', true],
    ['tok-yearly', 'active', true],
  ]);
  // The old token's later notification cannot bring it back.
  await h.call('POST', '/v1/rtdn?key=sekret', { json: rtdn({ subscriptionNotification: { notificationType: 2, purchaseToken: 'tok-monthly' } }) });
  assert.equal(h.sql<{ state: string }>("SELECT state FROM subscriptions WHERE purchase_token = 'tok-monthly'")[0]!.state, 'replaced');
});

test('a voided purchase cannot be verified again', async () => {
  const h = await makeHarness();
  await subscribe(h);
  await h.call('POST', '/v1/rtdn?key=sekret', { json: rtdn({ voidedPurchaseNotification: { purchaseToken: 'tok-1', productType: 1 } }) });
  const res = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'tok-1', productId: 'plus' } });
  assert.equal(res.status, 403);
  assert.equal(res.body.error.code, 'purchase_voided');
});

// --- GET /v1/entitlement ----------------------------------------------------------------------

test('entitlement: free without a subscription, 401 for a bad token', async () => {
  const h = await makeHarness();
  assert.equal((await h.call('GET', '/v1/entitlement', { token: 'dk_nothing' })).status, 401);
  const { device } = await subscribe(h, 'tok-1', googleSub({ state: 'SUBSCRIPTION_STATE_ON_HOLD', expiresIn: -DAY }));
  const res = await h.call('GET', '/v1/entitlement', { token: device });
  assert.equal(res.body.plan, 'free');
  assert.equal(res.body.state, 'on_hold');
});

// --- The gate on the paid routes ------------------------------------------------------------------

test('no entitlement: 402 not_entitled before any model call', async () => {
  const h = await makeHarness();
  whisper(h);
  const { device } = await subscribe(h, 'tok-1', googleSub({ state: 'SUBSCRIPTION_STATE_ON_HOLD', expiresIn: -DAY }));
  const res = await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) });
  assert.equal(res.status, 402);
  assert.deepEqual(res.body, {
    error: { message: 'Dictate Cloud needs an active Plus subscription.', type: 'insufficient_quota', code: 'not_entitled' },
  });
  const chat = await h.call('POST', '/v1/chat/completions', { token: device, json: { messages: [{ role: 'user', content: 'hi' }] } });
  assert.equal(chat.status, 402);
  const cleanup = await h.call('POST', '/v1/cleanup', { token: device, json: { text: 'first a second b third c' } });
  assert.equal(cleanup.status, 402);
  assert.equal(h.ai.calls.length, 0);
});

test('the daily allowance: used up is 429 daily_limit with the reset time, and midnight UTC resets it', async () => {
  mock.timers.enable({ apis: ['Date'], now: Date.parse('2026-10-14T23:50:00Z') });
  try {
    const h = await makeHarness({ DAILY_ALLOWANCE_SECONDS: '3' });
    whisper(h);
    const { device } = await subscribe(h);
    const ok = await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) });
    assert.equal(ok.status, 200);
    assert.equal(ok.headers.get('x-dictate-daily-used'), '2');
    assert.equal(ok.headers.get('x-dictate-daily-allowance'), '3');
    // One second left: the next request still goes through, and uses the day up.
    assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 200);
    const refused = await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) });
    assert.equal(refused.status, 429);
    assert.equal(refused.body.error.code, 'daily_limit');
    assert.equal(refused.body.resets_at, Date.parse('2026-10-15T00:00:00Z'));
    assert.equal(refused.headers.get('retry-after'), '600');

    mock.timers.setTime(Date.parse('2026-10-15T00:00:05Z'));
    assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 200);
    const ent = await h.call('GET', '/v1/entitlement', { token: device });
    assert.equal(ent.body.daily.used_seconds, 2);
  } finally {
    mock.timers.reset();
  }
});

test('a failed transcription gives the seconds back to the day', async () => {
  const h = await makeHarness();
  h.ai.handler = async () => { throw new Error('model down'); };
  const { device } = await subscribe(h);
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(5) })).status, 502);
  assert.equal((await h.call('GET', '/v1/entitlement', { token: device })).body.daily.used_seconds, 0);
});

test('credit bought before Plus stays spendable without a subscription', async () => {
  const h = await makeHarness();
  whisper(h);
  const { createWallet } = await import('../src/routes/wallet');
  const legacy = await createWallet(h.env as never, 60);
  const res = await h.call('POST', '/v1/audio/transcriptions', { token: legacy.token, body: transcribeForm(2) });
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('x-dictate-seconds-left'), '58');
  assert.equal(res.headers.get('x-dictate-daily-used'), null);
});

// --- Notifications, reconcile, recheck --------------------------------------------------------

test('notifications: the state is re-read from Google, so duplicates and disorder change nothing', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  // An "expired" notification arrives late, after Google has already renewed it.
  h.google.subscriptions.set('tok-1', googleSub({ expiresIn: 60 * DAY, acknowledged: true }));
  const note = rtdn({ subscriptionNotification: { notificationType: 13, purchaseToken: 'tok-1' } });
  assert.equal((await h.call('POST', '/v1/rtdn?key=sekret', { json: note })).status, 200);
  assert.equal((await h.call('POST', '/v1/rtdn?key=sekret', { json: note })).status, 200);
  const ent = await h.call('GET', '/v1/entitlement', { token: device });
  assert.equal(ent.body.plan, 'plus');
  assert.ok(ent.body.expires_at > Date.now() + 59 * DAY);
  assert.equal(h.sql('SELECT * FROM subscriptions').length, 1);
});

test('notifications: Google unreachable is a 503, so Pub/Sub delivers again', async () => {
  const h = await makeHarness();
  await subscribe(h);
  h.google.down = true;
  const res = await h.call('POST', '/v1/rtdn?key=sekret', { json: rtdn({ subscriptionNotification: { notificationType: 2, purchaseToken: 'tok-1' } }) });
  assert.equal(res.status, 503);
});

test('a refund ends access at once', async () => {
  const h = await makeHarness();
  whisper(h);
  const { device } = await subscribe(h);
  await h.call('POST', '/v1/rtdn?key=sekret', { json: rtdn({ voidedPurchaseNotification: { purchaseToken: 'tok-1', productType: 1 } }) });
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 402);
  assert.equal((await h.call('GET', '/v1/entitlement', { token: device })).body.state, 'expired');
});

test('the nightly sweep catches a refund whose notification was lost', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  h.google.voided.push({ purchaseToken: 'tok-1' });
  await h.cron(NIGHTLY);
  assert.equal((await h.call('GET', '/v1/entitlement', { token: device })).body.plan, 'free');
});

test('the hourly reconcile picks up a renewal whose notification was lost', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h, 'tok-1', googleSub({ expiresIn: 2 * 3_600_000 }));
  h.google.subscriptions.set('tok-1', googleSub({ expiresIn: 30 * DAY, acknowledged: true }));
  h.google.fetched.length = 0;
  await h.cron(HOURLY);
  assert.deepEqual(h.google.fetched, ['tok-1']);
  assert.ok((await h.call('GET', '/v1/entitlement', { token: device })).body.expires_at > Date.now() + 29 * DAY);
});

test('a refused request rechecks with Google before saying "not entitled"', async () => {
  const h = await makeHarness();
  whisper(h);
  const { device, wallet } = await subscribe(h);
  // The renewal happened, nobody told us, and the wallet's copy has run out.
  await h.wallets.getByName(wallet).setEntitlement(Date.now() - 1000);
  h.sql("UPDATE subscriptions SET expires_at = ?, updated_at = ?", Date.now() - 1000, Date.now() - DAY);
  h.google.subscriptions.set('tok-1', googleSub({ expiresIn: 30 * DAY, acknowledged: true }));
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 200);
});

test('rule 6: Google down at the moment of renewal does not lock a paying user out', async () => {
  const h = await makeHarness();
  whisper(h);
  const { device, wallet } = await subscribe(h);
  await h.wallets.getByName(wallet).setEntitlement(Date.now() - 1000);
  h.sql("UPDATE subscriptions SET expires_at = ?, updated_at = ?", Date.now() - 1000, Date.now() - DAY);
  h.google.down = true;
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 200);
});

// --- POST /v1/cleanup -------------------------------------------------------------------------

function chatAnswer(content: string, extra: object = {}) {
  return { choices: [{ message: { content }, finish_reason: 'stop' }], usage: { prompt_tokens: 600, completion_tokens: 20, neurons: 6 }, ...extra };
}

test('cleanup: a spoken list becomes a numbered list, charged at its real cost', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  h.ai.handler = async () => chatAnswer('For the trip we need:\n1. Passports\n2. Chargers\n3. Tickets');
  const res = await h.call('POST', '/v1/cleanup', {
    token: device,
    json: { text: 'um so for the trip we need first passports second chargers and third the tickets', locale: 'en-US', context: 'notes' },
  });
  assert.equal(res.status, 200);
  assert.equal(res.body.formatted_as, 'list');
  assert.equal(res.body.items, 3);
  assert.equal(res.body.text, 'For the trip we need:\n1. Passports\n2. Chargers\n3. Tickets');
  assert.ok(res.body.usage.seconds_charged >= 1);
  assert.equal(h.ai.calls[0]!.model, 'llama-3.1-8b-instant');
  const [log] = h.sql<{ kind: string; formatted_as: string; status: number }>('SELECT kind, formatted_as, status FROM usage_log');
  assert.deepEqual({ ...log }, { kind: 'cleanup', formatted_as: 'list', status: 200 });
});

test('cleanup: a model that times out returns the original text, uncharged', async () => {
  const h = await makeHarness({ CLEANUP_TIMEOUT_MS: '30' });
  const { device } = await subscribe(h);
  h.ai.handler = () => new Promise(() => {});
  const text = 'um first passports second chargers';
  const res = await h.call('POST', '/v1/cleanup', { token: device, json: { text } });
  assert.equal(res.status, 200);
  assert.deepEqual(res.body, { text, formatted_as: 'plain', usage: { seconds_charged: 0 } });
  assert.equal((await h.call('GET', '/v1/entitlement', { token: device })).body.daily.used_seconds, 0);
});

test('cleanup: an answer that invents content is not used', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  h.ai.handler = async () => chatAnswer('1. Passports\n2. Chargers\n3. Tickets\n4. Sunscreen, a hat and a travel adapter for every room');
  const text = 'first passports second chargers third tickets';
  const res = await h.call('POST', '/v1/cleanup', { token: device, json: { text } });
  assert.equal(res.body.text, text);
  assert.equal(res.body.formatted_as, 'plain');
});

test('cleanup: a list where lists are off is not used', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  h.ai.handler = async () => chatAnswer('- Milk\n- Eggs');
  const res = await h.call('POST', '/v1/cleanup', { token: device, json: { text: 'milk eggs', lists: 'off' } });
  assert.equal(res.body.formatted_as, 'plain');
  assert.equal(res.body.text, 'milk eggs');
});

test('cleanup: 10,000 characters are refused with 413, not truncated', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  const res = await h.call('POST', '/v1/cleanup', { token: device, json: { text: 'word '.repeat(2000) } });
  assert.equal(res.status, 413);
  assert.equal(res.body.error.code, 'input_too_long');
  assert.equal(h.ai.calls.length, 0);
});

test('cleanup: clean text with lists off skips the model and costs nothing', async () => {
  const h = await makeHarness();
  const { device } = await subscribe(h);
  const res = await h.call('POST', '/v1/cleanup', { token: device, json: { text: 'See you at eight.', lists: 'off' } });
  assert.deepEqual(res.body, { text: 'See you at eight.', formatted_as: 'plain', usage: { seconds_charged: 0 } });
  assert.equal(h.ai.calls.length, 0);
});

// --- Deletion ---------------------------------------------------------------------------------

test('deleting with a running subscription warns and links to Play; a later verify moves it to a new account', async () => {
  const h = await makeHarness();
  const { device, wallet } = await subscribe(h);
  const preview = await h.call('POST', '/v1/wallet/delete', { token: device, json: { confirm: false } });
  assert.equal(preview.body.subscription_active, true);
  assert.match(preview.body.manage_subscription_url, /play\.google\.com\/store\/account\/subscriptions\?sku=plus&package=app\.dictate\.test/);
  const done = await h.call('POST', '/v1/wallet/delete', { token: device, json: { confirm: true } });
  assert.equal(done.body.deleted, true);
  const back = await h.call('POST', '/v1/subscription/verify', { json: { purchaseToken: 'tok-1', productId: 'plus' } });
  assert.equal(back.status, 200);
  assert.notEqual(back.body.wallet_id, wallet);
  assert.equal(back.body.entitlement.plan, 'plus');
  assert.match(back.body.recovery_code, /^DICT-/);
});

// --- Providers ----------------------------------------------------------------------------------

test('Groq by default: Whisper Turbo gets the file as it came, and its cost has the ten-second floor', async () => {
  const h = await makeHarness();
  whisper(h);
  const { device } = await subscribe(h);
  const res = await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) });
  assert.equal(res.status, 200);
  assert.deepEqual(res.body, { text: 'hello world' });
  assert.equal(h.ai.calls[0]!.model, 'whisper-large-v3-turbo');
  assert.equal(h.ai.calls[0]!.input.audio, 44 + 2 * 32_000);
  assert.equal(h.ai.calls[0]!.input.response_format, 'verbose_json');
  const [log] = h.sql<{ provider: string; model: string; seconds: number; cost_nano: number }>(
    'SELECT provider, model, seconds, cost_nano FROM usage_log');
  // Two seconds booked against the allowance, ten seconds paid to Groq at $0.04 an hour.
  assert.deepEqual({ ...log }, { provider: 'groq', model: 'whisper-large-v3-turbo', seconds: 2, cost_nano: 111_112 });
  const [day] = h.sql<{ other_cost_nano: number; cost_nano_cf: number }>('SELECT other_cost_nano, cost_nano_cf FROM daily_totals');
  assert.equal(day!.other_cost_nano, 111_112);
  assert.equal(day!.cost_nano_cf, 0);
});

test('Groq refusing or unreachable refunds the seconds', async () => {
  const h = await makeHarness({ GROQ_API_KEY: 'wrong' });
  whisper(h);
  const { device } = await subscribe(h);
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 502);
  assert.equal((await h.call('GET', '/v1/entitlement', { token: device })).body.daily.used_seconds, 0);
});

test('Workers AI is one setting away: the same routes run on the binding', async () => {
  const h = await makeHarness({ AI_PROVIDER: 'workers-ai' });
  const { device } = await subscribe(h);
  h.ai.handler = async (model, input) => 'audio' in input
    ? { text: 'hello', transcription_info: { duration: 2 }, usage: { neurons: 1.5 } }
    : { choices: [{ message: { content: 'Hello.' }, finish_reason: 'stop' }], usage: { prompt_tokens: 10, completion_tokens: 2, neurons: 0.2 } };
  assert.equal((await h.call('POST', '/v1/audio/transcriptions', { token: device, body: transcribeForm(2) })).status, 200);
  assert.equal((await h.call('POST', '/v1/chat/completions', { token: device, json: { messages: [{ role: 'user', content: 'hello' }] } })).status, 200);
  assert.equal(h.ai.calls[0]!.model, '@cf/openai/whisper-large-v3-turbo');
  assert.equal(typeof h.ai.calls[0]!.input.audio, 'string');
  assert.equal(h.ai.calls[1]!.model, '@cf/google/gemma-4-26b-a4b-it');
  assert.equal((h.ai.calls[1]!.input.chat_template_kwargs as { enable_thinking: boolean }).enable_thinking, false);
  const providers = h.sql<{ provider: string }>('SELECT DISTINCT provider FROM usage_log').map((r) => r.provider);
  assert.deepEqual(providers, ['workers-ai']);
});

test('a model name from the other provider falls back to the default instead of failing every request', async () => {
  const { limitsFrom } = await import('../src/config');
  assert.equal(limitsFrom({ CHAT_MODEL: '@cf/google/gemma-4-26b-a4b-it' } as never).chatModel, 'llama-3.1-8b-instant');
  assert.equal(limitsFrom({ AI_PROVIDER: 'workers-ai', CHAT_MODEL: 'llama-3.1-8b-instant' } as never).chatModel, '@cf/google/gemma-4-26b-a4b-it');
  assert.equal(limitsFrom({ CLEANUP_MODEL: 'meta-llama/llama-4-scout-17b-16e-instruct' } as never).cleanupModel, 'meta-llama/llama-4-scout-17b-16e-instruct');
});
