Here is the full end-to-end blueprint for **Kaso**—tracing every screen, local action, server event, and back-office admin dashboard.

---

## 1. Complete Architecture & Flow Map

```
+-----------------------------------------------------------------------------------+
|                                 FRONTEND / CLIENT                                 |
|  [Screen 1: Setup] -> [Screen 2: Vault] -> [Screen 3: Devices] -> [Screen 4: Upgrade]
+-----------------------------------------------------------------------------------+
       |                                      |                                |
  Local SQLite                            k-Anonymity                      FIB / FastPay
  (Encrypted)                              Request                            Webhook
       v                                      v                                v
+-----------------------------------------------------------------------------------+
|                                RUST BACKEND SERVER                                |
|  - Auth & Device Handshake     - ScyllaDB Breach Index     - Payment Listener    |
|  - License Manager (Ed25519)   - WireGuard Tunnel Control  - Admin Telemetry API |
+-----------------------------------------------------------------------------------+
                                              |
                                              v
+-----------------------------------------------------------------------------------+
|                               ONLINE ADMIN DASHBOARD                              |
|  - User Directory & Entitlements   - Payment & Revenue Analytics                  |
|  - Real-time VPN Node Metrics      - Privacy Analytics (TelemetryDeck / PostHog)  |
+-----------------------------------------------------------------------------------+

```

---

## 2. Screen-by-Screen User Flow & Technical Execution

### Screen 1: Master Vault Creation & Initial Setup

* **User Perspective:** The user opens Kaso for the first time, sets up a master password, and enables Face ID / Fingerprint.
* **Under the Hood (Local):**
* Rust core generates a salt and derives a master key using `Argon2id`.
* Initializes the local encrypted SQLite/GRDB database using `SQLCipher` or AES-GCM.
* Generates a unique, persistent **Device Fingerprint** (UUID stored in iOS Keychain / Android KeyStore).

* **Server Action:** **Zero.** (The server has no idea this user exists yet).

---

### Screen 2: Password Vault & Local Usage (Free Tier)

* **User Perspective:** The user adds, generates, and autofills passwords seamlessly.
* **Under the Hood (Local):**
* Entries are encrypted locally using the derived key and rendered through Vue/Tauri or Swift/Kotlin native components.

* **Server Action:** **Zero network calls.** Keeps the local-first security promise intact.

---

### Screen 3: Device Management & Multi-Device Check

* **User Perspective:** The user installs the Kaso Chrome Extension or a second phone app and attempts to log into their account.
* **Under the Hood (Client-Server Handshake):**
1. Client sends a payload: `{ account_id, device_fingerprint, device_name: "MacBook Pro Chrome" }`.
2. **Rust Backend Server:**
* Checks Postgres/ScyllaDB for `active_devices_count` associated with `account_id`.
* **If Free Tier (Limit = 1):** Server rejects request with `403 Limit Exceeded`. Screen 3 shows: *"Free tier is limited to 1 device. Unlink 'Ali's iPhone' or upgrade to Premium."*
* **If Premium Tier (Limit = 5):** Server registers the new fingerprint and issues an **Ed25519-signed JWT License Token** (valid for 30 days).

3. Client stores the JWT in secure local storage and unlocks cross-device capability.

---

### Screen 4: Premium Subscription & Local Payment

* **User Perspective:** The user taps *"Upgrade to Premium (10,000 IQD / month)"* to unlock multi-device usage, Breach Monitoring, and VPN. They select **FIB**, **FastPay**, or **SuperQi**.
* **Under the Hood (Payment Flow):**
1. Kaso Backend calls the FIB/FastPay API to generate a invoice & deep link (`fib://pay?amount=10000...`).
2. The app opens the FIB/FastPay mobile app.
3. User approves payment. FIB/FastPay triggers a Webhook to `[https://api.kaso.app/v1/payments/webhook](https://api.kaso.app/v1/payments/webhook)`.
4. Rust Backend updates `user_subscriptions` (sets status = `active`, expiry = `Now + 30 days`).
5. Kaso app receives a WebSocket push or polls for status, downloads the updated **Ed25519 Offline License JWT**, and activates premium features instantly.

---

### Screen 5: Data Breach Monitoring (Premium)

* **User Perspective:** The user receives a notification: *"Your email test@gmail.com was found in a known leak."*
* **Access:** Premium only — free accounts cannot register monitors or run k-anonymity lookups (API returns `403`). Free may unlink a remote linked device to free the 1-device slot, or upgrade for up to 5 devices.
* **Under the Hood (Privacy-Preserving Search):**
1. Client hashes user email using SHA-256 (`5e884898da...`).
2. Client sends **only the first 5 characters** (`5e884`) to the Rust server.
3. Rust server searches its ScyllaDB breach dump index for hashes starting with `5e884`.
4. Server returns all candidate hash suffixes. Client checks the full hash locally. **Raw emails never leave the phone.**

---

### Screen 6: One-Tap WireGuard VPN (Premium)

* **User Perspective:** User taps "Connect VPN" to secure public Wi-Fi.
* **Under the Hood (Tunneling):**
1. Client sends its local JWT to `[https://vpn.kaso.app/v1/connect](https://vpn.kaso.app/v1/connect)`.
2. Rust server verifies JWT signature. If valid, generates ephemeral WireGuard keys and returns server IP, assigned IP, and handshake configuration.
3. App establishes a tunnel via OS-native network extension (`VpnService` / `NetworkExtension`).

---

## 3. Online Admin & Analytics Dashboard

To manage the app, monitor server health, track revenue, and analyze user retention without compromising privacy, you need an **Admin Web Dashboard**.

```
+-----------------------------------------------------------------------------------+
|                                 KASO ADMIN DASHBOARD                              |
|  [Overview]  [Users & Subscriptions]  [Payments (IQD)]  [VPN Nodes]  [Analytics]  |
+-----------------------------------------------------------------------------------+
| METRICS OVERVIEW:                                                                 |
|  - MRR: 4,500,000 IQD (~$3,000 USD)    - Active Paid Users: 450                   |
|  - Free Devices Active: 2,100          - VPN Bandwidth Used: 1.2 TB              |
+-----------------------------------------------------------------------------------+
| RECENT TRANSACTIONS:                                                              |
|  User ID    | Gateway | Amount     | Status  | Expiry Date | Actions          |
|  usr_9281a  | FIB     | 10,000 IQD | Success | 2026-09-02  | [Revoke] [Extend]|
|  usr_1102f  | FastPay | 10,000 IQD | Success | 2026-09-01  | [Revoke] [Extend]|
+-----------------------------------------------------------------------------------+

```

### Key Modules of the Dashboard

#### A. User & Device Management Module

* **View Accounts:** Search by masked ID or email (if provided for account recovery).
* **Device Control:** View linked device fingerprints per user (e.g., `iPhone 15 - 1/5 devices`). Manually clear or revoke device locks if a user loses their phone.
* **Entitlement Overrides:** Grant trial extensions or complimentary lifetime access to specific users.

#### B. Payment & Revenue Analytics (Local Gateways)

* Track real-time monthly recurring revenue (**MRR**) broken down by gateway (**FIB vs. FastPay vs. SuperQi**).
* View Webhook audit logs to debug failed payment notifications.
* Handle automated subscription expiration: Cron job flags expired accounts and blocks VPN/Breach endpoints upon next API request.

#### C. VPN & Server Infrastructure Health

* **Live WireGuard Server Metrics:** CPU, RAM, active peer connections, and bandwidth usage across your server locations.
* Automatic node routing: If Node 1 reaches 80% capacity, the dashboard/API directs new connections to Node 2.

#### D. Privacy-Preserving Analytics Strategy

Because Kaso is built on privacy, standard Google Analytics is unsuitable. Instead, combine:

1. **Self-Hosted PostHog** or **TelemetryDeck**: Track completely anonymized events (e.g., `event: "vault_item_created"`, `event: "upgrade_screen_viewed"`) without collecting raw IP addresses or PII.
2. **Server-side Metrics (Prometheus + Grafana):** Track API performance, k-Anonymity query throughput, and database query latencies.

---

## 4. Tech Stack Summary for Entire System

| Area | Recommended Tech |
| --- | --- |
| **Mobile & Desktop Frontend** | Vue.js + Tauri / Native Swift & Kotlin |
| **Local Vault Storage** | SQLite with SQLCipher (AES-256) |
| **Backend API Server** | Rust (`Axum` or `Actix-Web`) |
| **Breach Database** | ScyllaDB or Meilisearch (Optimized for instant prefix matching) |
| **VPN Engine** | WireGuard (`boringtun` or Linux Kernel WireGuard) |
| **Admin Dashboard UI** | Vue.js + Tailwind CSS (Hosted on Cloudflare Pages / Vercel) |
| **Database for Backend** | PostgreSQL (Stores accounts, payment transactions, and device limits) |
| **Analytics Engine** | TelemetryDeck or Self-Hosted PostHog |