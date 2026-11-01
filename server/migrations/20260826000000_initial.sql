-- Qasa initial schema (UUID v7 IDs assigned in application code)

CREATE TABLE accounts (
  id                  UUID PRIMARY KEY,
  public_id           TEXT NOT NULL UNIQUE,
  tier                TEXT NOT NULL DEFAULT 'free',
  recovery_email_hash BYTEA,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE devices (
  id              UUID PRIMARY KEY,
  account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  fingerprint     TEXT NOT NULL,
  device_name     TEXT NOT NULL,
  platform        TEXT NOT NULL,
  license_jti     TEXT,
  last_seen_at    TIMESTAMPTZ,
  revoked_at      TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (account_id, fingerprint)
);

CREATE TABLE subscriptions (
  id           UUID PRIMARY KEY,
  account_id   UUID NOT NULL REFERENCES accounts(id),
  status       TEXT NOT NULL,
  plan_id      TEXT NOT NULL DEFAULT 'premium_monthly',
  amount_iqd   INTEGER NOT NULL DEFAULT 10000,
  started_at   TIMESTAMPTZ NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
  id                UUID PRIMARY KEY,
  account_id        UUID NOT NULL REFERENCES accounts(id),
  subscription_id   UUID REFERENCES subscriptions(id),
  gateway           TEXT NOT NULL,
  gateway_order_id  TEXT UNIQUE,
  amount_iqd        INTEGER NOT NULL,
  status            TEXT NOT NULL DEFAULT 'pending',
  deep_link         TEXT,
  idempotency_key   TEXT UNIQUE,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at      TIMESTAMPTZ
);

CREATE TABLE webhook_events (
  id               UUID PRIMARY KEY,
  gateway          TEXT NOT NULL,
  gateway_order_id TEXT,
  payload          JSONB NOT NULL,
  signature_valid  BOOLEAN NOT NULL,
  processed        BOOLEAN NOT NULL DEFAULT false,
  error_message    TEXT,
  received_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE license_revocations (
  jti         TEXT PRIMARY KEY,
  account_id  UUID NOT NULL REFERENCES accounts(id),
  reason      TEXT,
  revoked_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE entitlement_overrides (
  id            UUID PRIMARY KEY,
  account_id    UUID NOT NULL REFERENCES accounts(id),
  override_type TEXT NOT NULL,
  payload       JSONB NOT NULL,
  expires_at    TIMESTAMPTZ,
  created_by    UUID,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_users (
  id            UUID PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  role          TEXT NOT NULL DEFAULT 'admin',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_audit_log (
  id          UUID PRIMARY KEY,
  admin_id    UUID NOT NULL REFERENCES admin_users(id),
  action      TEXT NOT NULL,
  target_type TEXT NOT NULL,
  target_id   TEXT NOT NULL,
  payload     JSONB,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vpn_nodes (
  id               UUID PRIMARY KEY,
  name             TEXT NOT NULL,
  city             TEXT NOT NULL,
  public_ip        INET NOT NULL,
  wg_public_key    TEXT NOT NULL,
  endpoint_port    INTEGER NOT NULL DEFAULT 51820,
  max_peers        INTEGER NOT NULL DEFAULT 1000,
  active_peers     INTEGER NOT NULL DEFAULT 0,
  status           TEXT NOT NULL DEFAULT 'online',
  agent_token_hash TEXT NOT NULL,
  last_heartbeat   TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vpn_sessions (
  id                 UUID PRIMARY KEY,
  account_id         UUID NOT NULL REFERENCES accounts(id),
  device_id          UUID NOT NULL REFERENCES devices(id),
  node_id            UUID NOT NULL REFERENCES vpn_nodes(id),
  client_public_key  TEXT NOT NULL,
  assigned_ip        INET NOT NULL,
  connected_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  disconnected_at    TIMESTAMPTZ,
  bytes_tx           BIGINT NOT NULL DEFAULT 0,
  bytes_rx           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE breach_hashes (
  prefix CHAR(5) NOT NULL,
  suffix TEXT NOT NULL,
  source TEXT NOT NULL DEFAULT 'local',
  PRIMARY KEY (prefix, suffix)
);

CREATE INDEX idx_devices_account_active ON devices (account_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_subscriptions_account_status ON subscriptions (account_id, status);
CREATE INDEX idx_subscriptions_expires ON subscriptions (expires_at) WHERE status = 'active';
CREATE INDEX idx_payments_status_created ON payments (status, created_at DESC);
CREATE INDEX idx_payments_gateway ON payments (gateway, status);
CREATE INDEX idx_webhook_events_gateway_time ON webhook_events (gateway, received_at DESC);
CREATE INDEX idx_vpn_nodes_status ON vpn_nodes (status) WHERE status = 'online';
CREATE INDEX idx_accounts_public_id ON accounts (public_id text_pattern_ops);
CREATE INDEX idx_breach_hashes_prefix ON breach_hashes (prefix);

CREATE MATERIALIZED VIEW mv_revenue_daily AS
  SELECT date_trunc('day', completed_at) AS day,
         gateway,
         SUM(amount_iqd) AS total_iqd,
         COUNT(*) AS tx_count
  FROM payments
  WHERE status = 'success'
  GROUP BY 1, 2;

CREATE MATERIALIZED VIEW mv_active_subscribers AS
  SELECT COUNT(DISTINCT account_id) AS paid_users
  FROM subscriptions
  WHERE status = 'active' AND expires_at > now();

CREATE UNIQUE INDEX mv_active_subscribers_unique ON mv_active_subscribers (paid_users);
