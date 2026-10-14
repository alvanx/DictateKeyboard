/**
 * Runs the Worker under Node with everything it talks to replaced by something local:
 *
 *  - **D1** by an in-memory SQLite loaded from `schema.sql`, so the real SQL runs.
 *  - **Durable Objects** by the real `Wallet` and `GlobalGuard` classes over a Map-backed storage.
 *  - **Google** by a fake answering the OAuth, `subscriptionsv2`, acknowledge and voided-purchase
 *    endpoints from a table the test fills in. The service-account JWT is really signed.
 *  - **Groq** and **Workers AI** by one handler the test sets. Groq's HTTP calls are routed to it
 *    the same way the binding is, so a test reads the same whichever provider is configured.
 *
 * Only the parts that cannot run outside Cloudflare are faked; every route, rule and query is the
 * one that ships.
 */
import { DatabaseSync } from 'node:sqlite';
import schemaSql from '../schema.sql';
import worker from '../src/index';
import { GlobalGuard } from '../src/guard';
import { Wallet } from '../src/wallet';
import type { GoogleSubscription } from '../src/entitlement';

type Row = Record<string, unknown>;

function norm(value: unknown): unknown {
  if (value === undefined) return null;
  if (typeof value === 'boolean') return value ? 1 : 0;
  return value;
}

class FakeStatement {
  constructor(private db: DatabaseSync, readonly sql: string, private args: unknown[] = []) {}
  bind(...args: unknown[]): FakeStatement {
    return new FakeStatement(this.db, this.sql, args);
  }
  private params(): never[] {
    return this.args.map(norm) as never[];
  }
  async first<T = Row>(column?: string): Promise<T | null> {
    const row = this.db.prepare(this.sql).get(...this.params()) as Row | undefined;
    if (!row) return null;
    return (column ? row[column] : { ...row }) as T;
  }
  async all<T = Row>(): Promise<{ results: T[]; success: true; meta: object }> {
    return { results: this.allSync() as T[], success: true, meta: {} };
  }
  async run(): Promise<{ success: true; meta: { changes: number; last_row_id: number } }> {
    return this.runSync();
  }
  allSync(): Row[] {
    return (this.db.prepare(this.sql).all(...this.params()) as Row[]).map((r) => ({ ...r }));
  }
  runSync(): { success: true; meta: { changes: number; last_row_id: number } } {
    const r = this.db.prepare(this.sql).run(...this.params());
    return { success: true, meta: { changes: Number(r.changes), last_row_id: Number(r.lastInsertRowid) } };
  }
}

class FakeD1 {
  constructor(readonly db: DatabaseSync) {}
  prepare(sql: string): FakeStatement {
    return new FakeStatement(this.db, sql);
  }
  async batch(statements: FakeStatement[]): Promise<Array<{ results: Row[]; success: true; meta: object }>> {
    this.db.exec('BEGIN');
    try {
      const out = statements.map((s) =>
        /^\s*(SELECT|WITH)/i.test(s.sql)
          ? { results: s.allSync(), success: true as const, meta: {} }
          : { results: [], ...s.runSync() });
      this.db.exec('COMMIT');
      return out;
    } catch (error) {
      this.db.exec('ROLLBACK');
      throw error;
    }
  }
  async exec(sql: string): Promise<void> {
    this.db.exec(sql);
  }
}

class FakeStorage {
  private map = new Map<string, unknown>();
  async get<T>(key: string): Promise<T | undefined> {
    const v = this.map.get(key);
    return v === undefined ? undefined : structuredClone(v as T);
  }
  async put(key: string, value: unknown): Promise<void> {
    this.map.set(key, structuredClone(value));
  }
  async delete(key: string): Promise<boolean> {
    return this.map.delete(key);
  }
  async deleteAll(): Promise<void> {
    this.map.clear();
  }
}

class FakeNamespace<T> {
  readonly instances = new Map<string, T>();
  constructor(private make: (ctx: unknown) => T) {}
  jurisdiction(): this {
    return this;
  }
  getByName(name: string): T {
    let instance = this.instances.get(name);
    if (!instance) {
      instance = this.make({ storage: new FakeStorage(), id: { name, toString: () => name } });
      this.instances.set(name, instance);
    }
    return instance;
  }
}

/** What the fake Google knows, and what it was asked to do. */
export interface FakeGoogle {
  subscriptions: Map<string, GoogleSubscription>;
  voided: Array<{ purchaseToken: string }>;
  acknowledged: string[];
  fetched: string[];
  /** While true every Play Developer API call answers 503. */
  down: boolean;
}

export interface Harness {
  env: Record<string, unknown> & { DB: FakeD1 };
  google: FakeGoogle;
  /** Calls the model saw. Set [ai.handler] to answer them. */
  ai: { calls: Array<{ model: string; input: Record<string, unknown> }>; handler: (model: string, input: Record<string, unknown>) => Promise<unknown> };
  wallets: FakeNamespace<Wallet>;
  call(method: string, path: string, opts?: { token?: string; json?: unknown; body?: BodyInit; headers?: Record<string, string> }): Promise<{ status: number; body: any; headers: Headers }>;
  cron(expression: string): Promise<void>;
  flush(): Promise<void>;
  sql<T = Row>(query: string, ...args: unknown[]): T[];
}

async function serviceAccount(): Promise<string> {
  const pair = await crypto.subtle.generateKey(
    { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' },
    true,
    ['sign', 'verify'],
  );
  const pkcs8 = new Uint8Array(await crypto.subtle.exportKey('pkcs8', pair.privateKey));
  const b64 = Buffer.from(pkcs8).toString('base64').replace(/(.{64})/g, '$1\n');
  return JSON.stringify({
    client_email: 'test@example.iam.gserviceaccount.com',
    private_key: `-----BEGIN PRIVATE KEY-----\n${b64}\n-----END PRIVATE KEY-----\n`,
  });
}

let cachedAccount: string | null = null;

export async function makeHarness(vars: Record<string, string> = {}): Promise<Harness> {
  const db = new DatabaseSync(':memory:');
  db.exec(schemaSql);
  const DB = new FakeD1(db);

  const google: FakeGoogle = { subscriptions: new Map(), voided: [], acknowledged: [], fetched: [], down: false };
  const ai: Harness['ai'] = {
    calls: [],
    handler: async () => {
      throw new Error('no model handler set');
    },
  };

  cachedAccount ??= await serviceAccount();
  const env: Harness['env'] = {
    DB,
    AI: {
      run: (model: string, input: Record<string, unknown>) => {
        ai.calls.push({ model, input });
        return ai.handler(model, input);
      },
    },
    GOOGLE_SERVICE_ACCOUNT: cachedAccount,
    GROQ_API_KEY: 'test-groq',
    PACKAGE_NAME: 'app.dictate.test',
    RTDN_SECRET: 'sekret',
    DAILY_BUDGET_USD: '25',
    ...vars,
  };
  const wallets = new FakeNamespace((ctx) => new Wallet(ctx as never, env as never));
  env.WALLET = wallets;
  env.GLOBAL = new FakeNamespace((ctx) => new GlobalGuard(ctx as never, env as never));

  globalThis.fetch = (async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url);
    const method = init?.method ?? 'GET';
    const json = (body: unknown, status = 200) =>
      new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });

    if (url.hostname === 'oauth2.googleapis.com') return json({ access_token: 'test-access', expires_in: 3600 });
    if (url.hostname === 'api.groq.com') return groq(url, init);
    if (url.hostname !== 'androidpublisher.googleapis.com') return new Response('not found', { status: 404 });
    if (google.down) return json({ error: 'unavailable' }, 503);

    const sub = /\/purchases\/subscriptionsv2\/tokens\/([^/]+)$/.exec(url.pathname);
    if (sub && method === 'GET') {
      const token = decodeURIComponent(sub[1]!);
      google.fetched.push(token);
      const found = google.subscriptions.get(token);
      return found ? json(found) : json({ error: 'not found' }, 404);
    }
    const ack = /\/purchases\/subscriptions\/([^/]+)\/tokens\/([^/]+):acknowledge$/.exec(url.pathname);
    if (ack && method === 'POST') {
      const token = decodeURIComponent(ack[2]!);
      google.acknowledged.push(token);
      const found = google.subscriptions.get(token);
      if (found) found.acknowledgementState = 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED';
      return json({});
    }
    if (url.pathname.endsWith('/purchases/voidedpurchases')) return json({ voidedPurchases: google.voided });
    return json({ error: 'not found' }, 404);
  }) as typeof fetch;

  /** Groq's two endpoints, answered by the same handler as the binding, in Groq's own shapes. */
  async function groq(url: URL, init?: RequestInit): Promise<Response> {
    const reply = (body: unknown, status = 200) =>
      new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } });
    if (new Headers(init?.headers).get('authorization') !== 'Bearer test-groq') return reply({ error: 'bad key' }, 401);
    try {
      if (url.pathname.endsWith('/audio/transcriptions')) {
        const form = init?.body as FormData;
        const file = form.get('file');
        const input: Record<string, unknown> = {
          audio: file instanceof Blob ? file.size : 0,
          language: form.get('language') ?? undefined,
          prompt: form.get('prompt') ?? undefined,
          response_format: form.get('response_format'),
        };
        const model = String(form.get('model'));
        ai.calls.push({ model, input });
        const out = (await ai.handler(model, input)) as { text?: string; duration?: number; transcription_info?: { duration?: number } };
        return reply({ text: out.text, duration: out.duration ?? out.transcription_info?.duration });
      }
      if (url.pathname.endsWith('/chat/completions')) {
        const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
        ai.calls.push({ model: String(body.model), input: body });
        return reply(await ai.handler(String(body.model), body));
      }
    } catch {
      return reply({ error: 'model failed' }, 500);
    }
    return reply({ error: 'not found' }, 404);
  }

  let pending: Promise<unknown>[] = [];
  const ctx = {
    waitUntil(p: Promise<unknown>) {
      pending.push(Promise.resolve(p).catch((e) => console.log(`waitUntil failed: ${e}`)));
    },
    passThroughOnException() {},
    props: {},
  };
  const flush = async () => {
    while (pending.length) {
      const batch = pending;
      pending = [];
      await Promise.all(batch);
    }
  };

  return {
    env,
    google,
    ai,
    wallets,
    async call(method, path, opts = {}) {
      const headers = new Headers(opts.headers);
      if (opts.token) headers.set('authorization', `Bearer ${opts.token}`);
      let body = opts.body;
      if (opts.json !== undefined) {
        body = JSON.stringify(opts.json);
        headers.set('content-type', 'application/json');
      }
      const response = await worker.fetch(
        new Request(`https://cloud.test${path}`, { method, headers, body }) as never,
        env as never,
        ctx as never,
      );
      await flush();
      const text = await response.text();
      let parsed: unknown = text;
      try {
        parsed = JSON.parse(text);
      } catch {
        // not JSON
      }
      return { status: response.status, body: parsed as any, headers: response.headers };
    },
    async cron(expression) {
      await worker.scheduled!({ cron: expression, scheduledTime: Date.now(), noRetry() {} } as never, env as never, ctx as never);
      await flush();
    },
    flush,
    sql(query, ...args) {
      return db.prepare(query).all(...(args.map(norm) as never[])) as never;
    },
  };
}

const DAY = 86_400_000;

/** A Google subscription resource, as `subscriptionsv2.get` would return it. */
export function googleSub(opts: {
  state?: string;
  expiresIn?: number;
  startedAgo?: number;
  basePlan?: string;
  offerId?: string;
  autoRenew?: boolean;
  linked?: string;
  test?: boolean;
  acknowledged?: boolean;
  accountId?: string;
  now?: number;
} = {}): GoogleSubscription {
  const now = opts.now ?? Date.now();
  return {
    subscriptionState: opts.state ?? 'SUBSCRIPTION_STATE_ACTIVE',
    startTime: new Date(now - (opts.startedAgo ?? DAY)).toISOString(),
    regionCode: 'DE',
    linkedPurchaseToken: opts.linked,
    acknowledgementState: opts.acknowledged ? 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED' : 'ACKNOWLEDGEMENT_STATE_PENDING',
    ...(opts.test ? { testPurchase: {} } : {}),
    ...(opts.accountId ? { externalAccountIdentifiers: { obfuscatedExternalAccountId: opts.accountId } } : {}),
    lineItems: [{
      productId: 'plus',
      expiryTime: new Date(now + (opts.expiresIn ?? 30 * DAY)).toISOString(),
      latestSuccessfulOrderId: 'GPA.0000-0000-0000-00000',
      autoRenewingPlan: { autoRenewEnabled: opts.autoRenew ?? true },
      offerDetails: { basePlanId: opts.basePlan ?? 'monthly', ...(opts.offerId ? { offerId: opts.offerId } : {}) },
    }],
  };
}

/** A WAV file of [seconds] of silence: 16 kHz, mono, 16-bit — the length the server reads from its header. */
export function wav(seconds: number): Blob {
  const rate = 16_000;
  const dataBytes = rate * 2 * seconds;
  const buf = new ArrayBuffer(44 + dataBytes);
  const v = new DataView(buf);
  const str = (o: number, s: string) => [...s].forEach((c, i) => v.setUint8(o + i, c.charCodeAt(0)));
  str(0, 'RIFF'); v.setUint32(4, 36 + dataBytes, true); str(8, 'WAVE');
  str(12, 'fmt '); v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
  v.setUint32(24, rate, true); v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
  str(36, 'data'); v.setUint32(40, dataBytes, true);
  return new Blob([buf], { type: 'audio/wav' });
}

export function transcribeForm(seconds: number): FormData {
  const form = new FormData();
  form.set('file', new File([wav(seconds)], 'a.wav', { type: 'audio/wav' }));
  form.set('model', 'whisper-1');
  return form;
}

/** A Pub/Sub push envelope carrying [notification]. */
export function rtdn(notification: unknown): unknown {
  return { message: { data: Buffer.from(JSON.stringify({ packageName: 'app.dictate.test', ...(notification as object) })).toString('base64') } };
}

export { DAY };
