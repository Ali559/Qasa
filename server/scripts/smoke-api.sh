#!/usr/bin/env bash
# Smoke-test breach, VPN, FCM device prefs, monitors, and inbox against a running qasa-api.
#
# Usage:
#   ./scripts/smoke-api.sh
#   QASA_API_URL=http://127.0.0.1:8080 DATABASE_URL=postgres://... ./scripts/smoke-api.sh
#
# Prerequisites:
#   - qasa-api running (QASA_ENV=dev recommended — seeds VPN nodes + admin)
#   - Postgres reachable (for breach ingest CLI)
#   - curl, jq
#   - FCM env optional: without QASA_FCM_*, inbox still writes and pushes are logged only

set -euo pipefail

API_URL="${QASA_API_URL:-http://localhost:8080}"
DATABASE_URL="${DATABASE_URL:-postgres://qasa:qasa@localhost:5432/qasa}"
ADMIN_EMAIL="${QASA_ADMIN_SEED_EMAIL:-admin@qasa.io}"
ADMIN_PASSWORD="${QASA_ADMIN_SEED_PASSWORD:-admin123}"
SKIP_INGEST=0
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# SHA-256("password") — also used as a monitored email_hash for push/inbox alerts
SAMPLE_HASH="5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"
SAMPLE_PREFIX="5e884"
# Fake FCM registration tokens (iOS + Android use the same push_token field)
FAKE_FCM_TOKEN_IOS="smoke-fcm-ios-$(date +%s)"
FAKE_FCM_TOKEN_ANDROID="smoke-fcm-android-$(date +%s)"

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

step "Device prefs — store FCM push token (iOS path; Android uses the same field)"
prefs_body="$(api_expect_status PATCH /v1/devices/prefs 200 \
  -H "Authorization: Bearer $LICENSE" \
  -H 'Content-Type: application/json' \
  -d "{
    \"locale\": \"en\",
    \"push_token\": \"$FAKE_FCM_TOKEN_IOS\",
    \"notifications_enabled\": true
  }")"
echo "$prefs_body" | jq -e '.ok == true and .notifications_enabled == true' >/dev/null \
  || fail "Device prefs update failed: $prefs_body"
pass "FCM push_token stored via device prefs"

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

step "Free-tier breach monitor (hash match → inbox + FCM queue / deliver)"
monitor_body="$(api_expect_status POST /v1/breach/monitor 200 \
  -H "Authorization: Bearer $LICENSE" \
  -H 'Content-Type: application/json' \
  -d "{
    \"email_hash\": \"$SAMPLE_HASH\",
    \"label_hint\": \"smoke-free\"
  }")"
echo "$monitor_body" | jq -e '.ok == true' >/dev/null || fail "Monitor register failed: $monitor_body"
matched="$(echo "$monitor_body" | jq -r .matched)"
alerted="$(echo "$monitor_body" | jq -r .alerted)"
details_premium="$(echo "$monitor_body" | jq -r .details_require_premium)"
if [[ "$SKIP_INGEST" -eq 0 ]]; then
  [[ "$matched" == "true" ]] || fail "Expected matched=true after ingest, got: $monitor_body"
  [[ "$alerted" == "true" ]] || fail "Expected alerted=true (inbox/push), got: $monitor_body"
  [[ "$details_premium" == "true" ]] || fail "Free tier should require premium for details: $monitor_body"
  pass "Free monitor matched + alerted (conversion push path)"
else
  pass "Free monitor registered (matched=$matched; ingest skipped)"
fi

step "List breach monitors"
monitors_body="$(api_expect_status GET /v1/breach/monitor 200 \
  -H "Authorization: Bearer $LICENSE")"
monitor_count="$(echo "$monitors_body" | jq '.monitors | length')"
[[ "$monitor_count" -ge 1 ]] || fail "Expected ≥1 monitor, got: $monitors_body"
pass "Listed $monitor_count monitor(s)"

step "Inbox notifications after breach alert"
notif_body="$(api_expect_status GET /v1/notifications 200 \
  -H "Authorization: Bearer $LICENSE")"
notif_count="$(echo "$notif_body" | jq '.notifications | length')"
if [[ "$SKIP_INGEST" -eq 0 ]]; then
  [[ "$notif_count" -ge 1 ]] || fail "Expected inbox row after breach alert, got: $notif_body"
  pass "Inbox has $notif_count notification(s)"
else
  pass "Inbox list ok ($notif_count notification(s); ingest skipped)"
fi

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

step "Admin broadcast notification (inbox fanout; FCM only on targeted notify)"
admin_notif="$(api_expect_status POST /v1/admin/notifications 200 \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title_en": "Smoke test",
    "title_ckb": "تاقیکردنەوە",
    "title_ar": "اختبار",
    "body_en": "Admin inbox fanout from smoke-api.sh",
    "body_ckb": "پەیامی تاقیکردنەوە",
    "body_ar": "رسالة اختبار",
    "audience": "all"
  }')"
echo "$admin_notif" | jq -e '.notification.id and (.notified_accounts | type == "number")' >/dev/null \
  || fail "Admin notification create failed: $admin_notif"
pass "Admin notification created (notified_accounts=$(echo "$admin_notif" | jq -r .notified_accounts))"

admin_list="$(api_expect_status GET /v1/admin/notifications 200 \
  -H "Authorization: Bearer $ADMIN_TOKEN")"
admin_notif_count="$(echo "$admin_list" | jq '.notifications | length')"
[[ "$admin_notif_count" -ge 1 ]] || fail "Expected admin notification list, got: $admin_list"
pass "Admin lists $admin_notif_count notification(s)"

step "Register Android-shaped device + FCM prefs (same API as iOS)"
ANDROID_ACCOUNT="$ACCOUNT_ID"
ANDROID_FP="smoke-android-$(date +%s)"
android_reg="$(api_expect_status POST /v1/devices/register 201 \
  -H 'Content-Type: application/json' \
  -d "{
    \"account_id\": \"$ANDROID_ACCOUNT\",
    \"device_fingerprint\": \"$ANDROID_FP\",
    \"device_name\": \"Smoke Android\",
    \"platform\": \"android\"
  }")"
ANDROID_LICENSE="$(echo "$android_reg" | jq -r .license_token)"
[[ -n "$ANDROID_LICENSE" && "$ANDROID_LICENSE" != null ]] || fail "Android device register failed"
android_prefs="$(api_expect_status PATCH /v1/devices/prefs 200 \
  -H "Authorization: Bearer $ANDROID_LICENSE" \
  -H 'Content-Type: application/json' \
  -d "{
    \"locale\": \"ar\",
    \"push_token\": \"$FAKE_FCM_TOKEN_ANDROID\",
    \"notifications_enabled\": true
  }")"
echo "$android_prefs" | jq -e '.ok == true' >/dev/null || fail "Android prefs failed: $android_prefs"
pass "Android device registered + FCM push_token stored"

step "Unregister breach monitor"
unreg="$(api_expect_status DELETE /v1/breach/monitor 200 \
  -H "Authorization: Bearer $LICENSE" \
  -H 'Content-Type: application/json' \
  -d "{\"email_hash\": \"$SAMPLE_HASH\"}")"
echo "$unreg" | jq -e '.ok == true' >/dev/null || fail "Unregister monitor failed: $unreg"
pass "Breach monitor removed"

echo ""
echo -e "${GREEN}All smoke tests passed.${NC}"
