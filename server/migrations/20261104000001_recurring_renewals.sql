-- Automatic renewals: the worker charges a payment method the customer saved at
-- checkout (only when they left auto-renew on, which is their consent to it).

-- The gateway's reusable token for a customer's wallet or card. Never a card number:
-- the gateway keeps the details and hands back an opaque reference. At most one live
-- method per account; a newer consented checkout replaces it.
CREATE TABLE IF NOT EXISTS payment_methods (
  id          UUID PRIMARY KEY,
  account_id  UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  gateway     TEXT NOT NULL,
  token       TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_methods_one_live
  ON payment_methods (account_id)
  WHERE revoked_at IS NULL;

-- A checkout the user started, or a renewal the worker charged.
ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS kind TEXT NOT NULL DEFAULT 'checkout'
    CHECK (kind IN ('checkout', 'renewal')),
  ADD COLUMN IF NOT EXISTS renews_subscription_id UUID REFERENCES subscriptions(id);

-- One renewal charge in flight per subscription, so two worker runs cannot both charge.
CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_one_pending_renewal
  ON payments (renews_subscription_id)
  WHERE kind = 'renewal' AND status = 'pending';

ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS renewal_attempts        INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS last_renewal_attempt_at TIMESTAMPTZ;

-- A reusable token can arrive with the webhook; the retry job needs it as well.
ALTER TABLE webhook_events
  ADD COLUMN IF NOT EXISTS payment_token TEXT;
