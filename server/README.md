# Qasa Rust Server

Backend API for Qasa — device licensing, payments, breach monitoring, and VPN.

## Prerequisites

- Rust 1.78+
- Docker (for Postgres)

## Quick start

```bash
# Start Postgres + Redis
docker compose -f docker/docker-compose.yml up -d

# Copy env and run API
cp .env.example .env
cargo run -p qasa-api
```

API listens on `http://0.0.0.0:8080`.

## Smoke test (breach + VPN)

With the API and Postgres running:

```bash
./scripts/smoke-api.sh
```

The script exercises:

1. Device registration + license JWT
2. Free-tier breach lookup (expects 403)
3. Premium upgrade via FIB payment webhook
4. Breach hash ingest + prefix lookup
5. VPN connect → status → disconnect
6. Admin VPN node list + agent heartbeat

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
| POST | `/v1/devices/register` | Device handshake |
| GET | `/v1/devices` | List devices (Bearer license JWT) |
| DELETE | `/v1/devices/{id}` | Unlink device |
| GET | `/v1/license` | Refresh license JWT |
| POST | `/v1/payments/intents` | Create payment intent (Bearer license JWT) |
| GET | `/v1/payments/{id}` | Poll payment status |
| POST | `/v1/payments/webhook/fib` | FIB webhook (stub) |
| POST | `/v1/payments/webhook/fastpay` | FastPay webhook (stub) |
| POST | `/v1/payments/webhook/superqi` | SuperQi webhook (stub) |
| POST | `/v1/admin/auth/login` | Admin login |
| GET | `/v1/admin/overview` | Executive KPIs |
| GET | `/v1/admin/users` | User directory |
| GET | `/v1/admin/payments` | Payment audit log |
| GET | `/v1/admin/webhooks` | Webhook audit log |
| GET | `/v1/breach/prefix/{prefix}` | k-anonymity lookup (premium JWT, 60/hr) |
| POST | `/v1/vpn/connect` | Allocate WireGuard peer (premium JWT) |
| POST | `/v1/vpn/disconnect` | Tear down VPN session |
| GET | `/v1/vpn/status` | Current VPN session status |
| POST | `/v1/internal/vpn/nodes/{id}/heartbeat` | Node agent heartbeat |
| GET | `/v1/admin/vpn/nodes` | VPN node dashboard |
| GET | `/v1/admin/telemetry` | System telemetry |
| GET | `/metrics` | Prometheus (prod only) |

### Breach ingest CLI

```bash
# Ingest pre-computed SHA-256 hashes (one per line, 64 hex chars)
cargo run -p qasa-breach -- ingest --file hashes.txt --source hibp-2024

# Index stats
cargo run -p qasa-breach -- stats
```

### VPN node agent (dev)

```bash
# After API is running (seeds 3 dev nodes on startup)
cargo run -p qasa-vpn --bin qasa-vpn-agent -- \
  --node-id <uuid-from-vpn_nodes> \
  --agent-token dev-agent-token
```

## Production (QASA_ENV=prod)

Phase 5 features activate **only when `QASA_ENV=prod`**:

- JSON structured logging
- Prometheus `/metrics` endpoint
- `qasa-worker` background jobs (subscription expiry, MV refresh, webhook retry, stale VPN cleanup)
- Required secrets: `QASA_ADMIN_JWT_SECRET`, `QASA_LICENSE_PRIVATE_KEY`
- Dev seeds (VPN nodes, admin user) are **disabled** in prod

```bash
# Production stack
export QASA_ENV=prod
export QASA_ADMIN_JWT_SECRET=<strong-secret>
export QASA_LICENSE_PRIVATE_KEY=<base64-key>
docker compose -f docker/docker-compose.prod.yml up --build
```

Worker alone (exits immediately if not prod):

```bash
QASA_ENV=prod cargo run -p qasa-worker
```

## Dashboard (live API)

```bash
cd dashboard
cp .env.example .env   # VITE_QASA_API_URL=http://localhost:8080
npm run dev
```

## Default admin (dev)

- Email: `admin@qasa.io`
- Password: `admin123`

Set via `QASA_ADMIN_SEED_EMAIL` and `QASA_ADMIN_SEED_PASSWORD` (dev only).
