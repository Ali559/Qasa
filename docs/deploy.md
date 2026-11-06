# Deploying Qasa

One server runs everything public, directly on Debian with no Docker: Caddy (HTTPS), the
landing page, the API, the worker and PostgreSQL 18. VPN nodes are separate servers, added
later. The files are in [`server/deploy/`](../server/deploy/).

| Name | Serves |
| --- | --- |
| `qasa-app.com` | Landing page (`apps/landing`), from `/var/www/qasa-app.com` |
| `www.qasa-app.com` | Redirects to `qasa-app.com` |
| `api.qasa-app.com` | API, including payment webhooks, proxied to `127.0.0.1:8080` |

The apps' release builds call `https://api.qasa-app.com`.

## The server

Contabo VPS, Debian 13, reached as `ssh qasa-api` (root, key only).

| What | Where |
| --- | --- |
| Settings and secrets | `/etc/qasa/qasa.env` (root:qasa, 640). Never commit or paste it |
| API, worker | `/usr/local/bin/qasa-{api,worker}`, systemd units `qasa-api`, `qasa-worker`, running as the `qasa` system user |
| Source and build cache | `/opt/qasa/src/server` |
| Caddy | `/etc/caddy/Caddyfile`, certificates managed by Caddy |
| PostgreSQL 18 | cluster `18/main`, local only; tuning in `/etc/postgresql/18/main/conf.d/10-qasa.conf` |
| Backups | `/var/backups/qasa`, nightly at 03:15 UTC by `qasa-backup.timer`, 14 days kept |

Useful commands:

```bash
ssh qasa-api 'journalctl -u qasa-api -f'          # API logs
ssh qasa-api 'systemctl status qasa-api qasa-worker caddy'
ssh qasa-api 'sudo -u postgres psql qasa'          # database shell
```

## DNS (Cloudflare)

The domain's nameservers point at Cloudflare. Three **A** records, all **DNS only** (grey
cloud), so Caddy can get its own certificates:

| Name | Content |
| --- | --- |
| `@` | `13.140.220.189` |
| `www` | `13.140.220.189` |
| `api` | `13.140.220.189` |

## Deploying

From your Mac, anywhere in the repository:

```bash
server/deploy/deploy.sh
```

It builds the landing page, copies it and `server/` to the server (never local `.env`
files), then runs `install.sh` there: compile, install the binaries, units and Caddyfile,
restart the API (which applies migrations), wait for `/health`, restart the worker and
reload Caddy. A first build takes several minutes; later ones are incremental.

## A new server

1. Debian 13, with your SSH key on root (`ssh-copy-id`), and an entry in `~/.ssh/config`.
2. `ssh <host> 'bash -s' < server/deploy/setup-debian.sh`. It turns off password login,
   enables the firewall (22, 80, 443), automatic security updates and fail2ban, installs
   PostgreSQL 18, Caddy and Rust, creates the `qasa` user, role and database, and writes
   `/etc/qasa/qasa.env` with new secrets. Safe to run again.
3. `QASA_HOST=<host> server/deploy/deploy.sh`.

**Keep a copy of `/etc/qasa/qasa.env`** in your password manager. Losing
`QASA_LICENSE_PRIVATE_KEY` signs everyone out of Premium until their licenses are reissued.
Its public key is the comment line in that file; release builds of the apps pin it as
`QASA_LICENSE_PUBLIC_KEY`.

## Backups

`qasa-backup.timer` dumps the database nightly. Those dumps are on the same disk, so they
do not survive losing the server. To also copy them off it: create a bucket in Contabo
Object Storage, `apt install rclone && rclone config` on the server, then
`systemctl edit qasa-backup.service` and add `Environment=QASA_BACKUP_REMOTE=<remote>:<bucket>`.
Try a restore once; the command is at the top of `server/deploy/backup-db.sh`.

## Turning on Wayl

Once Wayl approves the store, add to `/etc/qasa/qasa.env` and redeploy (or
`systemctl restart qasa-api qasa-worker`):

```bash
QASA_WAYL_API_KEY=<from dashboard.wayl.io>
QASA_WAYL_WEBHOOK_SECRET=<openssl rand -hex 32>
```

Webhooks go to `https://api.qasa-app.com/v1/payments/webhook/wayl`. Make one real
1,000 IQD purchase and watch it arrive (`journalctl -u qasa-api | grep -i wayl`).

## Not set up yet

- **Admin dashboard.** The admin API only answers the Tailscale range
  (`QASA_ADMIN_ALLOW_CIDRS=tailscale`), and there is no admin account in production yet.
- **Push notifications.** Without `QASA_FCM_*` in `/etc/qasa/qasa.env`, inbox rows are
  written but no push is sent.
- **Off-server backups** (above).
