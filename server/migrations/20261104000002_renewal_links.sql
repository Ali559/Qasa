-- Renewal links: where no gateway can charge a saved wallet (Wayl cannot), the worker
-- creates a payment link before an auto-renew subscription ends and sends it to the
-- customer, who pays it like a checkout.

-- When a link stops working. Set only on renewal links, which stay open for days; a
-- checkout is abandoned after an hour instead.
ALTER TABLE payments
  ADD COLUMN IF NOT EXISTS pay_by TIMESTAMPTZ,
  -- When the worker last asked the gateway about this payment, for missed webhooks.
  ADD COLUMN IF NOT EXISTS last_checked_at TIMESTAMPTZ;

-- Set when the worker sent this period's renewal link. One link per period: a paid
-- renewal starts a new subscription row, which has none yet.
ALTER TABLE subscriptions
  ADD COLUMN IF NOT EXISTS renewal_link_sent_at TIMESTAMPTZ;
