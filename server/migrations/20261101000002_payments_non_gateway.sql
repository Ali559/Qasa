-- Premium granted by staff (support cases, gateway outages) is an ordinary subscription
-- row, so every entitlement check, expiry job and stacking rule applies unchanged. These
-- columns say who granted it and allow exactly one revoke.
ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS granted_by       UUID REFERENCES admin_users(id),
  ADD COLUMN IF NOT EXISTS grant_days       INTEGER,
  ADD COLUMN IF NOT EXISTS grant_reason     TEXT,
  ADD COLUMN IF NOT EXISTS grant_revoked_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_subscriptions_granted
  ON subscriptions (created_at DESC)
  WHERE granted_by IS NOT NULL;

-- Payment history per account, newest first.
CREATE INDEX IF NOT EXISTS idx_payments_account_created
  ON payments (account_id, created_at DESC);
