-- Subscription management: auto-renew, cancel metadata; payment plan selection

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN NOT NULL DEFAULT true,
  ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS cancellation_reason TEXT;

ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS plan_id TEXT NOT NULL DEFAULT 'premium_monthly',
  ADD COLUMN IF NOT EXISTS auto_renew BOOLEAN NOT NULL DEFAULT true;
