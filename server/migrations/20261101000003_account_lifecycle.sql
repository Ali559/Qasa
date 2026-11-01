-- A deleted account is kept as a tombstone: its id must never be re-registered (the
-- devices that held it have been wiped), and its payments and subscriptions stay as
-- financial records. Everything personal is removed at deletion time.
ALTER TABLE accounts
  ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;
