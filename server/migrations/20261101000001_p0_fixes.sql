-- Tokens issued at or before this instant are no longer accepted for the device.
-- Set when a device is unlinked, so re-linking it later does not revive old tokens.
ALTER TABLE devices
  ADD COLUMN IF NOT EXISTS tokens_valid_after TIMESTAMPTZ;

-- The gateway-verified outcome of a webhook, so the retry job settles from what was
-- verified rather than re-parsing a gateway-specific raw payload.
ALTER TABLE webhook_events
  ADD COLUMN IF NOT EXISTS event_status TEXT,
  ADD COLUMN IF NOT EXISTS reported_amount_iqd INTEGER;
