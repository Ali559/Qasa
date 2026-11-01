-- Admin roles: `admin` may change things, `viewer` may only read. Checked against the
-- database on every request, so a role change or a disabled admin applies at once.
ALTER TABLE admin_users
  ADD COLUMN IF NOT EXISTS disabled_at TIMESTAMPTZ;
ALTER TABLE admin_users
  DROP CONSTRAINT IF EXISTS admin_users_role_check;
ALTER TABLE admin_users
  ADD CONSTRAINT admin_users_role_check CHECK (role IN ('admin', 'viewer'));

-- Admin logout: a revoked token id stays here until the token would have expired.
CREATE TABLE IF NOT EXISTS admin_token_revocations (
  jti        TEXT PRIMARY KEY,
  admin_id   UUID NOT NULL REFERENCES admin_users(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Staff can block an account (abuse, chargebacks). Blocked accounts' tokens stop
-- working and they cannot register devices.
ALTER TABLE accounts
  ADD COLUMN IF NOT EXISTS blocked_at     TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS blocked_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_accounts_live_created
  ON accounts (created_at DESC)
  WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created
  ON admin_audit_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_events_order
  ON webhook_events (gateway_order_id);
