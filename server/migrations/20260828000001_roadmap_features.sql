-- Roadmap: pricing, vault flag, push locale, notifications, VPN quota

CREATE TABLE IF NOT EXISTS plans (
  id              TEXT PRIMARY KEY,
  title           TEXT NOT NULL,
  interval        TEXT NOT NULL CHECK (interval IN ('monthly', 'yearly')),
  amount_iqd      INTEGER NOT NULL,
  days            INTEGER NOT NULL,
  vpn_quota_gb    INTEGER NOT NULL DEFAULT 100,
  active          BOOLEAN NOT NULL DEFAULT true,
  sort_order      INTEGER NOT NULL DEFAULT 0,
  features_json   JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO plans (id, title, interval, amount_iqd, days, vpn_quota_gb, sort_order, features_json)
VALUES
  ('premium_monthly', 'Premium Monthly', 'monthly', 10000, 30, 100, 1,
   '["5 devices","Breach monitoring","VPN"]'::jsonb),
  ('premium_yearly', 'Premium Yearly', 'yearly', 100000, 365, 100, 2,
   '["5 devices","Breach monitoring","VPN","Save vs monthly"]'::jsonb)
ON CONFLICT (id) DO NOTHING;

ALTER TABLE accounts
  ADD COLUMN IF NOT EXISTS vault_initialized BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE devices
  ADD COLUMN IF NOT EXISTS locale TEXT NOT NULL DEFAULT 'en',
  ADD COLUMN IF NOT EXISTS push_token TEXT,
  ADD COLUMN IF NOT EXISTS notifications_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS notifications (
  id           UUID PRIMARY KEY,
  title_en     TEXT NOT NULL,
  title_ckb    TEXT,
  title_ar     TEXT,
  body_en      TEXT NOT NULL,
  body_ckb     TEXT,
  body_ar      TEXT,
  audience     TEXT NOT NULL DEFAULT 'all',
  created_by   UUID REFERENCES admin_users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notification_receipts (
  notification_id UUID NOT NULL REFERENCES notifications(id) ON DELETE CASCADE,
  account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  device_id       UUID REFERENCES devices(id) ON DELETE SET NULL,
  read_at         TIMESTAMPTZ,
  pushed_at       TIMESTAMPTZ,
  PRIMARY KEY (notification_id, account_id)
);

CREATE TABLE IF NOT EXISTS vpn_usage (
  account_id       UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
  period_start     TIMESTAMPTZ NOT NULL DEFAULT date_trunc('month', now()),
  bytes_used       BIGINT NOT NULL DEFAULT 0,
  throttled        BOOLEAN NOT NULL DEFAULT false,
  warned_80        BOOLEAN NOT NULL DEFAULT false,
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS app_settings (
  key         TEXT PRIMARY KEY,
  value_json  JSONB NOT NULL,
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_settings (key, value_json)
VALUES
  ('vpn_throttle_mbps', '5'::jsonb),
  ('admin_tailscale_only', 'false'::jsonb)
ON CONFLICT (key) DO NOTHING;
