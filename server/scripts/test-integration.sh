#!/usr/bin/env bash
#
# Runs the qasa-db integration tests against a disposable Postgres.
#
# Why this exists rather than a bare `cargo test`: the tests write to the database, and
# `reconcile_active_peers` rewrites `vpn_nodes.active_peers` for every row. They must
# never be pointed at a development or production database, and a plain `cargo test`
# must never quietly report them as passed. This script guarantees both.
#
# Usage:
#   ./scripts/test-integration.sh              # create/recreate qasa_test and run
#   KEEP_DB=1 ./scripts/test-integration.sh    # keep the database for inspection
#   QASA_POSTGRES_PORT=5432 ./scripts/test-integration.sh
#
set -euo pipefail

cd "$(dirname "$0")/.."

HOST_PORT="${QASA_POSTGRES_PORT:-5433}"
DB_NAME="qasa_test"
DB_USER="qasa"
CONTAINER="qasa-test-postgres"
# Same major version as production (PostgreSQL 18 on the server).
IMAGE="postgres:18"
DB_URL="postgres://${DB_USER}:${DB_USER}@127.0.0.1:${HOST_PORT}/${DB_NAME}"

say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\n\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null || die "docker is required"

# ---------------------------------------------------------------- container

# A container left over from an older Postgres is replaced, not reused.
if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER" &&
   [ "$(docker inspect -f '{{.Config.Image}}' "$CONTAINER")" != "$IMAGE" ]; then
  say "replacing $CONTAINER, which runs $(docker inspect -f '{{.Config.Image}}' "$CONTAINER")"
  docker rm -f "$CONTAINER" >/dev/null
fi

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  say "reusing running container $CONTAINER"
else
  if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
    docker rm -f "$CONTAINER" >/dev/null
  fi
  say "starting $CONTAINER on 127.0.0.1:${HOST_PORT}"
  docker run -d \
    --name "$CONTAINER" \
    -e POSTGRES_USER="$DB_USER" \
    -e POSTGRES_PASSWORD="$DB_USER" \
    -e POSTGRES_DB="$DB_NAME" \
    -p "127.0.0.1:${HOST_PORT}:5432" \
    --health-cmd "pg_isready -U ${DB_USER} -d ${DB_NAME}" \
    --health-interval 2s \
    --health-timeout 3s \
    --health-retries 20 \
    "$IMAGE" >/dev/null
fi

say "waiting for postgres to become healthy (first boot runs initdb, which takes a while)"
ready=0
for _ in $(seq 1 120); do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null || echo "none")
  if [ "$status" = "healthy" ]; then
    ready=1
    break
  fi
  if [ "$status" = "unhealthy" ]; then
    docker logs --tail 30 "$CONTAINER" >&2
    die "postgres reported unhealthy"
  fi
  sleep 1
done
[ "$ready" = "1" ] || { docker logs --tail 30 "$CONTAINER" >&2; die "postgres did not become healthy in time"; }
docker exec "$CONTAINER" pg_isready -U "$DB_USER" -d "$DB_NAME" >/dev/null 2>&1 \
  || die "container is healthy but ${DB_NAME} is not accepting connections"

# ------------------------------------------------------- pristine database

if [ "${KEEP_DB:-0}" != "1" ]; then
  say "recreating $DB_NAME so the run starts from a known-empty state"
  docker exec "$CONTAINER" psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 -q \
    -c "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE);" \
    -c "CREATE DATABASE ${DB_NAME};"
fi

say "applying migrations"
for f in migrations/*.sql; do
  docker exec -i "$CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -q -f - < "$f"
done

# ------------------------------------------------------------- run the tests

say "running integration tests against ${DB_URL}"
# qasa-db: repository tests. qasa-api: the same database driven through the real HTTP
# router (status codes, JSON bodies, auth). Both suites must pass.
status=0
QASA_TEST_DATABASE_URL="$DB_URL" cargo test -p qasa-db --tests --offline -- --include-ignored "$@" || status=$?
QASA_TEST_DATABASE_URL="$DB_URL" cargo test -p qasa-api --offline -- --include-ignored "$@" || status=$?

if [ "${KEEP_DB:-0}" != "1" ]; then
  say "leaving $DB_NAME in place for the next run (it is recreated each time)"
fi

exit $status
