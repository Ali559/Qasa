-- Rate limits shared by every API process (the old limiter was a per-process HashMap,
-- so each replica and every restart granted a fresh allowance). Fixed windows, one row
-- per key. UNLOGGED: losing counters in a crash only resets a window.
CREATE UNLOGGED TABLE IF NOT EXISTS rate_limit_buckets (
  key          TEXT PRIMARY KEY,
  window_start TIMESTAMPTZ NOT NULL,
  hits         INTEGER NOT NULL
);

-- Never read or written by any code path. `entitlement_overrides` was superseded by
-- grant rows on `subscriptions`; `app_settings.vpn_throttle_mbps` was never consulted;
-- the materialized views were refreshed every 5 minutes but nothing read them (the
-- dashboard series come from `/v1/admin/timeseries`); `vault_initialized` was written on
-- every register and never read.
DROP TABLE IF EXISTS entitlement_overrides;
DROP TABLE IF EXISTS app_settings;
DROP MATERIALIZED VIEW IF EXISTS mv_revenue_daily;
DROP MATERIALIZED VIEW IF EXISTS mv_active_subscribers;
ALTER TABLE accounts DROP COLUMN IF EXISTS vault_initialized;
