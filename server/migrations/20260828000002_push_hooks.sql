-- Push hooks: monitored email hashes + notification idempotency columns

CREATE TABLE IF NOT EXISTS monitored_email_hashes (
  id              UUID PRIMARY KEY,
  account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  email_hash      TEXT NOT NULL,
  label_hint      TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  last_matched_at TIMESTAMPTZ,
  last_alerted_at TIMESTAMPTZ,
  UNIQUE (account_id, email_hash)
);

CREATE INDEX IF NOT EXISTS idx_monitored_email_hash ON monitored_email_hashes (email_hash);

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS expiry_warned_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS renew_failed_notified_at TIMESTAMPTZ;

ALTER TABLE notifications
  ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'generic',
  ADD COLUMN IF NOT EXISTS dedupe_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_dedupe
  ON notifications (dedupe_key)
  WHERE dedupe_key IS NOT NULL;
