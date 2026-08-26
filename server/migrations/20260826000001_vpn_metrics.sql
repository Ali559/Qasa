-- VPN node metrics for admin dashboard + per-node IP pools

ALTER TABLE vpn_nodes
  ADD COLUMN IF NOT EXISTS cpu_percent     INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS memory_percent  INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS memory_label    TEXT,
  ADD COLUMN IF NOT EXISTS bandwidth_label TEXT,
  ADD COLUMN IF NOT EXISTS service_name    TEXT NOT NULL DEFAULT 'wg-main',
  ADD COLUMN IF NOT EXISTS ip_pool_cidr    CIDR NOT NULL DEFAULT '10.64.0.0/24';

CREATE INDEX IF NOT EXISTS idx_vpn_sessions_active
  ON vpn_sessions (node_id)
  WHERE disconnected_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_vpn_sessions_account_device_active
  ON vpn_sessions (account_id, device_id)
  WHERE disconnected_at IS NULL;
