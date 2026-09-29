-- Approximate location label for linked-device UI (client-supplied region/city hint).
ALTER TABLE devices
  ADD COLUMN IF NOT EXISTS location TEXT;
