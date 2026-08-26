#!/usr/bin/env bash
# Smoke-test breach + VPN flows against a running qasa-api instance.
#
# Usage:
#   ./scripts/smoke-api.sh
#   QASA_API_URL=http://127.0.0.1:8080 DATABASE_URL=postgres://... ./scripts/smoke-api.sh
#
# Prerequisites:
#   - qasa-api running (QASA_ENV=dev recommended — seeds VPN nodes + admin)
#   - Postgres reachable (for breach ingest CLI)
#   - curl, jq

set -euo pipefail

API_URL="${QASA_API_URL:-http://localhost:8080}"
DATABASE_URL="${DATABASE_URL:-postgres://qasa:qasa@localhost:5432/qasa}"
ADMIN_EMAIL="${QASA_ADMIN_SEED_EMAIL:-admin@qasa.io}"
ADMIN_PASSWORD="${QASA_ADMIN_SEED_PASSWORD:-admin123}"
SKIP_INGEST=0
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# SHA-256("password")
SAMPLE_HASH="5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
SAMPLE_PREFIX="5e884"

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

pass() { echo -e "${GREEN}✓${NC} $*"; }
fail() { echo -e "${RED}✗${NC} $*" >&2; exit 1; }
step() { echo -e "\n${BLUE}→${NC} $*"; }

usage() {
  sed -n '2,12p' "$0"
  echo ""
  echo "Options:"
  echo "  --skip-ingest    Skip breach hash ingest (use if index already loaded)"
  echo "  --api-url URL    API base URL (default: $API_URL)"
  echo "  --database-url   Postgres URL for breach CLI (default: DATABASE_URL env)"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-ingest) SKIP_INGEST=1; shift ;;
    --api-url) API_URL="$2"; shift 2 ;;
    --database-url) DATABASE_URL="$2"; shift 2 ;;
    -h|--help) usage ;;
    *) fail "Unknown option: $1" ;;
  esac
done

command -v curl >/dev/null || fail "curl is required"
command -v jq >/dev/null || fail "jq is required"

api() {
  local method="$1"
  local path="$2"
  shift 2
  curl -sS -X "$method" "${API_URL}${path}" "$@"
}

api_expect_status() {
  local method="$1"
  local path="$2"
  local expected="$3"
  shift 3
  local body_file status
  body_file="$(mktemp)"
  status="$(curl -sS -o "$body_file" -w '%{http_code}' -X "$method" "${API_URL}${path}" "$@")"
  if [[ "$status" != "$expected" ]]; then
    echo "Expected HTTP $expected, got $status:" >&2
    cat "$body_file" >&2
    rm -f "$body_file"
    exit 1
  fi
  cat "$body_file"
  rm -f "$body_file"
}

step "Checking API health"
health="$(api GET /health)"
echo "$health" | jq -e '.status == "ok"' >/dev/null || fail "Health check failed: $health"
pass "API is up at $API_URL"

ACCOUNT_ID="$(uuidgen | tr '[:upper:]' '[:lower:]')"
FINGERPRINT="smoke-test-$(date +%s)"

step "Registering device (account=$ACCOUNT_ID)"
register_body="$(api_expect_status POST /v1/devices/register 201 \
  -H 'Content-Type: application/json' \
  -d "{
    \"account_id\": \"$ACCOUNT_ID\",
    \"device_fingerprint\": \"$FINGERPRINT\",
    \"device_name\": \"Smoke Test Device\",
    \"platform\": \"cli\"
  }")"

LICENSE="$(echo "$register_body" | jq -r .license_token)"
TIER="$(echo "$register_body" | jq -r .tier)"
[[ -n "$LICENSE" && "$LICENSE" != null ]] || fail "Missing license_token"
pass "Device registered (tier=$TIER)"

step "Breach lookup on free tier should be forbidden"
free_body="$(mktemp)"
free_status="$(curl -sS -o "$free_body" -w '%{http_code}' \
  -H "Authorization: Bearer $LICENSE" \
  "${API_URL}/v1/breach/prefix/${SAMPLE_PREFIX}")"
if [[ "$free_status" != "403" ]]; then
  echo "Expected HTTP 403, got $free_status:" >&2
  cat "$free_body" >&2
  rm -f "$free_body"
  fail "Free-tier breach lookup should return 403 (401 usually means invalid license JWT — restart API after rebuilding)"
fi
rm -f "$free_body"
pass "Free tier correctly blocked (403)"

step "Upgrading to premium via payment webhook"
intent_body="$(api_expect_status POST /v1/payments/intents 200 \
  -H "Authorization: Bearer $LICENSE" \
  -H 'Content-Type: application/json' \
  -d '{"gateway":"fib","amount_iqd":15000}')"
ORDER_ID="$(echo "$intent_body" | jq -r .gateway_order_id)"
[[ -n "$ORDER_ID" && "$ORDER_ID" != null ]] || fail "Missing gateway_order_id"

api_expect_status POST /v1/payments/webhook/fib 200 \
  -H 'Content-Type: application/json' \
  -d "{\"order_id\":\"$ORDER_ID\",\"status\":\"success\"}" >/dev/null

license_body="$(api_expect_status GET /v1/license 200 \
  -H "Authorization: Bearer $LICENSE")"
LICENSE="$(echo "$license_body" | jq -r .license_token)"
TIER="$(echo "$license_body" | jq -r .tier)"
[[ "$TIER" == "premium" ]] || fail "Expected premium tier after webhook, got $TIER"
pass "Premium activated via FIB webhook"

if [[ "$SKIP_INGEST" -eq 0 ]]; then
  step "Ingesting sample breach hash"
  hash_file="$(mktemp)"
  echo "$SAMPLE_HASH" >"$hash_file"
  (
    cd "$SERVER_ROOT"
    DATABASE_URL="$DATABASE_URL" cargo run -q -p qasa-breach -- ingest \
      --file "$hash_file" \
      --source smoke-test >/dev/null
  )
  rm -f "$hash_file"
  pass "Ingested hash prefix $SAMPLE_PREFIX"
else
  step "Skipping breach ingest (--skip-ingest)"
fi

step "Breach prefix lookup"
breach_body="$(api_expect_status GET "/v1/breach/prefix/${SAMPLE_PREFIX}" 200 \
  -H "Authorization: Bearer $LICENSE")"
breach_count="$(echo "$breach_body" | jq -r .count)"
[[ "$breach_count" -ge 1 ]] || fail "Expected breach matches, got: $breach_body"
pass "Breach lookup returned $breach_count match(es)"

step "VPN connect"
vpn_connect_body="$(api_expect_status POST /v1/vpn/connect 200 \
  -H "Authorization: Bearer $LICENSE" \
  -H 'Content-Type: application/json' \
  -d '{}')"
endpoint="$(echo "$vpn_connect_body" | jq -r .config.endpoint)"
[[ -n "$endpoint" && "$endpoint" != null ]] || fail "Missing VPN config endpoint: $vpn_connect_body"
pass "VPN connected (endpoint=$endpoint)"

step "VPN status (connected)"
vpn_status_body="$(api_expect_status GET /v1/vpn/status 200 \
  -H "Authorization: Bearer $LICENSE")"
connected="$(echo "$vpn_status_body" | jq -r .connected)"
[[ "$connected" == "true" ]] || fail "Expected connected=true, got: $vpn_status_body"
pass "VPN status reports connected"

step "VPN disconnect"
api_expect_status POST /v1/vpn/disconnect 200 \
  -H "Authorization: Bearer $LICENSE" >/dev/null
pass "VPN disconnected"

step "VPN status (disconnected)"
vpn_status_body="$(api_expect_status GET /v1/vpn/status 200 \
  -H "Authorization: Bearer $LICENSE")"
connected="$(echo "$vpn_status_body" | jq -r .connected)"
[[ "$connected" == "false" ]] || fail "Expected connected=false after disconnect, got: $vpn_status_body"
pass "VPN status reports disconnected"

step "Admin VPN nodes + agent heartbeat"
admin_body="$(api_expect_status POST /v1/admin/auth/login 200 \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
ADMIN_TOKEN="$(echo "$admin_body" | jq -r .token)"
[[ -n "$ADMIN_TOKEN" && "$ADMIN_TOKEN" != null ]] || fail "Admin login failed ($ADMIN_EMAIL)"

nodes_body="$(api_expect_status GET /v1/admin/vpn/nodes 200 \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
node_count="$(echo "$nodes_body" | jq '.nodes | length')"
[[ "$node_count" -ge 1 ]] || fail "Expected seeded VPN nodes in dev, got: $nodes_body"
pass "Admin sees $node_count VPN node(s)"

NODE_ID="$(command -v psql >/dev/null && psql "$DATABASE_URL" -tAc "SELECT id FROM vpn_nodes ORDER BY created_at LIMIT 1;" | tr -d '[:space:]' || true)"
if [[ -n "$NODE_ID" ]]; then
  api_expect_status POST "/v1/internal/vpn/nodes/${NODE_ID}/heartbeat" 200 \
    -H 'Content-Type: application/json' \
    -H 'x-qasa-agent-token: dev-agent-token' \
    -d '{"cpu_percent":10,"memory_percent":20,"active_peers":1}' >/dev/null
  pass "VPN agent heartbeat accepted"
else
  echo "  (skipped heartbeat — psql not available or no nodes in DB)"
fi

echo ""
echo -e "${GREEN}All smoke tests passed.${NC}"
