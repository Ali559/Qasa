#!/usr/bin/env bash
#
# Deploy from your Mac: build the landing page, copy it and the server code to the
# server, then build and restart there (install.sh). Run from anywhere in the repo:
#
#   server/deploy/deploy.sh            # host: qasa-api (from ~/.ssh/config)
#   QASA_HOST=other server/deploy/deploy.sh
#
# First time on a new server, run setup-debian.sh before this.
#
set -euo pipefail

HOST="${QASA_HOST:-qasa-api}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }

say "building the landing page"
(cd "$ROOT/apps/landing" && bun install --frozen-lockfile >/dev/null && bun --bun run build >/dev/null)

say "copying to $HOST"
# target/ stays on the server between deploys, so builds there are incremental. Local
# .env files are never copied.
rsync -az --delete --exclude target/ --exclude '.env' --exclude '.env.*' \
  "$ROOT/server/" "$HOST:/opt/qasa/src/server/"
rsync -az --delete "$ROOT/apps/landing/dist/" "$HOST:/var/www/qasa-app.com/"

say "building and restarting on $HOST"
ssh "$HOST" 'bash /opt/qasa/src/server/deploy/install.sh'
