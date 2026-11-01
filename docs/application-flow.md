# Qasa — Application Flow

End-to-end user and system journeys. Companion to [`ARCHITECTURE.md`](../ARCHITECTURE.md).

Legend for auth column: **none** = unauthenticated, **Lic** = Ed25519 license JWT, **Adm** = HS256 admin JWT, **Agent** = `x-qasa-agent-token`, **Prem** = live subscription required.

---

## Flow map

```
┌─────────────────────────────────────────────────────────────────────┐
│ CLIENTS (local-first, vault never leaves device)                    │
│                                                                     │
│  iOS: qasaApp → RootView ─unlock─→ AppShellView ──→ Vault / Devices  │
│                                                  ──→ Breach / VPN   │
│                        ↳ QasaCredentialProvider (ASCredentialProvider)│
│                        ↳ QasaPaymentNotification                      │
│                                                                     │
│  Android: QasaApp → MainActivity → RootNav → AppShellScreen        │
│                        ↳ QasaAutofillService (AutofillService)       │
│                        ↳ QasaCredentialProviderService (CredentialMgr)│
└────────────────────────────┬────────────────────────────────────────┘
                             │  Bearer license JWT
                             │  (no vault content, ever)
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│ qasa-api  (Axum)                                                    │
│  devices · license · subscriptions · payments · breach · vpn        │
│  pricing · notifications · admin · analytics · metrics              │
└──────┬───────────────────────┬──────────────────────┬───────────────┘
       │                       │                      │
       ▼                       ▼                      ▼
┌────────────┐        ┌────────────────┐      ┌──────────────────┐
│ PostgreSQL │        │ qasa-push      │      │ Payment gateways │
│ 16         │        │ FCM HTTP v1    │      │ FIB              │
│            │        │ → APNs / Android│      │ FastPay          │
│ Redis 7    │        └────────────────┘      │ SuperQi          │
│ (unused)   │                                └──────────────────┘
└────────────┘
       ▲
       │  HS256 admin JWT + CIDR guard
┌──────┴──────────────────────────────────────────────────────────────┐
│ dashboard (Vue 3 + Vite) — 10 views, 2 real writes                  │
└─────────────────────────────────────────────────────────────────────┘

Sidecar: qasa-worker  — 8 interval jobs, prod only
Sidecar: qasa-vpn-agent — per-node heartbeat
Sidecar: qasa-breach — offline corpus ingest
```

---

## 1. First launch — vault creation

No server contact until the vault is already encrypted on-device. Preserves the local-first promise.

```mermaid
sequenceDiagram
    participant U as User
    participant A as App (local)
    participant DB as SwiftData / Room
    participant S as qasa-api

    U->>A: Set master password
    A->>A: generate 32-byte salt
    A->>A: PBKDF2-HMAC-SHA256 310k → root key
    A->>A: HKDF-SHA256 → qasa-enc + qasa-search
    A->>A: SHA-256(root key) → verifier
    A->>DB: create encrypted vault schema
    A->>A: generate device fingerprint UUID
    A->>A: store fingerprint in Keychain / EncryptedSharedPreferences
    A->>A: offer biometrics (store under biometryCurrentSet)
    A->>A: AppBootstrap.onVaultUnlocked()
    A->>S: POST /v1/devices/register  [none]
    S->>S: upsert_account (tier=free) + resolve entitlements
    S->>S: device_limit check — free = 1
    S-->>A: license JWT (Ed25519, 30d)
    A->>A: store token
    A->>S: GET /v1/license  [Lic]
    S-->>A: refreshed JWT
    A->>S: PATCH /v1/devices/prefs  [Lic]
    Note over A,S: push_token + locale + notifications_enabled
    A->>A: sync premium state → AppSession
```

Server never sees the password, the salt, or any vault row. The first request is `/v1/devices/register` and it carries only a UUID the client invented.

Registration rejects an `expired` account up front — `devices.rs:74`. A free account that has hit its 1-device limit gets a 403 carrying `devices_used`, `device_limit`, and a device list so the UI can name the device to unlink.

---

## 2. Unlock and autolock

```mermaid
sequenceDiagram
    participant U as User
    participant A as RootView / RootNav
    participant B as AppBootstrap

    U->>A: Open app or resume
    A->>A: AutoLockMonitor checks idle time
    alt within limit
        A-->>U: show vault
    else exceeded
        A-->>U: show lock screen
        U->>A: master password or biometric
        A->>A: SHA-256(root key) == stored verifier
        A->>B: onVaultUnlocked()
        B->>B: seed / rebuild autofill index
        B->>B: register device if needed
        B->>B: refresh license
        B->>B: sync premium → AppSession
        B->>B: PATCH /v1/devices/prefs
        A->>A: AutoLockMonitor.start()
        A-->>U: vault
    end
```

Failure mode: wrong password only ever fails the verifier comparison. There is no server-side credential to fall back to and no reset path — the vault is unrecoverable by design.

---

## 3. Vault use — free tier, zero network

Adding, editing, and reading items is entirely local. The only server calls in this flow are the ones a premium feature needs.

| Action | Local work | Server |
| --- | --- | --- |
| Create item | encrypt envelope + payload, index search tokens | none |
| List items | decrypt envelopes only | none |
| Search | hash query with `qasa-search`, match `vault_search_tokens` | none |
| Open item | decrypt payload | none |
| Autofill | read app-group index, decrypt selected entry | none |
| Change master password | `reencryptAll` — re-wrap every item, rebuild every token | none |

Listing never decrypts a full payload. Search never sees plaintext. Neither is possible to express server-side because no endpoint accepts vault content.

---

## 4. Pairing a second device

```mermaid
sequenceDiagram
    participant U as User on new device
    participant A as PairExistingVaultView
    participant S as qasa-api

    U->>A: Enter account_id from existing device
    A->>S: POST /v1/devices/link-check  [none]
    S-->>A: account exists / 404
    alt account not found
        A-->>U: invalid pairing code
    else found
        A->>A: create LOCAL keys, init local vault
        Note over A: keys are never transferred
        A->>S: POST /v1/devices/register  [none]
        alt free + limit 1 already used
            S-->>A: 403 device_limit_exceeded
            A-->>U: "Free tier is limited to 1 device.<br>Unlink the other device or upgrade."
        else premium (limit 5) or slot free
            S-->>A: license JWT
            A-->>U: vault ready
        end
    end
```

`link-check` is unauthenticated — the security property is that `account_id` is an unguessable client-generated UUID, not that a credential guards it. Nothing is created by the probe.

Critically, pairing does not move key material. The new device derives its own keys from a locally entered master password. The server coordinates membership; it never brokers secrets.

---

## 5. Upgrade to premium

```mermaid
sequenceDiagram
    participant U as User
    participant A as App
    participant S as qasa-api
    participant G as FIB / FastPay / SuperQi
    participant W as qasa-worker

    U->>A: Tap Upgrade
    A->>S: GET /v1/pricing  [none]
    S-->>A: premium_monthly 10000 IQD, premium_yearly 100000 IQD
    U->>A: Pick plan + gateway + auto_renew
    A->>S: POST /v1/payments/intents  [Lic]
    Note over A: idempotency_key = UUID
    S->>S: insert payments (status=pending)
    S-->>A: payment_id + fib://pay?amount=10000&ref=...
    A->>G: open deep link
    U->>G: approve payment
    G->>S: POST /v1/payments/webhook/{gateway}  [none]
    S->>S: persist webhook_events
    S->>S: find payment by gateway_order_id
    S->>S: activate_subscription, expires_at = now + plan.days
    S->>S: mark_status success
    loop poll, 30 attempts × 2s
        A->>S: GET /v1/payments/{id}  [Lic]
        S-->>A: status
    end
    A->>S: GET /v1/license  [Lic]
    S-->>A: new JWT, tier=premium, device_limit=5
    A->>A: AppSession.isPremium = true
    A-->>U: breach + VPN + multi-device unlocked
```

Webhook handling is idempotent — `payments.rs:130` early-returns if the payment is already `success`, so a gateway retry is harmless.

If the payment fails and the account has an auto-renew subscription, the webhook path pushes `SUB_RENEW_FAILED` immediately rather than waiting for the hourly worker.

**Simulated path** — no money, no gateway app:

```mermaid
sequenceDiagram
    participant A as App
    participant S as qasa-api
    A->>S: POST /v1/payments/webhook/fib  [none]
    Note over A: StubWebhookBody(orderId, "success")
    S-->>A: activates premium
```

Dev-only convenience. The stub gateways accept unsigned webhooks, so they settle payments only when `QASA_ENV=dev`; in production they refuse, and `create_intent` refuses up front (see `ARCHITECTURE.md` §4.8).

---

## 6. Subscription lifecycle

```mermaid
stateDiagram-v2
    [*] --> free
    free --> active: successful payment
    active --> cancelled: POST /v1/subscription/cancel
    cancelled --> active: auto_renew + successful renewal
    active --> expired: expires_at passes (worker, 15 min)
    cancelled --> expired: expires_at passes (worker, 15 min)
    expired --> active: new successful payment
    expired --> free: [no active sub → tier downgraded]
```

`cancelled` still grants Premium until `expires_at` — a user who paid for the month keeps access. `require_active_premium` accepts `status IN ('active','cancelled') AND expires_at > now()`.

`effective_tier` never trusts `accounts.tier` alone: a `premium` flag with no live subscription row downgrades to `expired` — `entitlements.rs:37`.

### Expiry timeline

```mermaid
sequenceDiagram
    participant W as qasa-worker
    participant S as qasa-api
    participant A as App

    Note over W: T-3d, job subscription_expiry_warnings (60 min)
    W->>S: find auto_renew=false, expiring ≤3d
    W->>W: dedupe sub_expiring_3d:{sub_id}
    W-->>A: push SUB_EXPIRING_3D

    Note over W: T-24h, job subscription_renew_failed_notices (60 min)
    W->>S: find auto_renew=true, expiring ≤24h, no success payment in 2d
    W-->>A: push SUB_RENEW_FAILED

    Note over W: T+0, job expire_subscriptions (15 min)
    W->>S: mark sub expired
    W->>S: downgrade accounts.tier → expired
    W->>S: disconnect VPN sessions for non-subscribers

    A->>S: next request
    S-->>A: 403 subscription_required
    A-->>A: route locked pages → PremiumUpgrade
```

`GET /v1/vpn/status` also force-disconnects any session whose subscription has lapsed, so a stale tunnel does not outlive the sub even between worker ticks.

---

## 7. Breach monitoring — premium

k-anonymity. The server learns a 5-char prefix; the client compares the remaining 59 locally. Raw emails never leave the device.

### One-off check

```mermaid
sequenceDiagram
    participant U as User
    participant A as BreachMonitorService
    participant S as qasa-api
    participant DB as breach_hashes

    U->>A: Enter email to check
    A->>A: trim().lowercase()
    A->>A: SHA-256 → 64 hex chars
    A->>A: split → prefix = first 5, suffix = last 59
    A->>S: GET /v1/breach/prefix/{prefix}  [Lic + Prem]
    Note over A: 60 req/hr per account
    S->>DB: SELECT suffix WHERE prefix = $1
    DB-->>S: candidate suffixes
    S-->>A: suffix[]
    A->>A: compare locally, case-insensitive
    A-->>U: breached? + source list
```

Free tier gets 403 at this endpoint. Registration itself is also Premium-gated with a limit of 10 monitored addresses.

### Ongoing monitoring

```mermaid
sequenceDiagram
    participant U as User
    participant A as App
    participant S as qasa-api
    participant W as qasa-worker
    participant P as FCM

    U->>A: Enable monitoring for email
    A->>A: full 64-hex hash, split 5 + 59
    A->>S: POST /v1/breach/monitor  [Lic + Prem]
    S->>S: store full hash in monitored_email_hashes
    A->>S: GET /v1/breach/monitor  [Lic + Prem]
    S-->>A: list

    loop every 10 min
        W->>S: join monitored hashes vs breach_hashes
        Note over W: requires live sub<br>skips if alerted <7 days<br>limit 100
        W->>W: dedupe breach:{acct}:{hash12}:{YYYY-MM-DD}
        W->>P: push BREACH_ALERT_PREMIUM
        W->>S: notification_receipts + mark_alerted
    end

    P-->>A: notification, localized en/ar/ckb
    A-->>U: alert
    U->>S: DELETE /v1/breach/monitor  [Lic + Prem]
```

Corpus is imported offline, never fetched at runtime:

```bash
cargo run -p qasa-breach -- ingest --file <path> --source hibp-2024
cargo run -p qasa-breach -- stats
```

---

## 8. VPN — premium

Control-plane flow only. The server allocates and tracks; the node agents run the data plane.

```mermaid
sequenceDiagram
    participant U as User
    participant A as App
    participant S as qasa-api
    participant N as WireGuard node

    U->>A: Tap Connect
    A->>S: POST /v1/vpn/connect  [Lic + Prem]
    S->>S: verify Ed25519 JWT + jti not revoked
    S->>S: check monthly vpn_usage quota + throttle
    S->>S: select node — least utilization,<br/>eligible if online && util < 0.8
    S->>S: allocate IP from node ip_pool_cidr<br/>(skip .0, reserve .1 for gateway)
    S->>S: insert vpn_sessions (disconnected_at NULL)
    S-->>A: node name, city, assigned IP, config
    A-->>U: connected

    A->>S: GET /v1/vpn/status  [Lic only]
    S->>S: force-disconnect if sub lapsed
    S-->>A: active session

    A->>S: POST /v1/vpn/disconnect  [Lic only]
    S->>S: set disconnected_at, decrement active_peers
    S-->>A: ok
```

Node heartbeat is a separate path with its own auth:

```mermaid
sequenceDiagram
    participant G as qasa-vpn-agent
    participant S as qasa-api
    G->>S: POST /v1/internal/vpn/nodes/{node_id}/heartbeat
    Note over G: x-qasa-agent-token
    S->>S: SHA-256(token) vs stored hash,<br/>constant-time compare
    S->>S: update last_heartbeat, cpu, memory, active_peers
    S-->>G: ok
```

`disconnect` and `status` intentionally skip the Premium gate so a lapsed user is never stuck with a session they cannot close.

**The app does not establish a tunnel.** No `VpnService`, no `NetworkExtension`, no WireGuard library on either client. Traffic is not actually protected by this flow.

---

## 9. Autofill

### iOS — full implementation

```mermaid
sequenceDiagram
    participant S as Safari / any app
    participant E as QasaCredentialProvider
    participant I as AutofillVaultSync index
    participant U as User

    S->>E: ASCredentialProviderRequest
    E->>E: ASCredentialRequest requires interaction —<br/>never silent fill
    E->>I: read autofill-index.json (app group)
    Note over I: non-secret metadata in the clear,<br/>secrets AES-GCM encrypted
    E->>E: rank via AutofillContextMatcher
    E->>E: honor preferredRecordId (score 1000, pinned)
    E->>E: infer hint — domain-shaped service → login,<br/>else paymentCard if any card exists
    E-->>U: candidate list + search
    U->>E: select
    E->>E: auto-trigger Face ID / Touch ID on appear
    U->>E: authenticate
    alt login item
        E-->>S: onProvideLogin(user, password)
    else card or bank account
        E-->>U: EphemeralSecretDrawer
        Note over E: deliberately NOT returned as a credential.<br/>Apple has no safe surface for raw PAN/CVV.
        E-->>U: copy via EphemeralPasteboardSession
    end
```

Refusing to return cards as `ASPasswordCredential` is correct and deliberate. It is the one design choice in the codebase that has no Android equivalent and should not be "fixed" on Android.

`AutofillIdentitySync` also publishes to `ASCredentialIdentityStore` so entries appear in the system password manager picker.

### Android — narrower

```mermaid
sequenceDiagram
    participant S as Any app
    participant A as QasaAutofillService
    participant U as User

    S->>A: onFillRequest
    A->>A: session.isUnlocked?
    alt not unlocked
        A-->>S: onSuccess(null) — no fill
        Note over A: gap: no in-fill biometric unlock
    else unlocked
        A->>A: heuristic field detection<br/>(hints + HTML, not canonical constants)
        A->>A: filter kind == "login", cap 5
        A-->>U: RemoteViews rows, title only
        U->>A: select
        A-->>S: decrypt inline, fill
    end
```

Credential Manager provider (`QasaCredentialProviderService`) is weaker still: cap 10, all entries share one generic `PendingIntent` to MainActivity so selection does not convey which credential, username displayed is `entry.title`, `onBeginCreateCredentialRequest` returns empty, `onClearCredentialStateRequest` is a no-op.

---

## 10. LAN sync

### iOS — implemented

```mermaid
sequenceDiagram
    participant A as iOS device
    participant B as macOS target
    A->>B: mDNS advertise _qasa-sync._tcp
    U->>A: enter pairing code
    A->>B: pair
    A->>A: export selected items
    A->>B: ciphertext transfer
    B->>B: merge with local vault
```

### Android — stub

`sync/LANVaultSyncService.kt` updates a status string and never opens a socket. Cross-device vault sync is iOS-only today.

---

## 11. Notifications

Two channels. In-app inbox is server-backed and durable. Push is best-effort.

```mermaid
sequenceDiagram
    participant S as Trigger
    participant P as qasa-push
    participant F as FCM
    participant A as Device
    participant I as notification_receipts

    alt breach alert / expiry warning / renew failure
        S->>P: notify(account, kind, locale)
    else admin broadcast
        S->>P: POST /v1/admin/notifications
    end
    P->>I: write inbox row — always, even without FCM creds
    P->>P: select locale from device.locale — en / ar / ckb
    P->>P: get OAuth2 token, cached 60 s early
    P->>F: POST /v1/projects/{project_id}/messages:send
    Note over P: iOS: apns-priority 10, alert, sound<br/>Android: priority HIGH, channel qasa_alerts
    F-->>A: push
    A-->>A: PendingIntent → Notifications screen
    A->>S: GET /v1/notifications  [Lic]
    S-->>A: inbox
    A->>S: GET /v1/notifications/{id}  [Lic]
    Note over S: marks read as side effect
```

Without Firebase credentials the inbox row still writes and the push is logged — no user-visible failure.

Token upload is coupled to vault unlock (`AppBootstrap.syncLocaleToServer` PATCHes `/v1/devices/prefs` on unlock), so an FCM refresh that lands while the app is locked is not uploaded until the next unlock. A stale token can delay a breach alert.

---

## 12. Admin

```mermaid
sequenceDiagram
    participant A as Admin
    participant D as Dashboard
    participant S as qasa-api
    participant M as middleware

    A->>D: email + password at /login
    D->>S: POST /v1/admin/auth/login  [none]
    S->>S: Argon2 verify vs admin_users.password_hash
    S-->>D: HS256 token, 8h TTL
    D->>D: store in localStorage['qasa.admin.token']
    Note over D: email and role from the response are discarded

    D->>S: GET /v1/admin/*  [Adm]
    M->>M: admin_tailscale_guard
    Note over M: QASA_ADMIN_ALLOW_CIDRS — IPv4, tailscale,<br/>or 100.64.0.0/10. IPv6 always denied.
    S->>S: verify HS256, no revocation check
    S-->>D: data
```

### Reads

`/v1/admin/overview`, `/analytics`, `/users`, `/payments`, `/cancellations`, `/webhooks`, `/vpn/nodes`, `/telemetry`, `/plans`, `/notifications`. Ten screens of visibility, one of which — Payments — synthesizes its JSON payload client-side from table fields rather than showing the real stored webhook body.

### Writes — only two exist

```mermaid
sequenceDiagram
    participant A as Admin
    participant D as Dashboard
    participant S as qasa-api

    rect rgb(240,248,255)
    A->>D: edit title, amount, days, VPN quota on /plans
    D->>S: PUT /v1/admin/plans  [Adm]
    S->>S: upsert
    opt notify_subscribers checked
        S-->>A: push to affected accounts
    end
    end

    rect rgb(240,248,255)
    A->>D: compose EN + optional CKB/AR on /notifications
    D->>S: POST /v1/admin/notifications  [Adm]
    Note over D: audience hardcoded 'all' — no picker
    S->>S: insert notification + fan out receipts
    S-->>A: push when FCM configured
    end
    end
```

### UI actions with no server effect

Block user, refresh session, view devices, add VPN node, and all three CSV exports are toast-only. Sign out navigates to `/login` but never clears the token, so it survives in localStorage.

---

## 13. Backend background loop

Not user-initiated, but it governs the lifecycle every other flow depends on.

```mermaid
flowchart TD
    W[qasa-worker<br/>prod only] --> J1
    W --> J2
    W --> J3
    W --> J4
    W --> J5
    W --> J6
    W --> J7
    W --> J8

    J1["expire_subscriptions<br/>15 min"] --> A1[mark subs expired<br/>downgrade tier<br/>disconnect VPN]
    J2["refresh matviews<br/>5 min"] --> A2[REFRESH CONCURRENTLY<br/>errors swallowed]
    J3["webhook_retry<br/>60 s"] --> A3[retry_count less than 5<br/>limit 50]
    J4["stale_vpn_sessions<br/>10 min"] --> A4[disconnect over 5 min<br/>decrement active_peers]
    J5["purge revocations<br/>24 h"] --> A5[delete over 31 days]
    J6["breach_alert_scan<br/>10 min"] --> A6[hash join, dedupe daily, push]
    J7["expiry_warnings<br/>60 min"] --> A7[auto_renew off, within 3 days]
    J8["renew_failed<br/>60 min"] --> A8[auto_renew on, within 24 h, stale]
```

All eight loop forever. Errors are logged, never fatal. `std::future::pending()` holds the process open after spawn.

---

## 14. Cross-platform parity

| Capability | iOS | Android |
| --- | --- | --- |
| Vault storage | SwiftData, AES-GCM envelope+payload | Room `vault_items`, same scheme |
| KDF | PBKDF2-HMAC-SHA256 310k → HKDF | identical |
| Blind-index search | `VaultSearchToken` | Room `vault_search_tokens` |
| Device register / link-check | yes | yes |
| Device-limit 403 body | `DeviceLimitErrorBody` | `QasaApiException.DeviceLimitExceeded` |
| Endpoint set | 18 routes | same 18, one-to-one |
| Autofill unlock in-fill | Face ID / Touch ID | **requires app already unlocked** |
| Credential Manager / identity store | `ASCredentialIdentityStore` | provider present, weak |
| Card fill safety | ephemeral drawer, never a credential | no equivalent |
| LAN sync | Network.framework, real | **stub** |
| Notifications | categories incl. payment drawer | single `qasa_default` channel |
| Locale propagation | writes `AppleLanguages` to app group too | Room + resources |
| Autolock | scene phase + gesture | 15 s poll |

The API contract has no drift. Every divergence is client-side capability, not protocol.
