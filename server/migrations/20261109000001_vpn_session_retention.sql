-- Ended VPN sessions are deleted 30 days after they end (worker `purge_ended_vpn_sessions`,
-- privacy policy). This keeps that daily delete an index scan.
CREATE INDEX IF NOT EXISTS idx_vpn_sessions_ended
  ON vpn_sessions (disconnected_at) WHERE disconnected_at IS NOT NULL;
