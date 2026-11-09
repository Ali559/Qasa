#!/usr/bin/env bash
#
# Runs the qasa-db and qasa-api integration tests against a disposable database,
# `qasa_test`, on the local Postgres (the same major version as production, 18).
#
# Why this exists rather than a bare `cargo test`: the tests write to the database, and
# `reconcile_active_peers` rewrites `vpn_nodes.active_peers` for every row. They must
# never be pointed at a development or production database, and a plain `cargo test`
# must never quietly report them as passed. This script guarantees both.
#
# Usage:
#   ./scripts/test-integration.sh              # recreate qasa_test and run
#   KEEP_DB=1 ./scripts/test-integration.sh    # reuse qasa_test as it is (inspection)
#   PGPORT=5544 ./scripts/test-integration.sh  # a Postgres on another port
#
# Needs: a running local Postgres (`brew services start postgresql@18`), a superuser
# that can connect over the default socket (Homebrew's is your macOS user), and the
# `qasa` role (`make db`).
set -euo pipefail

cd "$(dirname "$0")/.."

PGHOST_TCP="${PGHOST_TCP:-127.0.0.1}"
PORT="${PGPORT:-5432}"
DB_NAME="qasa_test"
DB_USER="qasa"
DB_URL="postgres://${DB_USER}:${DB_USER}@${PGHOST_TCP}:${PORT}/${DB_NAME}"

say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\n\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

command -v psql >/dev/null || die "psql is required (brew install postgresql@18)"
pg_isready -p "$PORT" >/dev/null 2>&1 || die "Postgres is not accepting connections on port $PORT (brew services start postgresql@18)"
# Database admin (drop/create) as the local superuser over the socket.
admin() { psql -p "$PORT" -d postgres -v ON_ERROR_STOP=1 -q "$@"; }
admin -Atc "SELECT 1 FROM pg_roles WHERE rolname = '${DB_USER}'" | grep -q 1 \
  || die "the ${DB_USER} role is missing: run make db"

# ------------------------------------------------------- pristine database

if [ "${KEEP_DB:-0}" != "1" ]; then
  say "recreating $DB_NAME so the run starts from a known-empty state"
  admin -c "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE);" \
        -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"

  say "applying migrations"
  for f in migrations/*.sql; do
    psql "$DB_URL" -v ON_ERROR_STOP=1 -q -f "$f" >/dev/null
  done
fi

# ------------------------------------------------------------- run the tests

say "running integration tests against ${DB_URL}"
# qasa-db: repository tests. qasa-api: the same database driven through the real HTTP
# router (status codes, JSON bodies, auth). Both suites must pass.
status=0
QASA_TEST_DATABASE_URL="$DB_URL" cargo test -p qasa-db --tests --offline -- --include-ignored "$@" || status=$?
QASA_TEST_DATABASE_URL="$DB_URL" cargo test -p qasa-api --offline -- --include-ignored "$@" || status=$?

exit $status
