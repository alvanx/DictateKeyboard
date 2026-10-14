-- Migration 008 — the Plus subscription, and Groq
--
-- Adds the subscription ledger and the grants table (billing spec, section 5), one column on
-- usage_log for the cleanup route, and one on daily_totals for what Groq bills. Nothing is removed: the credit columns on `wallets` and the
-- `purchases` receipts stay, so the dashboard keeps working and seconds bought as packs stay
-- spendable.
--
-- Safe to run before the code that uses it; the code is not safe to run before this.
--
--   npm run db:migrate8            (local)
--   npm run db:migrate8:remote     (production)

CREATE TABLE IF NOT EXISTS subscriptions (
  purchase_token  TEXT PRIMARY KEY,
  wallet_id       TEXT NOT NULL REFERENCES wallets(id),
  source          TEXT NOT NULL DEFAULT 'google_play',
  product_id      TEXT NOT NULL,
  base_plan_id    TEXT NOT NULL,
  offer_id        TEXT,
  state           TEXT NOT NULL,
  expires_at      INTEGER NOT NULL,
  auto_renewing   INTEGER NOT NULL,
  linked_purchase_token TEXT,
  latest_order_id TEXT,
  acknowledged    INTEGER NOT NULL DEFAULT 0,
  is_test         INTEGER NOT NULL DEFAULT 0,
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_subscriptions_wallet ON subscriptions(wallet_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_expiry ON subscriptions(expires_at);

CREATE TABLE IF NOT EXISTS grants (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  wallet_id  TEXT NOT NULL REFERENCES wallets(id),
  kind       TEXT NOT NULL,
  starts_at  INTEGER NOT NULL,
  ends_at    INTEGER NOT NULL,
  note       TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_grants_wallet ON grants(wallet_id);

-- Not idempotent, unlike the rest: SQLite has no ADD COLUMN IF NOT EXISTS. Run the file once.
ALTER TABLE usage_log ADD COLUMN formatted_as TEXT;
ALTER TABLE daily_totals ADD COLUMN other_cost_nano INTEGER NOT NULL DEFAULT 0;
