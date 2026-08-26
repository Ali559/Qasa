-- Webhook retry tracking for prod worker

ALTER TABLE webhook_events
  ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
