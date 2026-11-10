-- Restore keys (ARCHITECTURE.md §9.5): SHA-256 of the random secret a device puts in its
-- backup files. A new device that restores the backup and finds no free slot may take the
-- place of the device whose hash matches, and only that one. The current and the previous
-- hash both count, so a backup written just before the device switched keys still works.
-- Both are cleared when the device is unlinked, so a key is used at most once.
ALTER TABLE devices
  ADD COLUMN IF NOT EXISTS restore_key_hash BYTEA,
  ADD COLUMN IF NOT EXISTS restore_key_prev_hash BYTEA;

ALTER TABLE devices
  ADD CONSTRAINT devices_restore_key_hash_len
    CHECK (restore_key_hash IS NULL OR octet_length(restore_key_hash) = 32),
  ADD CONSTRAINT devices_restore_key_prev_hash_len
    CHECK (restore_key_prev_hash IS NULL OR octet_length(restore_key_prev_hash) = 32);
