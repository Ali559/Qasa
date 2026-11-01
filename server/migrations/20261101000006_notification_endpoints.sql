-- Per-user soft delete: removing a broadcast from one inbox must not remove it for
-- everyone, so the receipt (not the notification) carries the deletion.
ALTER TABLE notification_receipts
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notification_receipts_inbox
  ON notification_receipts (account_id, notification_id)
  WHERE deleted_at IS NULL;

-- Broadcast receipts the worker has not pushed yet.
CREATE INDEX IF NOT EXISTS idx_notification_receipts_unpushed
  ON notification_receipts (notification_id)
  WHERE pushed_at IS NULL AND deleted_at IS NULL;

-- `audience` used to be free text and was ignored by the fan-out.
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_audience_check;
ALTER TABLE notifications
  ADD CONSTRAINT notifications_audience_check
  CHECK (audience IN ('all', 'subscribers', 'free', 'targeted'));
