# Qasa architecture

Qasa is a local-first password manager for iOS and Android, sold with Premium extras
(more devices, VPN, Wi‑Fi device sync) for 5,000 IQD a month or 50,000 IQD a year, and paid for through Iraqi wallets (FIB, FastPay,
SuperQi). This document describes the whole system as it is in the code today: the Rust
API and its worker, the node agent that runs on VPN servers, the two mobile apps and their
extensions, the admin dashboard, and the shared localization.

Work that is still open is tracked in [`docs/server-followups-plan.md`](docs/server-followups-plan.md).
Design rules for the apps are in [`docs/design.md`](docs/design.md), and user-facing flows
in [`docs/application-flow.md`](docs/application-flow.md).

---

## 1. The one rule: the vault never leaves the device

Every password, card and note is encrypted on the phone with keys derived from the
master password, and stored on the phone. The server never receives vault content, the
master password, or anything derived from it.

The server knows only what it needs to sell and meter features:

| The server stores | The server never sees |
| --- | --- |
| A random account id the app generated | The master password, keys, or vault items |
| Devices: name, platform, a coarse location hint, push token, locale | Email addresses (breach monitoring sends only a SHA-256 hash) |
| Subscriptions and payments | What the user searches for (search is a client-side blind index) |
| VPN sessions: node, assigned address, handshake time, byte counts | VPN traffic contents |
| Inbox notifications and read state | |

Consequences that show up everywhere below:

- **There is no login.** An account *is* a random UUID created on first launch. A
  device proves it belongs to the account with a license token the server signs.
- **Adding a device is pairing,** not signing in: the new device adopts the account id
  from an existing one (section 9.2).
- **Losing every device loses the vault.** There is no server-side recovery yet (pending
  design, todo `account-recovery`).

---

## 2. Repository layout

One repository for everything. Each part keeps its own native tooling: there is no
monorepo build system.

```
qasa/
├── server/                Rust workspace: API, worker, VPN node agent, breach CLI
│   ├── crates/            qasa-api, qasa-core, qasa-db, qasa-payments, qasa-push,
│   │                      qasa-breach, qasa-vpn, qasa-worker
│   ├── migrations/        Postgres schema (sqlx, applied on start)
│   ├── docker/            dev + prod compose, Caddyfile, Dockerfile
│   └── scripts/           integration tests, API smoke test
├── apps/
│   ├── ios/               Xcode project: app + AutoFill and notification extensions
│   ├── android/           Gradle project: Kotlin, Jetpack Compose
│   └── dashboard/         Vue 3 admin console (Vite, bun)
├── l10n/                  One string table → iOS and Android catalogs
├── docs/                  Plans, design system, flows
├── Makefile               Local dev shortcuts
└── ARCHITECTURE.md        This file
```

| Part | Language / stack | Build | Tests |
| --- | --- | --- | --- |
| `server/` | Rust 1.82+, Axum 0.8, sqlx 0.8, Postgres 16 | `cargo build` | `cargo test` (unit); `server/scripts/test-integration.sh` (DB + HTTP router) |
| `apps/ios/` | Swift, SwiftUI, SwiftData | Xcode / `xcodebuild -scheme qasa` | none yet |
| `apps/android/` | Kotlin 2.1, Compose, Hilt, Room; min SDK 26, target 35 | `./gradlew :app:assembleDebug` | `./gradlew :app:testDebugUnitTest` |
| `apps/dashboard/` | Vue 3.5, TypeScript, Vite 8, Tailwind 4, shadcn-vue | `bun run build` | type check in build |
| `l10n/` | Python 3 | `python3 l10n/generate.py` | `generate.py --check` |

---

## 3. System overview

```mermaid
flowchart LR
  subgraph Devices
    IOS["iOS app<br/>+ AutoFill extension<br/>+ notification extension"]
    AND["Android app<br/>+ Autofill / Credential Manager"]
  end
  ADMIN["Admin dashboard<br/>(Vue, staff only)"]

  subgraph Server["Server (one host, docker compose)"]
    CADDY["Caddy<br/>TLS :443"]
    API["qasa-api<br/>(Axum)"]
    WORKER["qasa-worker<br/>(interval jobs)"]
    PG[("Postgres 16")]
  end

  NODE["VPN node<br/>WireGuard + qasa-vpn-agent"]
  FCM["Firebase Cloud Messaging<br/>(APNs + Android)"]
  WALLET["FIB / FastPay / SuperQi<br/>(stubs today)"]

  IOS -- "HTTPS, license JWT" --> CADDY
  AND -- "HTTPS, license JWT" --> CADDY
  ADMIN -- "HTTPS, admin JWT<br/>(Tailscale only)" --> CADDY
  CADDY --> API
  API <--> PG
  WORKER <--> PG
  API -- push --> FCM
  WORKER -- push --> FCM
  FCM --> IOS
  FCM --> AND
  WALLET -- webhooks --> API
  NODE -- "heartbeat + peer stats,<br/>fetch desired peers" --> CADDY
  IOS -. "WireGuard tunnel (planned)" .-> NODE
  AND -. "WireGuard tunnel (planned)" .-> NODE
  IOS <-. "LAN pairing / vault transfer" .-> AND
```

- **Control plane vs data plane.** The API decides who may connect where. VPN traffic
  never passes through it: it goes from the device to a WireGuard node, and the node's
  agent keeps the WireGuard peer list in line with the API.
- **Everything client-facing goes through Caddy,** which terminates TLS. The API
  publishes no port of its own in production.

---

## 4. Server

### 4.1 Crates

The dependency graph is one-way: `qasa-api` and `qasa-worker` sit on top; `qasa-core`
knows nothing about HTTP or SQL.

| Crate | Kind | Responsibility |
| --- | --- | --- |
| `qasa-api` | binary | HTTP router, auth extractors, request limits, handlers. Router tests in `src/router_tests.rs` |
| `qasa-core` | library | Config, tiers and entitlements, Ed25519 license tokens, admin tokens, Argon2 password hashing |
| `qasa-db` | library | Connection pool, migrations, one module per table group under `src/repos/`, integration tests under `tests/` |
| `qasa-payments` | library | `PaymentGateway` trait (checkout, webhook, saved-method charge) and the three gateway stubs; renewal timing constants |
| `qasa-push` | library | FCM HTTP v1 delivery, per-device locale, dead-token cleanup |
| `qasa-breach` | library + CLI | Hash/prefix handling; `qasa-breach ingest` loads breach hashes |
| `qasa-vpn` | library + binary | WireGuard config types, key checks, IP pool, `wg` dump parsing and sync planning; the `qasa-vpn-agent` binary |
| `qasa-worker` | binary | Interval jobs (section 4.7) |

### 4.2 A request's path

```
Caddy (TLS, real client IP in X-Forwarded-For)
  → CORS (exposes X-Next-Before for paging)
  → admin network guard: /v1/admin/* only from QASA_ADMIN_ALLOW_CIDRS (Tailscale)
  → handler
      → extractor: LicenseAuth | AdminAuth | AdminWrite (section 4.3)
      → request limit check, where the route has one (section 4.10)
      → repo calls (qasa-db); multi-step changes run in one transaction
  → JSON response; errors map to {"error": code, "message"?} with a fitting status
```

The client IP is taken from `X-Forwarded-For` only when the TCP peer is in
`QASA_TRUSTED_PROXY_CIDRS` (the Caddy network); otherwise the peer address is used.

### 4.3 Identity and auth

| Who | Token | How it is checked |
| --- | --- | --- |
| A device | **License token**: Ed25519-signed JWT, 30 days. Claims: account id, device id, `jti`, tier, entitlements, `device_limit`, `breach_monitor_limit` | Signature, then the database: the device must still be linked, the account neither blocked nor deleted, the token issued after the device's last unlink, and its `jti` not revoked (`device::is_license_live`) |
| Staff | **Admin token**: HS256 JWT, 8 hours | Signature, then the database: the admin must exist, not be disabled, and not have logged this token out. The role is re-read on every request |
| A VPN node agent | Per-node agent token (random 256 bits, stored hashed) in `x-qasa-agent-token` | Constant-time compare with the stored hash |
| A payment gateway | Webhook signature (real gateways); stubs only settle in dev | `PaymentGateway::verify_webhook` |

- **Admin roles.** `admin` can change things. `viewer` reads only: every mutating admin
  route uses the `AdminWrite` extractor and returns 403 to viewers. Admins are managed with
  SQL.
- **Getting a license.** `POST /v1/devices/register` is unauthenticated by design: it takes
  the account id, a stable device fingerprint, a name and a platform, and returns a license
  token. Clients verify tokens offline with the public key from
  `/.well-known/qasa-license-key`.

### 4.4 Tiers and entitlements

Premium comes **only** from a live subscription row (`active` or `cancelled`, not yet
expired). The `accounts.tier` column is a cache, and a stale `premium` there grants
nothing.

| | Free | Premium |
| --- | --- | --- |
| Linked devices | 1 | 5 |
| Breached-password check (on device, section 4.11) | yes | yes |
| Email leak alerts | parked | parked (entitlement still issued: 10 email hashes) |
| VPN | no | yes, monthly data quota from the plan (100 GB default), then 5 Mbps |

- **When Premium lapses,** the account falls back to Free. Devices stay linked, but only
  the oldest (the primary) keeps getting licenses; the others get `device_limit_exceeded`
  until the user frees a slot or pays again. The worker labels such accounts `expired` for
  the dashboard.
- **Staff grants.** Premium granted by staff is an ordinary subscription row worth 0 IQD,
  with `granted_by`, `grant_days` and `grant_reason` set. Every rule above applies to it
  unchanged.

### 4.5 Data model

Migrations live in `server/migrations/` and are applied by `sqlx::migrate!` when the API
or the worker starts. The main tables:

| Area | Tables | Notes |
| --- | --- | --- |
| Accounts | `accounts` | `tier` (cache), `blocked_at`, `deleted_at` (a tombstone: a deleted id can never register again) |
| Devices | `devices`, `license_revocations` | `UNIQUE(account_id, fingerprint)`; `revoked_at` = unlinked; `tokens_valid_after` kills older tokens; `push_token`, `locale`, `notifications_enabled` |
| Billing | `plans`, `subscriptions`, `payments`, `payment_methods`, `webhook_events` | Price comes from `plans`. A payment is a `checkout` or a `renewal` and moves from `pending` to `success`, `failed` or `expired` (abandoned). `payment_methods` holds at most one live gateway token per account, for renewals. Webhook events record the verified status, amount and token |
| Breach | `breach_hashes`, `monitored_email_hashes` | Breach data keyed by 5-hex prefix; monitors are full SHA-256 hashes of emails |
| VPN | `vpn_nodes`, `vpn_sessions`, `vpn_usage` | Unique indexes on live sessions per device, per (node, address) and per (node, key). `last_handshake_at` comes from the node |
| Inbox | `notifications`, `notification_receipts` | One receipt per account per notification, carrying `read_at`, `pushed_at` and `deleted_at` (per-user delete) |
| Staff | `admin_users`, `admin_token_revocations`, `admin_audit_log` | Every admin change is written to the audit log |
| Limits | `rate_limit_buckets` | UNLOGGED, fixed windows, shared by every API process |

### 4.6 API surface

The complete endpoint table is in [`server/README.md`](server/README.md#phase-1-endpoints),
and `server/crates/qasa-api/src/main.rs` is authoritative. By area:

| Area | Endpoints (prefix `/v1`) | Auth |
| --- | --- | --- |
| Devices and license | `devices/register`, `devices/link-check`, `devices`, `devices/{id}`, `devices/me`, `devices/others`, `devices/prefs`, `license` | none for register/link-check; license otherwise |
| Account | `DELETE account` (with `confirm_account_id`) | license |
| Plans and billing | `pricing`, `subscription`, `subscription/auto-renew`, `subscription/cancel`, `DELETE payment-method`, `payments`, `payments/intents`, `payments/{id}`, `payments/webhook/{fib,fastpay,superqi}` | none for pricing/webhooks; license otherwise |
| Breach | `breach/prefix/{prefix}`, `breach/monitor` (+ `/{id}`) | license; Premium to look up or register |
| VPN | `vpn/nodes`, `vpn/connect`, `vpn/disconnect`, `vpn/status` | license; Premium to connect |
| Inbox | `notifications`, `notifications/unread-count`, `notifications/read-all`, `notifications/{id}`, `notifications/{id}/read` | license |
| VPN agent | `internal/vpn/nodes/{id}/heartbeat`, `internal/vpn/nodes/{id}/peers` | agent token |
| Admin | `admin/auth/*`, `admin/overview`, `admin/analytics`, `admin/timeseries`, `admin/users`, `admin/accounts/{id}` (+ block, devices, grants), `admin/grants`, `admin/payments` (+ `/{id}`), `admin/webhooks`, `admin/cancellations`, `admin/plans`, `admin/notifications`, `admin/vpn/nodes` (+ edit, retire, agent-token), `admin/licenses/revocations`, `admin/audit`, `admin/telemetry` | admin token; writes need role `admin` |

Paging is cursor-based everywhere: `?limit=&before=<timestamp>`, with `next_before` in the
response. The admin users list keeps a bare-array body and returns its cursor in the
`X-Next-Before` header.

### 4.7 Worker jobs

`qasa-worker` runs in production (or with `QASA_WORKER_ENABLE=1`). Each job is a
`tokio` interval loop; a failed run is logged, and the next run is the retry.

| Job | Every | What it does |
| --- | --- | --- |
| `expire_subscriptions` | 15 min | Expire ended subscriptions, mark accounts lapsed, end VPN sessions of non-subscribers |
| `expire_abandoned_payments` | 5 min | `pending` checkouts older than 60 min become `expired` (a late confirmation still settles) |
| `broadcast_pushes` | 1 min | Push admin broadcasts to each device in its language, once per receipt, for up to 24 h |
| `webhook_retry` | 1 min | Re-settle verified webhook events still pending, through the same settlement code as the API |
| `offline_vpn_nodes` | 1 min | Nodes with no heartbeat for 2 min go `offline` |
| `stale_vpn_sessions` | 1 min | End sessions with no handshake for 5 min (or none within 10 min of connecting) |
| `breach_alert_scan` | not scheduled | Parked with email leak alerts. Would match monitored hashes against breach data and push Premium accounts (at most weekly per hash) |
| `subscription_expiry_warnings` | 1 h | "Premium ends in 3 days" push, only to subscriptions that will not renew by themselves |
| `charge_renewals` | 15 min | Charge saved methods for auto-renew subscriptions ending within 24 h (section 4.8) |
| `subscription_renew_failed_notices` | 1 h | "We couldn't renew" push, once, after the worker's attempts are used up or the saved method was rejected |
| `purge_license_revocations` | 1 day | Drop expired token revocations, admin and device |
| `purge_rate_limit_buckets` | 5 min | Drop rate-limit windows older than a day |

### 4.8 Payments

1. **Intent.** The app calls `POST /payments/intents` with a gateway and a plan. The
   amount comes from `plans`; an `amount_iqd` that doesn't match is refused, and an
   `idempotency_key` makes retries safe. The server creates the gateway order and
   returns a deep link to the wallet app.
2. **Pay.** The user pays in the wallet app. The app polls `GET /payments/{id}`.
3. **Settle.** The gateway's webhook is verified, recorded in `webhook_events`, and passed
   to **`settle_payment`** (`qasa-db/src/repos/settlement.rs`), the only code path that
   turns money into an entitlement. In one transaction, with the payment row locked, it
   checks the gateway and the amount, then grants the subscription and marks the payment
   paid. Time stacks: paying early extends from the current end date. The worker's retry
   job uses the same function.
4. **Refresh.** The app sees `success` and refreshes its license, which now carries
   Premium.

**Automatic renewals.**

- **Consent and the saved method.** A checkout paid with `auto_renew` on asks the gateway
  to save the wallet (`create_intent(…, save_method)`). The token arrives with the
  successful webhook, and `settle_payment_with_token` stores it in `payment_methods` in the
  same transaction as the grant, but only if the payment had auto-renew on. A newer
  consented checkout replaces it. `DELETE /v1/payment-method` forgets it; deleting the
  account does too.
- **Will it renew?** Only when auto-renew is on, a live method exists, and its gateway
  `supports_recurring()`. `GET /v1/subscription` reports this as
  `renewal: {will_renew, gateway, amount_iqd}`, which is what the apps show under the
  toggle. Everything else lapses at its end date and gets the 3-day reminder.
- **Charging.** `charge_renewals` picks subscriptions ending within
  `RENEWAL_LEAD_HOURS` (24). It counts the attempt first (`renewal::claim_attempt`, a
  compare-and-swap, so two runs cannot both charge), inserts a `renewal` payment at the
  plan's current price, then calls `charge_saved` with the payment id as the reference. A
  synchronous answer goes straight to `settle_payment`; a pending one waits for the webhook
  like a checkout. Paid time stacks onto the old end, and the new period keeps auto-renew.
- **Failures.** A decline is retried after `RENEWAL_RETRY_HOURS` (6), up to
  `RENEWAL_MAX_ATTEMPTS` (3), all before the end date. A rejected token is dropped at once.
  Either way the customer gets one `SUB_RENEW_FAILED` push and pays by hand; turning
  auto-renew off and on again starts a fresh round. An inactive plan is not renewed.

The three gateways are stubs that settle, and charge saved methods, only in dev. The stub
mints a token for every successful webhook; tokens starting `stub-decline` or
`stub-revoked` exercise the failure paths. Real integrations, including which of FIB,
FastPay and SuperQi can charge a saved wallet at all, and refunds are pending (todo
`payments-live`). In prod no gateway `supports_recurring()` yet, so nothing is charged and
every subscriber gets the 3-day reminder.

### 4.9 Notifications and push

- **Targeted notices** (breach alert, Premium ending, VPN data at 80% / used up) are
  created for one account with a dedupe key and pushed straight away
  (`PushService::notify_account`).
- **Broadcasts** from the dashboard target `all`, `subscribers` or `free`. The API writes
  inbox receipts for that audience only; the worker pushes them.
- **Delivery.** Both platforms go through FCM HTTP v1 with one service account; APNs keys
  live in Firebase. The text is picked by the device's stored locale (`en`, `ckb`, `ar`),
  falling back to English when a translation is missing. FCM `UNREGISTERED` or
  invalid-token errors clear the stored token. Without FCM credentials the inbox still
  works and pushes are only logged.

### 4.10 Request limits

Counted in Postgres (`rate_limit_buckets`), so every API process shares them. The values
are in `server/crates/qasa-api/src/limits.rs`:

| Limit | Allowance |
| --- | --- |
| Breach prefix lookups | 60 per hour per account |
| Admin login | 10 per email, 30 per IP, every 15 min |
| Device register | 30 per account, 600 per IP, every 10 min |
| Link-check | 120 per IP every 10 min |

The per-IP allowances are high on purpose: mobile carriers put many users behind one
CGNAT address.

### 4.11 Breached passwords and email leak alerts

**Breached-password check (live, free).** Both apps check saved login passwords against
HIBP Pwned Passwords, with no Qasa server involved (`PwnedPasswordsService` on iOS and
Android):

- Each password is SHA-1 hashed on the device. Only the first 5 hex characters go to
  `https://api.pwnedpasswords.com/range/{prefix}` (with `Add-Padding: true`), and the
  returned suffixes are matched on the device. The service is free and needs no key.
- Results feed the password health report (a "Breached" count, 15 points off the score
  per breached login), the Home card, and the Alerts tab.
- A failed check (usually offline) is shown as such; the apps never show a partial
  "all clear".

**Email leak alerts (parked, todo `email-leak-alerts`).** No affordable source of
breached email data exists yet, so the apps no longer reach these screens and the worker
doesn't schedule the scan. The server endpoints and tables below stay in place.

- **Registering.** The app hashes each email it watches (SHA-256 of the trimmed,
  lowercased address) and registers the hash. The server keeps hashes, never addresses.
- **Alerts.** The worker matches stored hashes against `breach_hashes` and pushes an
  alert.
- **Ad-hoc checks** use the k-anonymity prefix lookup: the app sends 5 hex characters and
  compares the returned suffixes locally.
- **No per-account salt.** A salt would make server-side matching impossible; that
  trade-off is decided.
- **Where breach data comes from is still open** (todo `breach-data-source`). The ingest
  CLI is ready.

### 4.12 VPN

```
device ──(1) POST /vpn/connect {client_public_key, node_id?}──▶ API
       ◀─(2) WireGuard config: server key, endpoint, client address──┘
node agent ──(3) GET /internal/vpn/nodes/{id}/peers──▶ API: live sessions on this node
           ──(4) wg set wg0 peer <key> allowed-ips <addr>/32 (adds / removes)
device ════(5) WireGuard tunnel════▶ node
node agent ──(6) POST …/heartbeat {cpu, mem, peers: [handshake, rx, tx]}──▶ API
           API: record handshake → tunnel_up; meter bytes → quota, 80% / throttle pushes
```

- **Keys.** The device generates its WireGuard keypair and keeps the private key. The
  public key is required in production.
- **Allocation is race-safe.** Unique indexes on live sessions make a losing connect retry
  with the next free address.
- **Sessions end** when the node stops seeing handshakes. Unlinking, blocking, lapsing and
  account deletion also end them, and the next agent sync removes the peer from the node.
- **Nodes** are managed from the dashboard (create, drain, retire, rotate the agent token).
  The agent's `--apply` flag is required before it changes WireGuard; without it, it only
  logs what it would change.
- **Pending** (todo `vpn-tunnel`): the device tunnels themselves (an iOS
  `NetworkExtension` packet tunnel and Android `VpnService`), and node-side rate limiting
  for throttled peers.

### 4.13 Deployment

`server/docker/docker-compose.prod.yml` runs Postgres, the API, the worker and Caddy on a
fixed internal network (`172.30.0.0/24`):

- **Caddy** gets the certificate for `QASA_PUBLIC_HOST`, proxies to `api:8080`, blocks
  `/metrics` from the public internet, and sets HSTS and other security headers.
- **Required secrets.** The stack refuses to start without `POSTGRES_PASSWORD`,
  `QASA_ADMIN_JWT_SECRET` and `QASA_LICENSE_PRIVATE_KEY`. `DATABASE_URL` has no default in
  prod.
- **The admin surface** is restricted to `QASA_ADMIN_ALLOW_CIDRS` (default `tailscale`).
  How admins reach it through Caddy over the tailnet is still to be settled at deploy time.
- **VPN nodes** are separate hosts running WireGuard and `qasa-vpn-agent`.

---

## 5. Client crypto (iOS and Android, identical)

```
master password
  └─ PBKDF2-HMAC-SHA256, 310,000 iterations, 32-byte random salt
       └─ root key (32 bytes)
            ├─ SHA-256 verifier          → stored, to check the password
            └─ HKDF-SHA256
                 ├─ info "qasa-enc"     → AES-256-GCM data key
                 └─ info "qasa-search"  → blind-index key
```

- **Items are stored in two encrypted parts.** A small envelope (title, subtitle,
  collection) is what the list decrypts; the full payload is decrypted only when an item
  is opened.
- **Search is a blind index.** Search terms are normalised and hashed with the search key
  into tokens. Queries are hashed the same way and matched against those tokens, so
  plaintext search data is never stored.
- **Changing the master password** re-encrypts every item and rebuilds every search
  token.
- **Shared derivation code.** The iOS extensions derive keys with `SharedAutofill/`, which
  must match the app's `MasterKeyDeriver` byte for byte. Android's is in
  `vault/crypto/MasterKeyDeriver.kt`.
- **Item types:** login, secure note, payment (card or bank account), personal info, ID
  document.
- **Auto-lock** after 1–60 minutes of inactivity, on both platforms.

PBKDF2 rather than Argon2id is a known divergence from the original blueprint.

---

## 6. iOS (`apps/ios/`)

| Target | What it is |
| --- | --- |
| `qasa` (`qasa_ios/`) | The SwiftUI app. Vault in SwiftData; keys and the account id in the Keychain, in an access group shared with the extensions |
| `QasaCredentialProvider` | AutoFill credential provider extension (`authentication-services-credential-provider-ui`), with Face ID / Touch ID unlock |
| `QasaPaymentNotification` | Notification content extension that shows card details from a payment notification in an ephemeral drawer |
| `SharedAutofill/` | Code shared by the app and the extensions: key derivation, vault crypto, Keychain, the AutoFill index, the app group |

Inside `qasa_ios/`:

| Folder | Contents |
| --- | --- |
| `Views/` | Screens (Home, vault, generator, breach, VPN, notifications, settings, pairing, Premium upgrade) |
| `Services/` | Vault, Network (`QasaAPIClient`), License (offline token verification), Device registration, Payments, Breach, VPN, Notifications, Sync (LAN, Bonjour) |
| `Models/`, `Components/`, `Theme/` | Data model and the design-system building blocks |
| `Config/QasaConfig.swift` | Endpoint URLs. The API base URL comes from the build setting `QASA_API_URL`: localhost in Debug, `https://api.qasa.app` in Release |

---

## 7. Android (`apps/android/`)

A Compose app on Hilt (dependency injection), Room (encrypted vault rows and search
tokens) and kotlinx.serialization. Package `alibarznji.qasa`:

| Package | Contents |
| --- | --- |
| `vault/` | Repository, session (unlocked state), crypto, Room database, CSV import/export, biometric gate |
| `credentials/` | `QasaAutofillService` (Android Autofill) and `QasaCredentialProviderService` (Credential Manager). Autofill requires the app to be unlocked |
| `network/`, `config/` | OkHttp API client (logging only in debug builds), API models, endpoint URLs (`10.0.2.2` in debug, `https://api.qasa.app` in release) |
| `license/`, `device/` | Token verification and device registration |
| `payments/`, `breach/`, `vpn/`, `notifications/` | Feature services matching the iOS ones |
| `push/` | FCM token handling, `DevicePrefsSync` (all prefs updates), the Android 13+ notification-permission prompt |
| `sync/` | LAN vault sync (a stub; the real protocol is section 1 of the plan) |
| `screens/`, `theme/` | Compose UI and the design system |

---

## 8. Admin dashboard (`apps/dashboard/`)

- **Stack and structure.** Vue 3 with plain `ref`/`computed` state and no store library.
  All API calls are in `src/api/admin.ts`, through `src/api/client.ts`.
- **Session.** The admin token, role and email are kept in `localStorage`.
  `canWrite()` hides write controls for viewers, a 401 sends the user back to login, and
  sign-out calls the logout endpoint.
- **Demo mode.** Without `VITE_QASA_API_URL`, every screen runs on `src/data/mock.ts`, so
  the console works offline.
- **Screens:**
  - Overview: KPIs, growth and gateway-mix charts from `/admin/timeseries`
  - Analytics
  - Users, plus the account page: devices, subscriptions, grants, block, audit trail
  - Payments: filters, real webhook payloads
  - Plans
  - Cancellations
  - Notifications: broadcast with an audience picker
  - VPN nodes: create, edit, drain, retire, rotate token
  - Telemetry: the staff audit feed
- The dashboard is meant to be reached over Tailscale only (section 4.13).

---

## 9. Key flows

### 9.1 First launch

1. **Create the vault.** The app generates an account id and the user sets a master
   password. Keys are derived and the vault is created locally.
2. **Register the device.** `POST /devices/register` creates the account as Free if it's
   new, links the device and returns a license token. The app stores the token and checks
   it offline against the public key.
3. **Push setup.** With notification permission, the app sends its FCM token and locale
   with `PATCH /devices/prefs`.

### 9.2 Adding a second device (pairing)

The new device needs the account id from an existing one. Today the user enters it, or
uses a short pairing code that only works on the same device. The planned design (plan
section 1) is a LAN handshake: an Argon2id-stretched code, a shared Rust crate in both
apps, and the vault transferred over the local network.

On the server side:
1. `POST /devices/link-check` says whether the account can take another device. Unknown
   accounts get 404, and Free accounts get `premium_required`.
2. `register` links the device.

### 9.3 Upgrading

Pricing → payment intent → wallet app → webhook → `settle_payment` → license refresh →
Premium features unlock (section 4.8).

### 9.4 Lapse, unlink, block, delete

- **Lapse.** The account drops to Free, and only the primary device keeps a license.
- **Unlink** (`DELETE /devices/{id}`, `/me` or `/others`). Tokens die at once; the push
  token is cleared, the VPN session ends, and the node drops the peer.
- **Block** (staff). Every token stops working, the device can't register, and VPN
  sessions end. Unblocking restores tokens that haven't expired.
- **Delete** (`DELETE /account`). Devices, monitors, VPN data, notifications and receipts
  are removed. Payments and subscriptions stay as financial records. The account id
  becomes a tombstone that can never register again (410 `account_deleted`).

---

## 10. Localization (`l10n/`)

- **One table, three languages.** `l10n/strings.json` holds every UI string in English,
  Arabic and Sorani Kurdish (`en`, `ar`, `ckb`). `python3 l10n/generate.py` writes the
  iOS catalogs (app and both extensions) and Android's `values`, `values-ar` and
  `values-ckb`.
- **Never edit the generated files.** `generate.py --check` fails if they have drifted
  from the source.
- **Format rules.** Placeholders are written iOS-style (`%@`, `%lld`) and converted for
  Android. A row can carry an explicit `"android"` resource name when it can't be derived
  from the key.
- **Server-side copy.** Push and inbox text (breach alerts, Premium reminders, VPN quota,
  pricing notices) lives in `server/crates/qasa-db/src/repos/push_copy.rs` and
  `pricing.rs`, also in all three languages.

---

## 11. Security model in one page

| Threat | Defence |
| --- | --- |
| Server breach | No vault data on the server. Email monitoring keeps hashes only. Payments hold no card data (wallets handle it) |
| Stolen or lost phone | Vault encrypted with master-password-derived keys, Keychain/Keystore protection, auto-lock, biometric unlock. Staff or the user can unlink the device remotely, which kills its tokens at once |
| Forged licenses | Ed25519 signatures plus a database liveness check on every authenticated request |
| Forged payments | Only verified webhooks settle; the price comes from the plan; amount and gateway are checked in one locked transaction. Stubs cannot settle in production |
| Admin compromise | Tailscale-only admin surface, DB-checked roles, login rate limits, logout, audit log of every change. 2FA is pending (no OTP method yet) |
| Abuse and floods | Shared rate limits on login, register, pairing and breach lookups |
| VPN abuse | Premium-only, one live session per device, quota and throttling, node tokens rotated from the dashboard |

---

## 12. Local development

```bash
make up        # Postgres in Docker (localhost:5433)
make env       # create server/.env from the example
make api       # API on :8080 (dev seeds three VPN nodes and the admin user from .env)
make worker    # background jobs (set QASA_ENV=prod or QASA_WORKER_ENABLE=1)
make dash      # dashboard (Vite); set VITE_QASA_API_URL in apps/dashboard/.env
make android   # build and install the debug app on a device or emulator
make smoke     # API smoke test
```

- **iOS:** open `apps/ios/qasa.xcodeproj`. The simulator reaches the local API at
  `localhost:8080`.
- **Server tests:** `cd server && ./scripts/test-integration.sh` runs the DB and router
  tests in a throwaway Postgres. Set `QASA_POSTGRES_PORT=5544` if the dev database already
  holds 5433.
- **Strings:** after editing `l10n/strings.json`, run `python3 l10n/generate.py` and
  commit the outputs.
