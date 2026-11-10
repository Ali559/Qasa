# Qasa Rust Server

Backend API for Qasa — device licensing, payments, breach monitoring, and VPN.

## Prerequisites

- Rust 1.82+
- PostgreSQL 18 running locally (`brew install postgresql@18 && brew services start postgresql@18`).
  No Docker: development, tests and production all use a native Postgres.

## Quick start

```bash
make db                      # from the repo root: creates the qasa role and database
cp .env.example .env
cargo run -p qasa-api        # applies migrations as it starts
```

API listens on `http://0.0.0.0:8080`. The database is
`postgres://qasa:qasa@localhost:5432/qasa`.

## Tests

```bash
cargo test                    # unit tests only; database tests report as `ignored`
./scripts/test-integration.sh # database tests against a disposable qasa_test
```

The integration tests under `crates/qasa-db/tests/` are `#[ignore]`d by default. This is
deliberate: a test that quietly returns when no database is available is reported by
libtest as `ok`, so a skipped run is indistinguishable from a passing one. As ignored they
show up as `N ignored` in the summary, which cannot be mistaken for a pass.

`./scripts/test-integration.sh` recreates `qasa_test` on the local Postgres,
applies migrations, and runs both the `qasa-db` repository tests and the `qasa-api`
router tests (real HTTP requests through the router: status codes, bodies, auth). Set
`PGPORT` for a Postgres on another port. The tests **refuse to start** if
`QASA_TEST_DATABASE_URL` is unset or does not name a database ending in `_test`, because
they insert and delete rows and `reconcile_active_peers` rewrites `vpn_nodes.active_peers`
for every node in the database. Set `QASA_TEST_ALLOW_ANY_DB=1` to override deliberately.

The tests clean up after themselves, so the database is left as it was found.


## Smoke test (breach + VPN)

With the API and Postgres running:

```bash
./scripts/smoke-api.sh
```

The script exercises:

1. Device registration + license JWT
2. Free-tier breach lookup + monitor register (expects 403)
3. Premium upgrade via FIB payment webhook
4. Premium breach monitor + inbox alert, hash ingest + prefix lookup
5. VPN connect → status → disconnect
6. Admin VPN node list + agent heartbeat

**Entitlements:** Breach monitoring, VPN, and multi-device (limit 5) require a live Premium subscription. Free accounts are limited to 1 device; they may unlink a remote device to free the slot. When a subscription lapses the account falls back to Free: the primary (oldest linked) device keeps working, and the others get `device_limit_exceeded` on register and license refresh until a slot is freed or Premium is renewed. JWT claim `entitlements` may include `"multi_device"` for all tiers — the real cap is `device_limit` (1 free / 5 premium).

Options:

```bash
./scripts/smoke-api.sh --skip-ingest          # skip breach ingest if already loaded
QASA_API_URL=http://127.0.0.1:8080 ./scripts/smoke-api.sh
```

Requires `curl` and `jq`.

## Phase 1 endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check |
| GET | `/.well-known/qasa-license-key` | Ed25519 public key |
| POST | `/v1/devices/register` | Device handshake. Optional `restore_key` (+ `replace_device`) from a restored backup lets it replace the device that made the backup when no slot is free |
| POST | `/v1/devices/link-check` | Can this account add another device? (pairing) |
| GET | `/v1/devices` | List devices (Bearer license JWT) |
| DELETE | `/v1/devices/others` | Unlink all except primary (first linked) |
| DELETE | `/v1/devices/me` | The calling device unlinks itself (logout / reset) |
| PUT | `/v1/devices/me/restore-key` | The device's new backup restore key hash (SHA-256, base64); keeps the previous one |
| DELETE | `/v1/devices/{id}` | Unlink device (ends its tokens, push token and VPN session) |
| DELETE | `/v1/account` | Delete the account (`{confirm_account_id}`); personal data removed, payments kept |
| GET | `/v1/license` | Refresh license JWT |
| POST | `/v1/payments/intents` | Create payment intent (Bearer license JWT) |
| GET | `/v1/payments` | Account payment history (`?limit=&before=`, newest first) |
| GET | `/v1/payments/{id}` | Poll payment status |
| POST | `/v1/payments/webhook/fib` | FIB webhook (stub) |
| POST | `/v1/payments/webhook/fastpay` | FastPay webhook (stub) |
| POST | `/v1/payments/webhook/superqi` | SuperQi webhook (stub) |
| POST | `/v1/admin/auth/login` | Admin login (10 tries / 15 min per email, 30 per IP) |
| POST | `/v1/admin/auth/logout` | End this admin session |
| GET | `/v1/admin/overview` | Executive KPIs |
| GET | `/v1/admin/users` | User directory (`?q=&limit=&before=`; next cursor in `X-Next-Before`) |
| GET | `/v1/admin/accounts/{id}` | Account detail: devices, subscriptions, payments, usage, audit |
| POST / DELETE | `/v1/admin/accounts/{id}/block` | Block (`{reason}`) / unblock an account |
| DELETE | `/v1/admin/accounts/{id}/devices/{device_id}` | Unlink a user's device |
| GET | `/v1/admin/payments` | Payments (`?q=&status=&gateway=&limit=&before=`) |
| GET | `/v1/admin/payments/{id}` | Payment with its webhook events |
| GET | `/v1/admin/timeseries` | Daily revenue per gateway, signups, new paid subs, gateway mix (`?days=`) |
| GET | `/v1/admin/audit` | Admin audit trail (`?target_id=&limit=&before=`) |
| GET | `/v1/admin/webhooks` | Webhook audit log (`?gateway_order_id=`) |
| POST | `/v1/admin/accounts/{account_id}/grants` | Grant Premium without payment (`{days, reason, plan_id?}`, audited) |
| DELETE | `/v1/admin/grants/{subscription_id}` | Take a grant's days back off (once, while it is current) |
| GET | `/v1/admin/grants` | Recent grants |
| GET | `/v1/notifications` | Inbox, newest first (`?limit=&before=`), with `unread_count` and `next_before` |
| GET | `/v1/notifications/unread-count` | Badge count |
| POST | `/v1/notifications/read-all` | Mark the whole inbox read |
| GET / DELETE | `/v1/notifications/{id}` | One notification (still marks it read) / remove it from this inbox only |
| POST | `/v1/notifications/{id}/read` | Mark one read |
| GET | `/v1/breach/prefix/{prefix}` | k-anonymity lookup (premium JWT, 60/hr) |
| POST / GET / DELETE | `/v1/breach/monitor` | Register (premium) / list with ids + full hashes / remove by hash |
| DELETE | `/v1/breach/monitor/{id}` | Remove a monitor by id |
| GET | `/v1/vpn/nodes` | Online nodes a device may pick (id, name, city, load %) |
| POST | `/v1/vpn/connect` | Allocate a WireGuard peer (`{client_public_key, node_id?}`; key required in prod) |
| POST | `/v1/vpn/disconnect` | Tear down VPN session |
| GET | `/v1/vpn/status` | Session status; `tunnel_up` once the node has seen a recent handshake |
| POST | `/v1/internal/vpn/nodes/{id}/heartbeat` | Node agent heartbeat + per-peer handshake/bytes (agent token) |
| GET | `/v1/internal/vpn/nodes/{id}/peers` | Peers the node should have (agent token) |
| GET / POST | `/v1/admin/vpn/nodes` | VPN node dashboard / register a node (returns agent token once; starts `draining`) |
| PATCH / DELETE | `/v1/admin/vpn/nodes/{id}` | Edit / set `online` or `draining` / retire (refused while it has sessions) |
| POST | `/v1/admin/vpn/nodes/{id}/agent-token` | Rotate the node agent token |
| GET | `/v1/admin/telemetry` | System telemetry |
| GET | `/metrics` | Prometheus (prod only) |

### Breach ingest CLI

```bash
# Ingest pre-computed SHA-256 hashes (one per line, 64 hex chars)
cargo run -p qasa-breach -- ingest --file hashes.txt --source hibp-2024

# Index stats
cargo run -p qasa-breach -- stats
```

### VPN node agent

Runs on each WireGuard node. Every interval it reports host health and each peer's
latest handshake and byte counters (from `wg show <iface> dump`), then fetches the peers
the node should have and adds or removes peers to match. Without `--apply` it only logs
what it would change.

```bash
# Dev, against a seeded node (health only, no interface)
cargo run -p qasa-vpn --bin qasa-vpn-agent -- \
  --node-id <uuid-from-vpn_nodes> --agent-token dev-agent-token

# On a node: dry run first, then apply
qasa-vpn-agent --node-id <uuid> --agent-token <token-from-admin> \
  --api-url https://api.qasa-app.com --wg-interface wg0          # logs changes only
qasa-vpn-agent ... --wg-interface wg0 --apply                # runs `wg set`
```

Sessions end when the node reports no handshake for 5 minutes (or none within 10
minutes of connecting); nodes with no heartbeat for 2 minutes go `offline` and take no
new peers. Data counted from peer byte counters drives the monthly quota: a push at
80%, then throttling (5 Mbps) at 100%. Per-peer rate limiting on the node is not
implemented yet; the agent logs when throttled peers are present.

## Production (QASA_ENV=prod)

Phase 5 features activate **only when `QASA_ENV=prod`**:

- JSON structured logging
- Prometheus `/metrics` endpoint
- `qasa-worker` background jobs (subscription expiry, abandoned-checkout expiry after 60 min, rate-limit window purge, webhook retry, stale VPN cleanup)
- Required secrets: `QASA_ADMIN_JWT_SECRET`, `QASA_LICENSE_PRIVATE_KEY`
- Dev seeds (VPN nodes, admin user) are **disabled** in prod

Production runs directly on a Debian 13 server (no Docker): Caddy, the API and worker as
systemd services, and PostgreSQL 18. Setup and deploys are scripted in
[`deploy/`](deploy/); the runbook is [`docs/deploy.md`](../docs/deploy.md).

```bash
deploy/deploy.sh   # from your Mac: build the landing page, copy, build and restart on the server
```

Web traffic arrives only through a Cloudflare Tunnel: Cloudflare terminates TLS,
`cloudflared` hands requests to Caddy on `127.0.0.1:80`, and the firewall allows only SSH.
Caddy sets `X-Forwarded-For` to Cloudflare's `Cf-Connecting-IP` and blocks `/metrics`. The
API listens on `127.0.0.1:8080` and trusts `X-Forwarded-For` only from loopback
(`QASA_TRUSTED_PROXY_CIDRS`), so the admin CIDR guard and rate limits see the real client
address. `DATABASE_URL` has no default in prod.

### Rate limits

Shared by every API process through Postgres (`rate_limit_buckets`); the worker purges
old windows. Limits are in `crates/qasa-api/src/limits.rs`:

| Limit | Key | Allowance |
|---|---|---|
| Breach prefix lookup | account | 60 / hour |
| Admin login | email / client IP | 10 / 30 per 15 min |
| Device register | account / client IP | 30 / 600 per 10 min |
| Link-check | client IP | 120 per 10 min |

Per-IP allowances are deliberately high: mobile carriers put many users behind one
CGNAT address.

Worker alone (exits immediately if not prod):

```bash
QASA_ENV=prod cargo run -p qasa-worker
```

## Dashboard (live API)

```bash
cd apps/dashboard
cp .env.example .env   # VITE_QASA_API_URL=http://localhost:8080
bun run dev
```

## Admin roles

`admin_users.role` is `admin` (full access) or `viewer` (read-only; every mutating
admin route returns 403). The role is read from the database on each request, so a
change or `admin_users.disabled_at` takes effect immediately. There is no API for
managing admins yet; use SQL.

## Default admin (dev)

- Email: `admin@qasa.io`
- Password: `admin123`

Set via `QASA_ADMIN_SEED_EMAIL` and `QASA_ADMIN_SEED_PASSWORD` (dev only).
