#!/usr/bin/env bash
#
# First-time setup of the production server: Debian 13, no Docker. Run as root, from the
# repository root on your Mac:
#
#   ssh qasa-api 'bash -s' < server/deploy/setup-debian.sh
#
# Safe to run again; every step checks what is already there. Afterwards, deploy the code
# with server/deploy/deploy.sh.
#
# - SSH: keys only (root keeps key login; passwords off). Refuses if root has no key.
# - Firewall: SSH, HTTP and HTTPS only.
# - Automatic security updates, rebooting at 04:00 when a kernel update needs it.
# - fail2ban for SSH.
# - PostgreSQL 18 from the PostgreSQL apt repository, local connections only, with the
#   qasa role and database.
# - Caddy from its apt repository, Rust for building the server, and the `qasa` system
#   user the services run as.
# - /etc/qasa/qasa.env with the database URL and freshly generated secrets.
#
set -euo pipefail

PG_MAJOR=18
ENV_FILE=/etc/qasa/qasa.env

say() { printf '\n\033[1;34m==>\033[0m %s\n' "$*"; }
die() { printf '\n\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" -eq 0 ] || die "run as root"
. /etc/os-release
[ "${ID:-}" = "debian" ] && [ "${VERSION_CODENAME:-}" = "trixie" ] ||
  die "written for Debian 13 (trixie), this is ${PRETTY_NAME:-unknown}"

# Never inherit the caller's locale over SSH: it may not exist here, and initdb would
# fall back to SQL_ASCII.
export LANG=C.UTF-8 LC_ALL=C.UTF-8 DEBIAN_FRONTEND=noninteractive

say "packages"
apt-get update -q
apt-get upgrade -yq
apt-get install -yq ca-certificates curl gnupg git rsync build-essential pkg-config \
  ufw unattended-upgrades fail2ban python3-systemd \
  debian-keyring debian-archive-keyring apt-transport-https postgresql-common

say "SSH: keys only"
[ -s /root/.ssh/authorized_keys ] || die "root has no authorized SSH key; not turning off passwords"
# sshd takes the first value it reads, so this file must sort before the others.
cat > /etc/ssh/sshd_config.d/00-qasa.conf <<'EOF'
PermitRootLogin prohibit-password
PasswordAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
EOF
sshd -t
systemctl reload ssh

say "firewall: SSH, HTTP and HTTPS only"
ufw default deny incoming >/dev/null
ufw default allow outgoing >/dev/null
ufw allow 22/tcp >/dev/null
ufw allow 80/tcp >/dev/null
ufw allow 443/tcp >/dev/null
ufw allow 443/udp >/dev/null
ufw --force enable

say "automatic security updates"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
# Kernel updates need a reboot; every service here starts on boot.
cat > /etc/apt/apt.conf.d/52qasa-reboot <<'EOF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:00";
EOF
# Also take PostgreSQL and Caddy updates from their own repositories.
cat > /etc/apt/apt.conf.d/53qasa-origins <<'EOF'
Unattended-Upgrade::Origins-Pattern {
        "origin=apt.postgresql.org";
        "origin=cloudsmith/caddy/stable";
};
EOF
systemctl enable --now unattended-upgrades >/dev/null

say "fail2ban for SSH"
cat > /etc/fail2ban/jail.d/qasa.local <<'EOF'
[sshd]
enabled = true
backend = systemd
maxretry = 5
bantime = 1h
EOF
systemctl enable --now fail2ban >/dev/null
systemctl restart fail2ban

say "PostgreSQL $PG_MAJOR"
if [ ! -f /etc/apt/sources.list.d/pgdg.sources ] && [ ! -f /etc/apt/sources.list.d/pgdg.list ]; then
  /usr/share/postgresql-common/pgdg/apt.postgresql.org.sh -y
fi
apt-get install -yq "postgresql-$PG_MAJOR"
PGC=/etc/postgresql/$PG_MAJOR/main
[ -f "$PGC/pg_hba.conf.orig" ] || cp "$PGC/pg_hba.conf" "$PGC/pg_hba.conf.orig"
cat > "$PGC/pg_hba.conf" <<'EOF'
# Qasa: only the qasa user, only to the qasa database, only from this machine.
# Admin work goes through the postgres OS account (sudo -u postgres psql).
# TYPE  DATABASE  USER      ADDRESS        METHOD
local   all       postgres                 peer
local   qasa      qasa                     peer
host    qasa      qasa      127.0.0.1/32   scram-sha-256
host    qasa      qasa      ::1/128        scram-sha-256
EOF
cat > "$PGC/conf.d/10-qasa.conf" <<'EOF'
# Qasa on a 4 vCPU / 8 GB VPS shared with the API and worker.
listen_addresses = 'localhost'
password_encryption = scram-sha-256

shared_buffers = 2GB
effective_cache_size = 5GB
work_mem = 16MB
maintenance_work_mem = 256MB
random_page_cost = 1.1
effective_io_concurrency = 200
wal_compression = zstd

idle_in_transaction_session_timeout = 5min
log_min_duration_statement = 500ms
log_lock_waits = on
log_timezone = 'UTC'
timezone = 'UTC'
EOF
systemctl restart "postgresql@$PG_MAJOR-main"

say "qasa role, database and secrets"
install -d -m 750 -o root -g root /etc/qasa
touch "$ENV_FILE"
if ! grep -q '^DATABASE_URL=' "$ENV_FILE"; then
  PW="$(openssl rand -hex 32)"
  sudo -u postgres psql -v ON_ERROR_STOP=1 -q -v pw="$PW" <<'SQL'
SELECT 'CREATE ROLE qasa LOGIN' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'qasa')\gexec
ALTER ROLE qasa WITH LOGIN PASSWORD :'pw' NOSUPERUSER NOCREATEDB NOCREATEROLE;
SQL
  echo "DATABASE_URL=postgres://qasa:$PW@127.0.0.1:5432/qasa" >> "$ENV_FILE"
fi
if ! sudo -u postgres psql -Atc "SELECT 1 FROM pg_database WHERE datname = 'qasa'" | grep -q 1; then
  sudo -u postgres createdb -O qasa -E UTF8 qasa
fi
sudo -u postgres psql -q -d qasa \
  -c "REVOKE ALL ON DATABASE qasa FROM PUBLIC" \
  -c "REVOKE CREATE ON SCHEMA public FROM PUBLIC" \
  -c "ALTER SCHEMA public OWNER TO qasa"

add_setting() { grep -q "^$1=" "$ENV_FILE" || echo "$1=$2" >> "$ENV_FILE"; }
add_setting QASA_ENV prod
add_setting BIND_ADDR 127.0.0.1:8080
add_setting QASA_PUBLIC_HOST api.qasa-app.com
# Caddy runs on this machine, so only loopback may set X-Forwarded-For.
add_setting QASA_TRUSTED_PROXY_CIDRS 127.0.0.1/32,::1/128
add_setting QASA_ADMIN_ALLOW_CIDRS tailscale
add_setting QASA_ADMIN_CORS_ORIGIN https://admin.qasa-app.com
add_setting QASA_ADMIN_JWT_SECRET "$(openssl rand -hex 32)"
if ! grep -q '^QASA_LICENSE_PRIVATE_KEY=' "$ENV_FILE"; then
  # The API reads the raw 32-byte Ed25519 seed, base64. DER encodings end with the seed
  # and with the raw public key.
  KEY_PEM="$(openssl genpkey -algorithm ed25519)"
  PRIV="$(printf '%s' "$KEY_PEM" | openssl pkey -outform DER | tail -c 32 | base64)"
  PUB="$(printf '%s' "$KEY_PEM" | openssl pkey -pubout -outform DER | tail -c 32 | base64)"
  {
    echo "QASA_LICENSE_PRIVATE_KEY=$PRIV"
    echo "# Release builds of the apps pin this as QASA_LICENSE_PUBLIC_KEY: $PUB"
  } >> "$ENV_FILE"
fi

say "the qasa system user"
id qasa >/dev/null 2>&1 || adduser --system --group --no-create-home --home /nonexistent qasa
chown root:qasa "$ENV_FILE"
chmod 640 "$ENV_FILE"
install -d -m 755 /opt/qasa /opt/qasa/src /var/www/qasa-app.com
install -d -m 700 -o postgres -g postgres /var/backups/qasa

say "Caddy"
if [ ! -f /usr/share/keyrings/caddy-stable-archive-keyring.gpg ]; then
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' |
    gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
  curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
    > /etc/apt/sources.list.d/caddy-stable.list
  chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
  apt-get update -q
fi
apt-get install -yq caddy

say "Rust, for building the server here"
if [ ! -x /root/.cargo/bin/cargo ]; then
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --profile minimal
fi
/root/.cargo/bin/rustup update stable >/dev/null 2>&1 || true
/root/.cargo/bin/cargo --version

say "done"
echo "Next, from your Mac: server/deploy/deploy.sh"
