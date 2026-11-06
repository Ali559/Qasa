#!/usr/bin/env bash
#
# Build and (re)start everything from the code in /opt/qasa/src. Runs on the server as
# root; deploy.sh copies the code there and calls this.
#
set -euo pipefail

SRC=/opt/qasa/src/server
DEPLOY=$SRC/deploy

say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }

say "building the API and worker"
cd "$SRC"
# Release builds use most of the RAM for a few minutes; one job per core is fine on 8 GB.
/root/.cargo/bin/cargo build --release --locked -p qasa-api -p qasa-worker

say "installing"
install -m 755 target/release/qasa-api target/release/qasa-worker /usr/local/bin/
install -m 755 "$DEPLOY/backup-db.sh" /usr/local/sbin/qasa-backup-db
install -m 644 "$DEPLOY"/systemd/*.service "$DEPLOY"/systemd/*.timer /etc/systemd/system/
caddy validate --config "$DEPLOY/Caddyfile" --adapter caddyfile >/dev/null
install -m 644 "$DEPLOY/Caddyfile" /etc/caddy/Caddyfile
chown -R root:root /var/www/qasa-app.com
chmod -R u=rwX,go=rX /var/www/qasa-app.com

say "restarting"
systemctl daemon-reload
systemctl enable --quiet qasa-api qasa-worker qasa-backup.timer caddy
# The API applies migrations as it starts; the worker waits for it to answer first.
systemctl restart qasa-api
for _ in $(seq 1 30); do
  curl -fsS http://127.0.0.1:8080/health >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS http://127.0.0.1:8080/health || { journalctl -u qasa-api -n 30 --no-pager; exit 1; }
echo
systemctl restart qasa-worker
systemctl start qasa-backup.timer
systemctl reload caddy 2>/dev/null || systemctl restart caddy

systemctl --no-pager --lines=0 status qasa-api qasa-worker caddy | grep -E "●|Active:"
