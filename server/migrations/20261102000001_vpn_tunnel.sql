-- When the node last saw a WireGuard handshake from this peer, as reported by the node
-- agent. Sessions are expired on this, not on how long ago they were created.
ALTER TABLE vpn_sessions
  ADD COLUMN IF NOT EXISTS last_handshake_at TIMESTAMPTZ;

-- Before adding uniqueness, end duplicates that earlier races could create: keep the
-- newest live session per device, per (node, ip) and per (node, key).
UPDATE vpn_sessions s SET disconnected_at = now()
WHERE s.disconnected_at IS NULL AND EXISTS (
  SELECT 1 FROM vpn_sessions t
  WHERE t.disconnected_at IS NULL AND t.id <> s.id
    AND (t.device_id = s.device_id
         OR (t.node_id = s.node_id AND t.assigned_ip = s.assigned_ip)
         OR (t.node_id = s.node_id AND t.client_public_key = s.client_public_key))
    AND (t.connected_at, t.id) > (s.connected_at, s.id)
);

-- One live session per device; one live peer per address and per key on a node. These
-- make concurrent connects safe: the loser gets a unique violation and retries instead
-- of two peers sharing an address.
CREATE UNIQUE INDEX IF NOT EXISTS uniq_vpn_sessions_live_device
  ON vpn_sessions (device_id) WHERE disconnected_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uniq_vpn_sessions_live_ip
  ON vpn_sessions (node_id, assigned_ip) WHERE disconnected_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uniq_vpn_sessions_live_key
  ON vpn_sessions (node_id, client_public_key) WHERE disconnected_at IS NULL;

-- Usage notices are sent once per period.
ALTER TABLE vpn_usage
  ADD COLUMN IF NOT EXISTS throttle_notified BOOLEAN NOT NULL DEFAULT false;
