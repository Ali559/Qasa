# Qasa — Architecture Reference

Local-first password manager. Vault lives on-device, encrypted. Server meters devices and sells features. No vault content ever leaves the phone.

Brand note: code says **Qasa**. The blueprint in `.cursor/skills/qasa-app-blueprint/blueprint.md` still says **Kaso** and `api.kaso.app`. Naming drift — treat blueprint domain names as stale.

---

## 1. Stack

| Component | Path | Stack |
| --- | --- | --- |
| Server | `server/` | Rust 1.78+, Axum 0.8, sqlx 0.8, 8-crate workspace |
| Database | `server/docker/` | PostgreSQL 16, Redis 7 (Redis unused by code) |
| iOS app | `qasa/qasa_ios/` | Swift 5, SwiftUI, SwiftData, 3 Xcode targets |
| iOS extensions | `qasa/QasaCredentialProvider/`, `qasa/QasaPaymentNotification/`, `qasa/SharedAutofill/` | ASCredentialProvider, PKPayment, shared crypto |
| Android app | `qasa_android/` | Kotlin 2.1, Jetpack Compose, Hilt, Room, min SDK 26 / target 35 |
| Admin dashboard | `dashboard/` | Vue 3.5, TS 6.0, Vite 8, Tailwind v4, shadcn-vue (reka-ui) |
| Localization | `l10n/` | Python 3, `strings.json` → 6 generated files |
| Dev glue | `Makefile`, `commit` | make targets; `./commit to <repo> -m` |

Nested git repos: `dashboard/`, `server/`, `qasa/`. `qasa_android/` and `l10n/` are not repos — `./commit` cannot commit them.

---

## 2. Server workspace

Root manifest `server/Cargo.toml:1`. Dependency direction is strictly one-way, no cycles.

| Crate | Type | Role | Entrypoint |
| --- | --- | --- | --- |
| `qasa-api` | bin | HTTP API, router, auth extractors, CORS/Tailscale guard, Prometheus | `crates/qasa-api/src/main.rs` |
| `qasa-core` | lib | Domain: config, Ed25519 license issuer, HS256 admin issuer, Argon2, tiers, masked IDs | `crates/qasa-core/src/lib.rs` |
| `qasa-db` | lib | sqlx pool + 13 raw-SQL repos + row models | `crates/qasa-db/src/lib.rs` |
| `qasa-payments` | lib | `PaymentGateway` trait, 3 Iraqi gateway stubs | `crates/qasa-payments/src/lib.rs` |
| `qasa-breach` | lib + bin | Prefix normalization, hash split, in-memory rate limit, ingest CLI | `crates/qasa-breach/src/main.rs` |
| `qasa-vpn` | lib + bin | WireGuard config types, agent-token hashing, CIDR IP pool, heartbeat agent | `crates/qasa-vpn/src/bin/agent.rs` |
| `qasa-push` | lib | FCM HTTP v1 delivery for iOS + Android, inbox row creation | `crates/qasa-push/src/lib.rs` |
| `qasa-worker` | bin | 8 interval background jobs | `crates/qasa-worker/src/main.rs` |

`qasa-api` → core, db, payments, breach, vpn, push. `qasa-worker` → core, db, payments, push. `qasa-push` → qasa-db.

### qasa-api modules

`main.rs:1` declares: `admin, analytics, auth, breach, devices, entitlements, error, health, license, metrics, metrics_handler, middleware, notifications, payments, pricing, state, subscriptions, vpn, vpn_agent`.

Router built in `build_router` — `server/crates/qasa-api/src/main.rs:111`. Route table at lines `140-183`. Layers applied `185-199`.

---

## 3. HTTP API surface

39 route registrations, 41 method-path pairs. Source of truth is `main.rs:140`; `server/README.md:53` is stale and omits 9 endpoints.

### Health / meta

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| GET | `/health` | `health.rs:8` | none |
| GET | `/.well-known/qasa-license-key` | `license.rs:10` | none |
| GET | `/metrics` | `metrics_handler.rs:3` | none, **prod only** |

### Devices & licensing

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| POST | `/v1/devices/register` | `devices.rs:55` | **none** |
| POST | `/v1/devices/link-check` | `devices.rs:184` | **none** |
| GET | `/v1/devices` | `devices.rs:139` | License |
| DELETE | `/v1/devices/others` | `devices.rs:163` | License |
| DELETE | `/v1/devices/{device_id}` | `devices.rs:148` | License |
| GET | `/v1/license` | `license.rs:17` | License |
| PATCH | `/v1/devices/prefs` | `notifications.rs:41` | License |

Axum 0.8 `{param}` brace syntax throughout. `register` is unauthenticated by design — account IDs are unguessable client-generated UUIDs.

### Subscription

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| GET | `/v1/subscription` | `subscriptions.rs:12` | License |
| PATCH | `/v1/subscription/auto-renew` | `subscriptions.rs:43` | License |
| POST | `/v1/subscription/cancel` | `subscriptions.rs:87` | License |

### Payments

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| POST | `/v1/payments/intents` | `payments.rs:34` | License |
| GET | `/v1/payments/{payment_id}` | `payments.rs:80` | License + owner check |
| POST | `/v1/payments/webhook/fib` | `payments.rs:106` | **none** |
| POST | `/v1/payments/webhook/fastpay` | `payments.rs:114` | **none** |
| POST | `/v1/payments/webhook/superqi` | `payments.rs:122` | **none** |

All three webhooks converge on `payments::handle_webhook` — `payments.rs:130`. Idempotent: early-return if payment already `success`.

### Breach (Premium-gated)

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| GET | `/v1/breach/prefix/{prefix}` | `breach.rs:16` | License + Premium, 60/hr |
| POST | `/v1/breach/monitor` | `breach.rs:53` | License + Premium, max 10 |
| GET | `/v1/breach/monitor` | `breach.rs:109` | License + Premium |
| DELETE | `/v1/breach/monitor` | `breach.rs:139` | License + Premium |

### VPN

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| POST | `/v1/vpn/connect` | `vpn.rs:13` | License + Premium |
| POST | `/v1/vpn/disconnect` | `vpn.rs:126` | License only — no Premium |
| GET | `/v1/vpn/status` | `vpn.rs:143` | License only |
| POST | `/v1/internal/vpn/nodes/{node_id}/heartbeat` | `vpn_agent.rs:11` | `x-qasa-agent-token` |

`disconnect` and `status` skip the Premium gate deliberately so lapsed users can tear down.

### Pricing & notifications

| Method | Path | Handler | Auth |
| --- | --- | --- | --- |
| GET | `/v1/pricing` | `pricing.rs:11` | **none** |
| GET | `/v1/notifications` | `notifications.rs:12` | License, limit 50 |
| GET | `/v1/notifications/{id}` | `notifications.rs:21` | License, marks read |

### Admin (HS256 JWT except login)

| Method | Path | Handler |
| --- | --- | --- |
| POST | `/v1/admin/auth/login` | `admin.rs:20` |
| GET | `/v1/admin/overview` | `admin.rs:87` |
| GET | `/v1/admin/analytics` | `analytics.rs:10` |
| GET | `/v1/admin/users` | `admin.rs:43` |
| GET | `/v1/admin/payments` | `admin.rs:118` |
| GET | `/v1/admin/cancellations` | `admin.rs:140` |
| GET | `/v1/admin/webhooks` | `admin.rs:162` |
| GET | `/v1/admin/vpn/nodes` | `admin.rs:170` |
| GET | `/v1/admin/telemetry` | `admin.rs:204` |
| GET | `/v1/admin/plans` | `pricing.rs:18` |
| PUT | `/v1/admin/plans` | `pricing.rs:53` |
| GET | `/v1/admin/notifications` | `notifications.rs:114` |
| POST | `/v1/admin/notifications` | `notifications.rs:84` |

### Errors

`ApiError` — `crates/qasa-api/src/error.rs:34`. Variants: `Core(CoreError)`, `Internal`, `RateLimited` (429), `SubscriptionRequired` (403 `subscription_required`). Domain errors in `crates/qasa-core/src/error.rs:4`.

Device-limit rejection is a 403 with structured body: `devices_used`, `device_limit`, `devices[]` — `devices.rs:92`.

---

## 4. Data model

7 migrations in `server/migrations/`, embedded at compile time via `sqlx::migrate!` — `crates/qasa-db/src/pool.rs:14`. Applied automatically on API and worker start.

| # | File | Adds |
| --- | --- | --- |
| 1 | `20260826000000_initial.sql` | accounts, devices, subscriptions, payments, webhook_events, license_revocations, entitlement_overrides, admin_users, admin_audit_log, vpn_nodes, vpn_sessions, breach_hashes, 2 matviews |
| 2 | `20260826000001_vpn_metrics.sql` | node CPU/mem/bandwidth, `ip_pool_cidr`, session indexes |
| 3 | `20260826000002_webhook_retries.sql` | `webhook_events.retry_count` |
| 4 | `20260826000003_subscription_management.sql` | `auto_renew`, `cancelled_at`, `cancellation_reason`, payment `plan_id` |
| 5 | `20260828000001_roadmap_features.sql` | plans, notifications, notification_receipts, vpn_usage, app_settings |
| 6 | `20260828000002_push_hooks.sql` | `devices.locale/push_token/notifications_enabled`, `accounts.vault_initialized` |
| 7 | `20260829000001_device_location.sql` | `devices.location` — untracked in git |

### Core tables

| Table | Key columns |
| --- | --- |
| `accounts` | `id UUID PK`, `public_id TEXT UNIQUE`, `tier` (default `free`), `recovery_email_hash BYTEA`, `vault_initialized BOOL` |
| `devices` | `id PK`, `account_id → accounts CASCADE`, `fingerprint`, `device_name`, `platform`, `license_jti`, `last_seen_at`, `revoked_at`, `locale`, `push_token`, `notifications_enabled`, `location`. **`UNIQUE(account_id, fingerprint)`** |
| `subscriptions` | `id PK`, `account_id`, `status`, `plan_id`, `amount_iqd`, `auto_renew`, `started_at`, `expires_at`, `cancelled_at`, `cancellation_reason`, `expiry_warned_at`, `renew_failed_notified_at` |
| `payments` | `id PK`, `account_id`, `subscription_id`, `gateway`, **`gateway_order_id UNIQUE`**, `amount_iqd`, `status`, `deep_link`, **`idempotency_key UNIQUE`**, `plan_id`, `auto_renew`, `completed_at` |
| `webhook_events` | `id PK`, `gateway`, `gateway_order_id`, `payload JSONB`, `signature_valid BOOL`, `processed BOOL`, `error_message`, `retry_count`, `received_at` |
| `license_revocations` | `jti TEXT PK`, `account_id`, `reason`, `revoked_at` — 31-day purge |
| `vpn_nodes` | `id PK`, `name`, `city`, `public_ip INET`, `wg_public_key`, `endpoint_port` (51820), `max_peers`, `active_peers`, `status`, `agent_token_hash`, `last_heartbeat`, `cpu_percent`, `memory_percent`, `memory_label`, `bandwidth_label`, `service_name`, `ip_pool_cidr CIDR` |
| `vpn_sessions` | `id PK`, `account_id`, `device_id`, `node_id`, `client_public_key`, `assigned_ip INET`, `connected_at`, `disconnected_at`, `bytes_tx`, `bytes_rx`. `disconnected_at IS NULL` = active |
| `breach_hashes` | **`prefix CHAR(5)`, `suffix TEXT`, `source`, `PK(prefix, suffix)`** |

### Feature tables

| Table | Key columns |
| --- | --- |
| `plans` | `id TEXT PK`, `title`, `interval` CHECK (`monthly`\|`yearly`), `amount_iqd`, `days`, `vpn_quota_gb` (100), `active`, `sort_order`, `features_json` |
| `notifications` | `id PK`, `title_en/_ckb/_ar`, `body_en/_ckb/_ar`, `audience` (default `all`), `kind`, `dedupe_key`, `created_by → admin_users` |
| `notification_receipts` | `PK(notification_id, account_id)`, `device_id` SET NULL, `read_at`, `pushed_at` |
| `vpn_usage` | `account_id PK CASCADE`, `period_start` (auto month rollover), `bytes_used`, `throttled`, `warned_80` |
| `monitored_email_hashes` | 64-hex full SHA-256, index `idx_monitored_email_hash` |
| `app_settings` | `key PK`, `value_json`. Seeded `vpn_throttle_mbps='5'`, `admin_tailscale_only='false'`. **No code reads it** |

Seeded plans — `20260828000001_roadmap_features.sql:17`:

| id | title | interval | IQD | days | VPN GB |
| --- | --- | --- | --- | --- | --- |
| `premium_monthly` | Premium Monthly | monthly | 10,000 | 30 | 100 |
| `premium_yearly` | Premium Yearly | yearly | 100,000 | 365 | 100 |

### Materialized views

| View | Columns | Source |
| --- | --- | --- |
| `mv_revenue_daily` | `day`, `gateway`, `total_iqd`, `tx_count` | `payments WHERE status='success'` |
| `mv_active_subscribers` | `paid_users` | unique index present → `CONCURRENTLY` works |

### Relationships

`accounts` 1—N `devices`, `subscriptions`, `payments`, `vpn_sessions`, `notification_receipts`, `license_revocations`. 1—1 `vpn_usage`. 1—N `monitored_email_hashes`.
`devices` 1—N `vpn_sessions`. `vpn_nodes` 1—N `vpn_sessions`. `subscriptions` 1—N `payments`.
`notifications` N—M `accounts` via `notification_receipts`.
`breach_hashes` standalone — no FK, cross-referenced by prefix in queries only.

### Dead schema

`entitlement_overrides`, `admin_audit_log`, `app_settings` — created, never read or written by Rust code.

---

## 5. Worker

`qasa-worker` refuses to start outside production unless `QASA_WORKER_ENABLE=1|true` — `worker/src/main.rs:18`. Runs migrations on boot. Spawns 8 `tokio::time::interval` loops — `main.rs:36-91`. Errors are logged, never fatal. Then blocks on `std::future::pending()` — `main.rs:93`.

| Job | Interval | Function | Work |
| --- | --- | --- | --- |
| `expire_subscriptions` | 15 min | `jobs.rs:11` | mark lapsed subs expired; downgrade `accounts.tier` to `expired`; disconnect VPN for non-subscribers |
| `refresh_materialized_views` | 5 min | `jobs.rs:19` | `REFRESH MATERIALIZED VIEW CONCURRENTLY` both MVs — errors swallowed by `.ok()` |
| `webhook_retry` | 60 s | `jobs.rs:113` | unprocessed + signature_valid + `retry_count < 5` + >1 min old, limit 50 |
| `stale_vpn_sessions` | 10 min | `jobs.rs:25` | disconnect sessions older than **5 min**, decrement `active_peers` |
| `purge_license_revocations` | 24 h | `jobs.rs:33` | delete revocations older than 31 days |
| `breach_alert_scan` | 10 min | `jobs.rs:43` | join monitored hashes vs `breach_hashes`, require live sub, skip if alerted <7 days, limit 100, push with daily dedupe |
| `subscription_expiry_warnings` | 60 min | `jobs.rs:66` | `auto_renew=false`, unwarned, expiring ≤3 days |
| `subscription_renew_failed_notices` | 60 min | `jobs.rs:90` | `auto_renew=true`, expiring ≤24 h, no successful payment in 2 days |

Dedupe key patterns: `breach:{acct}:{hash12}:{YYYY-MM-DD}`, `sub_expiring_3d:{sub_id}`, `sub_renew_failed:{sub_id}`.

Triggers are purely time-based. No queue, no cron, no external scheduler. Two immediate in-API push triggers also exist: webhook failure path (`payments.rs:207`) and monitor-registration match (`breach.rs:86`).

---

## 6. Auth

Stateless JWT. No sessions, no cookies, no refresh-token table.

### License token (device clients)

- Algorithm **EdDSA / Ed25519**, TTL **30 days** — `crates/qasa-core/src/license.rs:12`
- Claims: `sub` account UUID, `did` device UUID, `jti`, `tier`, `entitlements[]`, `device_limit`, `iat`, `exp` — `license.rs:14`
- Private key env: `QASA_LICENSE_PRIVATE_KEY`, **raw 32-byte seed, standard base64** (not PEM). PKCS#8 DER is hand-assembled for `jsonwebtoken` compat — `license.rs:133`
- Prod: missing key is a hard error at boot — `main.rs:60`. Dev: ephemeral key regenerated per restart, which invalidates all previously issued tokens
- Public key published at `/.well-known/qasa-license-key`

### Admin token

- **HS256**, TTL **8 hours** — `crates/qasa-core/src/admin_auth.rs:9`. Claims `sub`, `email`, `role`, `jti`, `iat`, `exp`. Secret `QASA_ADMIN_JWT_SECRET`
- Prod: missing secret panics

### Extractors

`crates/qasa-api/src/auth.rs`:

- `LicenseAuth` (line 9) — parse bearer, verify Ed25519, **then check `license_revocations` for `jti`** (`repos/device.rs:173`). Rejects revoked.
- `AdminAuth` (line 26) — parse bearer, verify HS256. **No revocation check.**

### Network guard

`admin_tailscale_guard` — `middleware.rs:16`, applied to the whole router at `main.rs:191`. Restricts `/v1/admin*` when `QASA_ADMIN_ALLOW_CIDRS` is set. Client IP from first `x-forwarded-for` hop, else `ConnectInfo<SocketAddr>`. Accepts literal IPv4, the token `tailscale`, or CIDR `100.64.0.0/10`. **IPv6 always denied.**

### Entitlement model

`effective_tier` — `crates/qasa-core/src/entitlements.rs:37`. Premium is granted **only** by a live subscription row. A stale `accounts.tier = 'premium'` with no live sub downgrades to `expired`. Never trust the flag alone.

| Tier | Device limit | Breach | VPN |
| --- | --- | --- | --- |
| `free` | 1 | no | no |
| `premium` | 5 | yes | yes |
| `expired` | 0 | no | no |

`entitlement_names` (`license.rs:121`) always includes `"multi_device"` for every tier — the real cap is `device_limit`.

`require_active_premium` — `crates/qasa-api/src/entitlements.rs:26`. Requires `subscriptions` row with `status IN ('active','cancelled') AND expires_at > now()`.

### Provisioning

1. Client POSTs `/v1/devices/register` with client-generated `account_id`, `fingerprint`, `device_name`, `platform`, optional `location`. No credential.
2. `upsert_account` inserts as `tier='free'` if new — `repos/account.rs:7`
3. `resolve_entitlements` checks live subscription. `Expired` → 403 — `devices.rs:74`
4. Known fingerprint → `touch_device` (updates `last_seen_at` + `location`). Else capacity check vs `device_limit`; overflow → 403 structured body — `devices.rs:92`
5. `license_issuer.issue(...)` → `(token, jti)`; `set_license_jti` persists jti — `devices.rs:119`
6. `set_vault_initialized` flips
7. Client later PATCHes `/v1/devices/prefs` with `push_token` + `locale` + opt-in

---

## 7. Client crypto

Spec is identical on both platforms. `qasa/SharedAutofill/MasterKeyDeriver.swift` (iOS) and `qasa_android/.../vault/crypto/MasterKeyDeriver.kt` (Android).

```
master password
  └─ PBKDF2-HMAC-SHA256, 310,000 iterations, 32-byte salt
       └─ root key (32 bytes)
            ├─ SHA-256 verifier          → stored, for password checks
            └─ HKDF-SHA256, empty salt
                 ├─ info "qasa-enc"    → AES-256-GCM data key
                 └─ info "qasa-search" → blind-index key
```

`PBKDF2_ITERATIONS = 310_000` — `MasterKeyDeriver.kt:17`.

### Storage split

Every item is two AES-GCM blobs:

- `encryptedEnvelope` — display name, subtitle, collection. All the list needs.
- `encryptedPayload` — full typed record.

The list decrypts envelopes only. Listing never requires full decryption.

### Blind-index search

Normalize searchable strings → token hashes via the `qasa-search` subkey → `vault_search_tokens` rows. Queries hash client-side and match rows. Plaintext never persists, and the server has no search surface at all.

### Master-password change

`VaultRepository.reencryptAll(oldEnc, newEnc, newSearch)` — Android `VaultRepository.kt:111`. Re-wraps every item, rebuilds every search token. iOS equivalent in `Services/Vault/VaultRepository.swift` behind `Views/ChangeMasterPasswordView.swift`.

### Item types

`Models/VaultEnums.swift` / `vault/VaultPayload.kt`: login, secure note, payment (card | bank account), personal info, ID document.

### Autolock

| | iOS | Android |
| --- | --- | --- |
| Mechanism | `AutoLockMonitor.handleScenePhase` + `DragGesture(minimumDistance: 0)` activity hook | `AutoLockMonitor` polls every 15 s on `Dispatchers.Main.immediate` |
| State | `lastActivityAtMs` | `lastActivityAtMs`, `backgroundedAtMs` |
| Range | 1–60 min | 1–60 min |

Behaviorally equivalent. Android polling is the more wasteful approach but is bounded and cancelled on stop.

---

## 8. Payments

`PaymentGateway` trait — `crates/qasa-payments/src/gateway.rs:66`. `GatewayKind` — line 6. Registered into `AppState.gateways` — `state.rs:23`.

Three Iraqi gateways: **FIB**, **FastPay**, **SuperQi**. No Apple Pay, no Stripe, no StoreKit, no Google Play Billing on any client. Deliberate.

All three are stubs — `crates/qasa-payments/src/stub.rs:8`:

- Synthetic order IDs: `FIB-ORD-` / `FP-ORD-` / `SQ-ORD-` + 8 hex — line 30
- Deep links `fib://pay?amount=…&ref=…` — line 38
- `verify_webhook` **ignores the headers argument entirely** — line 43. No HMAC, no signature.

### Webhook convergence

`payments::handle_webhook` — `payments.rs:130`: verify → persist `webhook_events` → find payment by `gateway_order_id` → early-return if already `success` → on success `activate_subscription` + `mark_status`; on failure push `SUB_RENEW_FAILED` if the account has an auto-renew sub.

### Client-side flow

`GET /v1/pricing` → pick gateway → `POST /v1/payments/intents` → open external gateway app → poll `GET /v1/payments/{id}` → on success refresh license.

Android polls 30 attempts at 2 s = 60 s ceiling — `PaymentServices.kt:60,73`.

### Simulated path

`PaymentService.simulateStubPayment(gateway, orderId)` POSTs `StubWebhookBody(orderId, "success")` to the webhook. Lets the whole upgrade flow complete on a device with no money and no gateway app. Both clients.

---

## 9. Push

`crates/qasa-push/src/lib.rs`. FCM HTTP v1, not legacy.

- OAuth2 JWT-bearer to `https://oauth2.googleapis.com/token`, scope `firebase.messaging` — line 320
- POST `https://fcm.googleapis.com/v1/projects/{project_id}/messages:send` — line 242
- Token cached in `Mutex<Option<CachedToken>>`, 60 s refresh margin — line 301
- Android payload: `priority: HIGH`, `channel_id: qasa_alerts` — line 261
- iOS/APNs payload: `apns-priority: 10`, `apns-push-type: alert`, `aps.sound` — line 269
- Single `devices.push_token` field serves both platforms. Apple `.p8` lives in Firebase Console, never on the server
- Degrades gracefully: without creds, inbox rows still write, pushes are logged only — line 229

### Multilingual copy

`crates/qasa-db/src/repos/push_copy.rs`. Three `LocalizedNotice` constants: `BREACH_ALERT_PREMIUM`, `SUB_EXPIRING_3D`, `SUB_RENEW_FAILED`. English + Sorani Kurdish + Arabic. Locale selection at `qasa-push/src/lib.rs:347` and `notifications.rs:122`.

### Client handling

Both no-op cleanly if Firebase config files are absent — iOS checks `GoogleService-Info.plist`, Android checks `FirebaseApp.getApps(context).isEmpty()`. Android posts to a single `qasa_default` channel. iOS has a richer category set for its payment drawer.

Token upload is coupled to vault unlock: `AppBootstrap.syncLocaleToServer()` PATCHes `/v1/devices/prefs` on every unlock. A refresh that lands while the app is locked is not uploaded until the next unlock.

---

## 10. VPN

Control plane only. The server never touches a WireGuard socket — node agents own the data plane.

- `WireGuardConfig` — `crates/qasa-vpn/src/config.rs:16`. DNS `1.1.1.1` / `8.8.8.8`, `allowed_ips 0.0.0.0/0, ::/0`, `persistent_keepalive 25`
- IP allocation from per-node `ip_pool_cidr` — `pool.rs:29`. Skips `.0`, reserves `.1` for gateway
- Node selection = least utilization, eligible if `status='online' && utilization < 0.8` — `repos/vpn.rs:30`
- Agent tokens stored as SHA-256 hash, constant-time compare — `qasa-vpn/src/config.rs:53`
- Heartbeat via `x-qasa-agent-token` header, not JWT

Neither mobile app builds a tunnel — no `VpnService`, no `NetworkExtension`, no WireGuard library. `VPNScreen` is a control surface for server-side session state only.

---

## 11. Dashboard

Vue 3.5 + Vite 8 + Tailwind v4 + shadcn-vue (reka-ui). `dashboard/src/`.

**No state library.** No Pinia, no Vuex, no composables dir. Local `ref`/`computed` per view, route state via `useRoute()`, token in `localStorage` key `qasa.admin.token`, toasts via `vue-sonner`.

**Mock-fallback mode.** Every view does `if (!isLiveApiEnabled()) return` against `src/data/mock.ts` fixtures. The whole app runs fully offline. Setting `VITE_QASA_API_URL` is the single switch to live mode — `src/api/client.ts`.

### Routes

| Path | View | Purpose |
| --- | --- | --- |
| `/login` | `LoginView.vue` | Email + password. Hardcoded dev creds `admin@qasa.io` / `admin123` |
| `/` | `OverviewView.vue` | 4 KPI cards, subscription growth chart, gateway breakdown |
| `/analytics` | `AnalyticsView.vue` | 3 KPIs, payment funnel, conversion %, cancellation reasons |
| `/users` | `UsersView.vue` | User table, client-side search, tier filter, 5-per-page pagination |
| `/payments` | `PaymentsView.vue` | Webhook audit table, status filter, JSON payload sheet + clipboard copy |
| `/plans` | `PlansView.vue` | **Real write.** Edit plan fields, optional subscriber notify → `PUT /v1/admin/plans` |
| `/cancellations` | `CancellationsView.vue` | Cancellation reasons and amounts |
| `/notifications` | `NotificationsAdminView.vue` | **Real write.** Compose EN + optional CKB/AR, `audience` hardcoded `all` → `POST /v1/admin/notifications` |
| `/vpn` | `VpnNodesView.vue` | Node fleet, CPU/mem gauges, online vs High Load |
| `/telemetry` | `TelemetryView.vue` | System health, resource gauges, live event stream |

Router guard — `src/router/index.ts:41`. Demo mode allows everything. Live mode: `/login` public, everything else needs a token. No role check — `role` from the login response is discarded.

`src/api/admin.ts` has 14 endpoint wrappers. Two are writes. `fetchWebhooks()` is defined but never called.

### UI affordances with no server effect

Block user, refresh session, view devices, add VPN node, export CSV (3 places) — all toast-only. Sign out toasts and navigates but never calls `setAuthToken(null)`, so the token persists in localStorage.

---

## 12. Localization

`l10n/`. Single source of truth `strings.json` + per-platform overlays in `platform/`.

| Property | Value |
| --- | --- |
| Keys | 543 |
| Locales | 3 — `en` (source), `ar`, `ckb` (Sorani) |
| Coverage | 100% — every key has all 3 |
| Placeholders | 102 strings |
| Schema | flat `key → {en, ar, ckb}`, dotted camelCase, ≥2 segments |

`l10n/platform/ios.json` is `{}` — mechanism wired, unused. `l10n/platform/android.json` has 2 keys: `app_name`, `autofill_service_description`.

### generate.py

```bash
python3 l10n/generate.py           # write
python3 l10n/generate.py --check   # drift gate, exit 1
```

Fans out into 6 committed files:

1. `qasa/qasa_ios/Resources/Localizable.xcstrings` — full merged table
2. `qasa/QasaCredentialProvider/Localizable.xcstrings` — hardcoded 28-key subset — `generate.py:22`
3. `qasa/QasaPaymentNotification/Localizable.xcstrings` — hardcoded 7-key subset — `generate.py:54`
4-6. `qasa_android/app/src/main/res/values/`, `values-ar/`, `values-ckb/`

`subset()` raises `KeyError` listing missing keys, so deleting a subset key breaks the build loudly.

Android key transform `android_name()` — dotted to snake_case, non-`[a-zA-Z0-9_]` to `_`, lowercased, leading digit gets `s_` prefix. Placeholder conversion `ios_to_android_placeholders()`: `%@`/`%s` → `%s`, `%lld`/`%ld`/`%d` → `%d`, positional `%1$@` → `%1$s`. XML escaping via `escape_android_xml()`. Collision guard raises `ValueError` if two keys map to one resource name.

`--check` regenerates into a tempdir and byte-compares all 6 targets. No CI wires this up — no `.github/` exists.

### extract_ios.py

Reverse, destructive direction. Reads the iOS catalog, keeps only dotted camelCase keys with all 3 locales, and **overwrites `l10n/strings.json`**. README flags it as re-seed only. Silently drops Android-overlay-style keys like `app_name`.

---

## 13. Environment variables

Names only. `AppConfig::from_env` — `crates/qasa-core/src/config.rs:44`. `AppEnvironment::from_env` — line 11: `prod|production` → Prod, else Dev. Loaded by `dotenvy` at `crates/qasa-api/src/main.rs:38`, best-effort.

| Variable | Default / behavior |
| --- | --- |
| `DATABASE_URL` | `postgres://qasa:qasa@localhost:5432/qasa` |
| `BIND_ADDR` | `0.0.0.0:8080` |
| `QASA_ENV` | `dev`. `prod` enables structured logs, `/metrics`, worker, prod key requirements |
| `QASA_ADMIN_JWT_SECRET` | dev default present; **prod panics if unset** |
| `QASA_LICENSE_PRIVATE_KEY` | dev: ephemeral; **prod: hard error at boot** |
| `QASA_ADMIN_SEED_EMAIL` | optional, dev-only admin seeding — `main.rs:48` |
| `QASA_ADMIN_SEED_PASSWORD` | optional, dev-only |
| `QASA_ADMIN_CORS_ORIGIN` | prod only. Unset → `Any`. Invalid value panics — `main.rs:130` |
| `QASA_ADMIN_ALLOW_CIDRS` | comma-separated IPv4 or `tailscale`. Empty = guard disabled |
| `QASA_WORKER_ENABLE` | `1`/`true` runs worker outside prod |
| `QASA_FCM_PROJECT_ID` | optional, falls back to service-account JSON `project_id` |
| `QASA_FCM_SERVICE_ACCOUNT_JSON` | filesystem path to Firebase service-account JSON |
| `QASA_FCM_SERVICE_ACCOUNT_JSON_INLINE` | raw JSON, takes precedence over the path |
| `QASA_VPN_NODE_ID` | agent CLI |
| `QASA_VPN_AGENT_TOKEN` | agent CLI, default `dev-agent-token` |
| `QASA_API_URL` | `http://localhost:8080` for agent + smoke script |
| `RUST_LOG` | api fallback `qasa_api=debug,tower_http=info,sqlx=warn`; worker `qasa_worker=info,sqlx=warn` |
| `POSTGRES_PASSWORD` | compose-only, default `qasa` |

Client-side:

| Platform | Debug | Release |
| --- | --- | --- |
| iOS | `http://localhost:8080` | `http://localhost:8080` — see Appendix |
| Android | `http://10.0.2.2:8080` | `https://api.qasa.app` |
| Dashboard | `VITE_QASA_API_URL=http://localhost:8080` | same |

`server/.gitignore` excludes `.env`, `.env.*` except `.env.example`, `*.pem`, `*.key`, `*.p12`, `secrets/`, `credentials.json`.

---

## 14. Local development

```bash
make help      # list targets
make up        # Postgres :5432 + Redis :6379
make env       # create server/.env from .env.example
make api       # cargo run -p qasa-api on :8080
make dev       # up + wait-db + api
make smoke     # server/scripts/smoke-api.sh
make worker    # needs QASA_ENV=prod
make dash      # cd dashboard && bun run dev
make android   # assembleDebug + installDebug
make down      # stop containers
make clean     # down -v, wipes DB volumes
```

`server/scripts/smoke-api.sh` — 345 lines, needs `curl` + `jq`. Six stages: device registration + license JWT, free-tier breach lookup + monitor register (expects 403), premium upgrade via FIB webhook, premium monitor + inbox alert + hash ingest + prefix lookup, VPN connect→status→disconnect, admin node list + agent heartbeat. Flags `--skip-ingest`, `QASA_API_URL`.

CLI tools: `qasa-breach` (`ingest --file … --source …`, `stats`), `qasa-vpn-agent` (`--node-id <uuid> --agent-token <token>`).

---

## Appendix: known gaps

Ranked by risk. Verified against code, not inferred.

**1. iOS Release API URL is `http://localhost:8080`.** Both Debug and Release hardcode the same localhost value in `project.pbxproj` (lines 504 and 543). A Release build cannot talk to anything. Must be `https://api.qasa.app` before any distribution build.

**2. VPN has no tunnel.** Billed as a premium security feature, but neither client establishes one. It toggles server-side session state only.

**3. `verify_webhook` is a no-op.** All three gateways ignore headers — `stub.rs:43`. Combined with client-side `simulateStubPayment`, a forged POST to `/v1/payments/webhook/fib` with any order ID grants premium against production. Server must gate by environment.

**4. PBKDF2-HMAC-SHA256 310k, not Argon2id.** Blueprint specifies Argon2id; both clients implement PBKDF2. Consistent across clients and the README, so settled in practice, but PBKDF2 is not memory-hard and this is a password manager. Largest single crypto divergence from spec.

**5. Android autofill requires the app already unlocked.** `QasaAutofillService` bails with `onSuccess(null)` if `!session.isUnlocked` — no in-fill biometric unlock. Source comment acknowledges the gap. iOS has full Face ID / Touch ID unlock in its credential provider.

**6. Android LAN sync is a stub.** `sync/LANVaultSyncService.kt` only updates a status string, never opens a socket. iOS has a real Network.framework mDNS + pairing-code + ciphertext transfer. Cross-device vault sync is effectively iOS-only.

**7. Android Credential Manager provider is weak.** All entries share one generic `PendingIntent` to MainActivity, so entry selection does not convey the specific credential. Username displayed is `entry.title`, a display name, not a real username. `onBeginCreateCredentialRequest` returns empty — no save-to-Qasa path. `onClearCredentialStateRequest` is a no-op.

**8. Three dead tables.** `entitlement_overrides`, `admin_audit_log`, `app_settings`. `is_vault_initialized` is written but never read. `vpn_usage.add_bytes` is defined but never called — VPN byte accounting is read-only.

**9. Stale VPN threshold is 5 minutes** (`worker/src/jobs.rs:26`) while sessions are meant to be long-lived. Combined with `GET /v1/vpn/status` force-disconnecting any user without a live sub, the 10-minute cleanup job is aggressive.

**10. `refresh_materialized_views` swallows all errors** with `.ok()` — `repos/worker.rs:58`. Silent failure.

**11. Rate limiting is per-process.** `Mutex<HashMap>` in `qasa-breach/src/rate_limit.rs:13`, not Redis, despite Redis being in both compose files. Only applied to the breach prefix endpoint. Breaks across multiple API replicas.

**12. Android logging interceptor is not DEBUG-guarded.** `HttpLoggingInterceptor.Level.BASIC` — `QasaApiClient.kt:32`. BASIC logs request lines and status codes, never bodies, so it is not a secret leak, but it logs every endpoint and status on a misconfigured release. Wrap in `if (BuildConfig.DEBUG)`.

**13. Single Android notification channel.** `qasa_default` at `IMPORTANCE_DEFAULT`. Breach alerts and payment failures are meaningfully different urgencies; a silent breach notification is the likely result.

**14. No tests on either client.** No `Tests` / `androidTest` source sets, no XCTest targets. For a password manager whose value proposition is key derivation and record encryption, crypto vectors and the master-password rotation path are the two places where a regression would be silent and unrecoverable.

**15. `server/README.md` endpoint table is stale** — omits 9 endpoints, including all of `/v1/subscription*`, `/v1/admin/plans`, `/v1/admin/notifications`, `/v1/pricing`, `/v1/devices/prefs`. `main.rs` is authoritative.

**16. `dashboard/.env` is not gitignored** and contains the API URL. Not a secret, but it is committed config.

**17. Prod compose does not wire** `QASA_ADMIN_ALLOW_CIDRS`, `QASA_FCM_*`, or `QASA_WORKER_ENABLE` into the api/worker service environments, and has no Caddy service — despite `docker/Caddyfile` existing.
