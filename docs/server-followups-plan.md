---
name: Server-dependent follow-ups
overview: Items from the iOS ↔ Android parity work (docs/ios-android-parity-plan.md) that need server or network work, deferred to a separate session. The client sides are listed too, so each item can ship end-to-end on both apps.
todos:
  - id: lan-pairing
    content: "LAN pairing: shared Rust library in both apps (Argon2id-stretched code, PAKE-style LAN handshake); server only attaches the device; free tier → upgrade"
    status: pending
  - id: vpn-tunnel
    content: "VPN tunnel: server + node agent done 2026-11-02 (peer sync, required keys, handshake expiry, metering, offline nodes, node picker); still pending: device tunnels (iOS NetworkExtension, Android VpnService), node-side throttling"
    status: pending
  - id: notif-endpoints
    content: "Remote notifications: read / read-all / delete / unread count / paging, audience honoured, broadcasts pushed by the worker, localised pricing notice; server + dashboard (2026-11-01) and both apps (2026-11-02) done"
    status: completed
  - id: p0-correctness
    content: "P0 server bugs: client-set payment amount, unlinked devices keep working, re-link 500, webhook retry skips amount check, non-atomic settlement, early renewal, idempotency, link-check, monitor delete (server fixed 2026-11-01; client follow-ups listed in section 4)"
    status: completed
  - id: payments-non-gateway
    content: "Payments without a gateway: history, staff grants, abandoned-checkout expiry, no false renew pushes (done 2026-11-01)"
    status: completed
  - id: payments-live
    content: "Payments needing a gateway: Wayl gateway, renewal links and Wayl status checks done 2026-11-04 (switch on once the Wayl store is approved; first real 1,000 IQD purchase to confirm the webhook shape). Still open: refunds, direct FIB/FastPay/SuperQi if a saved-wallet charge is ever wanted, App Store / Play billing decision. Renewal engine done 2026-11-04"
    status: pending
  - id: lapsed-premium
    content: "Lapsed Premium falls back to Free; primary device keeps working (server done 2026-11-01)"
    status: completed
  - id: account-lifecycle
    content: "Account lifecycle: deletion, device self-unregister + unlink teardown, breach monitor restore, breach limit in license (server done 2026-11-01); vault backup and recovery still need a product decision"
    status: completed
  - id: breach-data-source
    content: "Breach data source researched (2026-11-02): no source sells email hash lists; providers are per-lookup APIs. Folded into email-leak-alerts"
    status: completed
  - id: email-leak-alerts
    content: "Email leak alerts parked until there are enough subscribers to pay for a provider (much later). See 'Email leak alerts: parked'"
    status: pending
  - id: account-recovery
    content: "Account recovery: design later (recovery_email_hash kept until then)"
    status: pending
  - id: admin-gaps
    content: "Admin: roles, audit log, login rate limit + logout, account detail/block/device unlink, VPN node CRUD, webhooks by order, search + paging, chart series, MRR (server done 2026-11-01)"
    status: completed
  - id: l10n-source-drift
    content: "l10n/strings.json reconciled with the shipped catalogs; generate.py --check passes (done 2026-11-02)"
    status: completed
  - id: android-notif-permission
    content: "Android 13+: request POST_NOTIFICATIONS at runtime and resync notifications_enabled on resume / after the prompt (done 2026-11-02; not yet tried on a device)"
    status: completed
  - id: admin-2fa
    content: "Admin 2FA: pending, no OTP method available yet"
    status: pending
  - id: ops-hygiene
    content: "Ops: Caddy TLS + required secrets, shared Postgres rate limiter, FCM dead tokens, prefs PATCH, unused schema dropped, API router tests; dashboard wired to the section 6–8 admin endpoints (done 2026-11-01)"
    status: completed
isProject: false
---

# Server-dependent follow-ups

Decisions here come from the product owner on 2026-11-01. Client paths: `I/` = `apps/ios/qasa_ios/`, `A/` = `apps/android/app/src/main/java/alibarznji/qasa/`. Server: `server/crates/`.

## 1. LAN pairing with Argon2 (Rust)

**Intended design (product owner):** the pairing code works **across the local network**, using **Argon2 in Rust**, to securely add a new device to the user's subscription. If the user is on the **free tier**, the flow sends them to the **upgrade page/modal**.

**Today (both apps):**
- `PairingSessionStore` (defined in `I/Views/PairExistingVaultView.swift`) and its port `A/screens/auth/pair/PairingSessionStore.kt` keep code → account id **in process memory** (10-minute codes). A code only resolves on the device that published it, so across two phones only the pasted account UUID links.
- Settings → Linked Devices → "Show pairing code" publishes into that store on both apps.
- The Pair flow (`I/Views/PairExistingVaultView.swift`, `A/screens/auth/PairExistingVaultScreen.kt` + `pair/*`) checks eligibility with `POST /v1/devices/link-check` (`server/crates/qasa-api/src/devices.rs`). A free account currently shows a "Premium required" card with **no upgrade action**.
- LAN vault sync: iOS `Services/Sync/LANVaultSyncService.swift`, Android `sync/LANVaultSyncService.kt`. Android still sends a placeholder blob.

**Decided (product owner, 2026-11-01): Argon2 runs in a shared Rust library inside both apps.**
- One Rust crate compiled for iOS and Android (e.g. UniFFI bindings) stretches the short pairing code with Argon2id into a key for a PAKE-style handshake over the LAN.
- The secret never makes a server round trip.
- The server is involved only afterwards, to attach the new device to the subscription. That step goes through the existing `/v1/devices/link-check` and device-registration endpoints, or an extension of them.
- Rejected: hashing and resolving codes in `qasa-api`.

**Still to decide before building:**
- Crate location (for example a new `server/crates/qasa-pairing` or a top-level `core/` workspace) and the binding tool (UniFFI vs. hand-written FFI). Also the build integration: an XCFramework for iOS and `cargo-ndk` `.so` files for Android.
- Discovery: mDNS / Bonjour (iOS `NWBrowser`, Android `NsdManager`). Reuse what `LANVaultSyncService` does.
- Abuse limits: attempt limits on 6-digit codes, a short TTL, and one-time use.

**Client work once the protocol exists (both apps, identical):**
- Replace `PairingSessionStore` publish/resolve with the real protocol.
- **Free tier → upgrade:**
  - The Pair "Premium required" step gets an "Upgrade to Premium" action that opens the upgrade modal (iOS `PremiumUpgradeView(embedded: false)` sheet; Android `PremiumUpgradeScreen(asSheet = true)`, which already exists).
  - "Show pairing code" in Settings opens the same modal for free users instead of showing a code.
- After pairing, finish on the normal Create screen with the adopted account. That already works on both apps.
- Android: replace the placeholder blob in LAN sync with the real encrypted vault transfer.

## 2. VPN tunnel (kill switch, auto-connect)

**Decision:** both switches are premium-only. Free users go to the upgrade page/modal.

**Today:**
- The whole VPN page is premium-gated on both apps, and free users see the shared `PremiumGate` with an upgrade action, so this part is satisfied.
- Both switches are now **saved** (`qasa.vpn.killSwitch` defaults to on, `qasa.vpn.autoConnect` to off) on both apps.
- **Nothing acts on them.** VPN is API-only: `/v1/vpn/connect|disconnect|status` in `server/crates/qasa-vpn` and `qasa-api`. There is no device tunnel, and the auto-connect copy promises "connects on public Wi-Fi".

**Work:**
- **Server:** issue per-device WireGuard configs (keys, endpoint, allowed IPs) from `qasa-vpn`, and rotate or revoke them on unlink and logout.
- **iOS:** a Packet Tunnel `NetworkExtension` target. Kill switch → `includeAllNetworks` / `enforceRoutes`. Auto-connect → `NEOnDemandRuleConnect` for untrusted Wi-Fi.
- **Android:** a `VpnService` running WireGuard (e.g. the wireguard-android library). Kill switch → guide the user to the system "Always-on + Block connections without VPN" setting, which apps can't turn on themselves. Auto-connect → a network callback on unmetered, insecure Wi-Fi.
- Until this ships, consider changing the two subtitles so they don't promise behaviour.

### Server and node agent — done 2026-11-02

Migration `20261102000001_vpn_tunnel.sql`.

**Verification:**
- Unit tests pass, including `wg` dump parsing and sync planning.
- `scripts/test-integration.sh` passes: 52 DB tests (5 new in `qasa-db/tests/vpn_tunnel.rs`) and 17 `qasa-api` tests. The new router test covers connect → agent peers → handshake → `tunnel_up` → disconnect → peer dropped.
- A live run of the real agent binary used a fake `wg`:
  - The dry run only logged.
  - `--apply` added the device peer and removed a stale one.
  - A reported handshake and 6 MB of traffic turned `tunnel_up` on and were metered.
  - Once in sync, nothing ran.
  - After disconnect, the agent removed the peer.
  - A wrong agent token got 403.

**Peer provisioning**
- `GET /v1/internal/vpn/nodes/{id}/peers` (agent token) returns every live session on the node as `{public_key, allowed_ip /32, throttle_mbps}`. A retired node gets an empty list.
- The agent (`qasa-vpn/src/bin/agent.rs`) diffs that list against `wg show <iface> dump` and runs `wg set … allowed-ips` / `wg set … remove`. It is a dry run unless `--apply` is passed, and it skips any key that isn't a valid WireGuard key.
- Disconnects, expiry, unlink, block and deletion all end the session, so the next sync removes the peer.

**Client keys**
- `client_public_key` must be a base64 32-byte WireGuard key; a malformed key is a 400.
- In prod the key is required. In dev a missing key gets a random well-formed placeholder, so API-only flows keep working.
- Same device and same key on the same node reuses the session. A new key, or another node, replaces it.

**Handshake-based expiry**
- `vpn_sessions.last_handshake_at` comes from the agent's per-peer report.
- The worker now runs every minute. It ends a session with no handshake for 5 minutes, or none within 10 minutes of connecting.
- This replaces the old rule, which ended every session 5 minutes after it started.

**Metering and quota**
- Byte deltas come from the cumulative counters; a counter reset counts the whole value.
- They are added to `vpn_usage` per account (`vpn_usage::meter`, under a row lock), against the current plan's `vpn_quota_gb`.
- At 80% a push goes out (`VPN_USAGE_80`). At 100% the account is throttled and a push goes out (`VPN_THROTTLED`). Each is sent once per month and resets with the period.
- The ckb/ar text awaits native review.
- Connect no longer writes usage; it only reports it.

**Node health**
- A worker job marks nodes `offline` after 2 minutes without a heartbeat. The next heartbeat brings them back. Drained and retired nodes keep their status.
- Heartbeats no longer overwrite `active_peers`. The agent also reports real CPU, memory and interface throughput from `/proc` and `/sys`, replacing the hardcoded 20% / 40% / 450 Mbps.

**Race-safe allocation**
- New unique indexes on live sessions: per device, per (node, address) and per (node, key). The migration first ends any duplicates.
- Connect retries the next free address when it loses a race. A concurrent connect from the same device returns the winner's session.
- A refused insert doesn't bump the peer count.

**Region choice**
- `GET /v1/vpn/nodes` lists online nodes under the 80% threshold, with id, name, city and load %.
- `connect` takes an optional `node_id`; an unavailable node is a 400.

**Status**
- `/v1/vpn/status` adds `node_id`, `last_handshake_at` and `tunnel_up` (a handshake within 3 minutes).
- `connected` alone still means "a session exists".

### Still pending

- **Node-side throttling.** Throttled peers come with `throttle_mbps = 5`, but the agent doesn't shape traffic yet (it logs that). It needs `tc` per-peer rate limiting on the node.
- **iOS device tunnel:** a Packet Tunnel `NetworkExtension` target. Generate the WireGuard keypair on the device (private key in the Keychain) and send the public key on connect. Then build the tunnel from the returned `config`. Kill switch and auto-connect as described above, plus the region picker and data meter Android has.

### Android device tunnel — done 2026-11-06

- **Tunnel:** wireguard-android `tunnel` 1.0.20260102 (`GoBackend` and its `VpnService`, declared by the library). `vpn/VPNService.kt` asks the API for a session, builds the config (`WireGuardConfigFactory`) and brings the tunnel up. The first connect shows Android's VPN consent dialog (`rememberVpnToggle`).
- **Keys:** `WireGuardKeyStore` makes the keypair on the device and keeps the private key in encrypted preferences. "Clear device" and losing the device slot drop the tunnel and the key (`VPNService.forget`).
- **State:** "Connected" means the device saw a handshake in the last 3 minutes; until then the dial shows "Connecting…". After 45 s with no handshake the page says the server can't be reached. Every 30 s the app checks `/vpn/status`: if the server ended the session it reconnects once, then gives up; a 401/403 (lapsed, unlinked) takes the tunnel down.
- **Region picker:** tap the server pill. The choice is saved (`qasa.vpn.nodeId`); a node that went away falls back to auto-select.
- **Data meter:** from the `usage` block. `/vpn/status` now returns `usage` too, connected or not.
- **Auto-connect:** `VpnAutoConnector` connects once on joining open, WEP or OWE Wi-Fi (any Wi-Fi below Android 12, where the type can't be read). It runs while the process lives and never asks for consent itself.
- **Kill switch:** Android apps can't enforce it. While it's on, the page explains the system's Always-on VPN + "Block connections without VPN" and opens VPN settings; once the system lockdown is on, it says so. Always-on starting the service connects (`GoBackend.setAlwaysOnCallback`).
- **Debug builds** keep Qasa itself outside the tunnel so a local dev API stays reachable.
- **Not verified on a device yet:** unit tests cover the config and Wi-Fi rules; a real node with a running agent is needed for an end-to-end check. The new ar/ckb text awaits native review.
- **Peer cleanup on the node at startup** (stale peers after a crash) already happens on the first sync. No extra work.
- **Dev:** seeded nodes have no agent, so with the worker on they go `offline` after 2 minutes. Run `qasa-vpn-agent` per node, or leave the worker off.

## 3. Remote notification endpoints — server and dashboard done 2026-11-01

Migration `20261101000006_notification_endpoints.sql`.

**Verification:**
- Unit tests pass.
- `scripts/test-integration.sh` passes: 47 DB tests (3 new in `qasa-db/tests/notifications.rs`) and 16 `qasa-api` tests (1 new router test).
- A live API and worker run covered every item below, including a push delivered in the device's language.
- In the browser, the dashboard sent a Premium-only broadcast against a live API, with no console errors.

**Inbox endpoints** (all take the license token):
- `GET /v1/notifications?limit=&before=` returns newest first, with `unread_count` and `next_before`. The limit is 1–100, default 50. Deleted entries are hidden.
- `GET /v1/notifications/unread-count` returns the badge count.
- `POST /v1/notifications/{id}/read` marks one read and returns the new `unread_count`. It is a 404 when the notification isn't in this inbox.
- `POST /v1/notifications/read-all` marks the whole inbox read.
- `DELETE /v1/notifications/{id}` is a per-user soft delete (`notification_receipts.deleted_at`). A broadcast stays in everyone else's inbox.
- `GET /v1/notifications/{id}` **still marks the notification read**, because current app versions rely on that. New clients should call `/read` when the notification is actually shown. Remove the side effect once both apps do.

**Audience**
- `audience` is now enforced. It must be `all`, `subscribers` (any live subscription, paid or granted) or `free` (none); `targeted` is reserved for one-account notices.
- The admin API returns 400 for anything else, and a CHECK constraint backs that up.
- The fan-out inserts receipts only for that audience, and skips deleted and blocked accounts. Before, every account got every broadcast, so pricing notices meant for subscribers reached free users.

**Push for broadcasts**
- A new worker job (`broadcast_pushes`, every minute, 500 receipts per run) pushes admin broadcasts and pricing notices to each device in its own language. A missing ckb or ar translation falls back to English.
- Each receipt is pushed once (`pushed_at`). Read or deleted receipts, and broadcasts older than 24 hours, are skipped.
- The admin request no longer waits on delivery.

**Pricing notice**
- The body now exists in en, ckb and ar, with grouped thousands, for example "Premium Yearly ئێستا 95,000 دینارە بۆ هەر ساڵ."
- The ckb and ar wording awaits native review with the other 2026-11-01 strings.

**Dashboard**
- The notification form has a "Who gets it" picker: Everyone / Premium / Free.
- The send button and the toast name the audience and the number of accounts reached.
- The sent list shows each broadcast's audience.

### Apps — done 2026-11-02

Both apps behave the same:
- **Mark All Read** now covers both sections. Remote notifications call `read-all`, the list updates right away, and it rolls back on failure.
- **Swipe-to-delete** on "From Qasa" rows calls `DELETE /{id}`. It is optimistic and restores the row on failure; a 404 counts as success.
- **Opening a remote notification** calls `POST /{id}/read`. Deep links still fetch the detail first.
- **New unread badge** on the Home bell. It is the server `unread_count` plus local unread, capped at 99+. It refreshes on Home and when the app comes to the foreground.
- **Paging** loads 50 at a time via `next_before`, as the user scrolls.
- **New error string** `notifications.actionFailed`, in en/ar/ckb.

Android also:
- uploads the FCM token from `onNewToken` and when it changes after unlock
- sends the real `notifications_enabled` value (`areNotificationsEnabled()`)
- sends only `{locale}` on a language change

All prefs calls now go through `push/DevicePrefsSync.kt`. iOS already sent locale-only updates.

**Verification:**
- Android `:app:assembleDebug` builds, and `:app:testDebugUnitTest` passes (75 tests, 0 failures).
- The iOS Simulator build succeeds. The project has no unit-test targets.
- The apps' request shapes were replayed with curl against a live API: paging (50 + 10), read, read-all, delete then 404, and partial prefs updates.
- **Not checked on a device or simulator:** scroll-to-load, the swipe animations and the badge's look.

**Follow-ups:**
- **Localization source: fixed 2026-11-02 (todo `l10n-source-drift`).**
  - **What was wrong.** The generated catalogs had copy that `l10n/strings.json` lacked:
    - 89 keys had been reworded identically on iOS and Android.
    - 30 keys (`home.*`, `tab.*`, `you.*`, `common.back`, `vault.titleYours`) existed only in the outputs.
    - There were no conflicts between the platforms.
  - **What changed.** All of that copy is now in `strings.json`, along with `notifications.actionFailed`.
  - **Generator change.** 19 of the new keys use Android names that can't be derived from the iOS key (`home.allgoodBody` → `home_allgood_body`). The generator therefore supports an optional `"android"` name per row, documented in `l10n/README.md`.
  - **Result of regenerating.** The main iOS catalog, the payment extension and all three Android languages are unchanged in content; only key order differs.
  - **One real copy change.** The AutoFill extension now gets the main app's newer wording for `autofill.noMatchesHint`, `autofill.unlockTitle` and `autofill.wrongPassword`. For example, "Wrong master password." becomes "That's not the right main password." Its catalog had never been updated with that copy.
  - **Verification.** `generate.py --check` passes, and regenerating again changes nothing. The iOS build (app and extensions) and the Android build with 75 unit tests both pass.
- **Android notification permission (todo `android-notif-permission`).** The app declares `POST_NOTIFICATIONS` but never asks for it. Because the real value is now sent, Android 13+ users without the permission are recorded as notifications-off. That is accurate, since the OS was blocking those pushes anyway, but it needs a runtime prompt. The value also syncs only at unlock, not on resume.

## 4. P0: server correctness and security bugs — fixed 2026-11-01

All nine are fixed on the server. Migration `20261101000001_p0_fixes.sql` adds `devices.tokens_valid_after` and `webhook_events.event_status` / `reported_amount_iqd`.

**Verification:**
- `cargo test --workspace` passes.
- `scripts/test-integration.sh` passes: 28 DB tests, 10 of them new.
- An end-to-end curl run against a live API covered every case below.

**What changed:**
1. **The price comes from the plan.**
   - `create_intent` (`qasa-api/src/payments.rs`) loads the plan from `plans` and rejects unknown or inactive plans.
   - It uses the plan's `amount_iqd`. A client `amount_iqd` that differs is now a 400.
   - `normalize_plan_id` resolves only the `yearly` / `monthly` aliases. Dashboard-created plans can now be bought.
   - The silent `plan_amount` fallback is removed.
2. **Unlinked devices lose access at once.**
   - Unlinking sets `devices.tokens_valid_after`.
   - `LicenseAuth` now calls `device::is_license_live`. It requires a linked device, `iat` after the last unlink, and a jti that isn't revoked.
3. **Re-linking works.**
   - `register` finds the unlinked row by fingerprint and reactivates it through the normal device-limit check (`device::reactivate_device`).
   - The reactivated device gets `created_at = now()`, so it can't take over as primary.
   - Tokens from before the unlink stay dead.
4. and 5. **One atomic settlement path.**
   - The new `qasa-db/src/repos/settlement.rs` (`settle_payment`) is used by both the webhook handler and the worker retry job.
   - It locks the payment row (`FOR UPDATE`), checks the gateway matches and checks the amount. It then grants the subscription and marks the payment paid in one transaction.
   - Webhook events store the verified status and amount, which the retry job uses.
   - Terminal events (settled, mismatch, unknown order, failed) are marked `processed`.
   - A repeated "failed" no longer re-sends the renewal-failed push.
   - The non-atomic `payment::mark_status` and `worker::mark_webhook_processed` are removed.
6. **Early payment stacks time.** `activate_subscription` extends from `max(now, current end)` and locks the account row. Plan changes are handled the same way, so monthly → yearly adds 365 days after the current end. A proration rule, if one is wanted, is still a product call.
7. **Idempotency works.**
   - A repeated `idempotency_key` returns the original payment, including under a race.
   - A key that belongs to another account is a 400.
8. **Unknown account → 404** on `link-check`. Still open: `register` will still create an account for any UUID. That is needed for first install, but the pair flow should only call it after link-check passes. Both apps already do this.
9. **`DELETE /v1/breach/monitor` normalises and validates** the hash.

**Client follow-ups these changes need:**
- **Payments (both apps):** both send `amount_iqd` from the price they display (iOS `Services/Payments/PaymentService.swift:26`, Android `payments/PaymentServices.kt:95`). If `/v1/pricing` failed, that price is the hardcoded fallback, and if the dashboard price differs the intent now gets a 400.
  - Either stop sending `amount_iqd`, or, on that 400, refetch pricing and show the real price before retrying.
  - Also send an `idempotency_key` per checkout attempt.
- **Pairing (both apps):** a 404 from link-check currently lands on the generic "check failed" message (iOS `PairExistingVaultView.swift`, Android `PairViewModel.kt`). Map it to "no vault with this code/ID".
- **Unlinking the current device:** after unlinking itself, the device's token is now rejected straight away (401 `token revoked`). Make sure both apps handle that 401 by going to the signed-out or re-register state rather than retrying.

## 5. Lapsed Premium accounts — fixed 2026-11-01

**Decision (product owner, 2026-11-01):** when a subscription runs out, the account falls back to **Free**. The **primary (oldest linked) device keeps working**, and nothing is unlinked automatically.

**Before:** a lapsed account became `Expired`, which meant a device limit of 0. `register` and `GET /v1/license` both returned 403, so a user who stopped paying was locked out on every device. They also couldn't remove their breach monitors.

**What changed:**
- **Entitlements.** `qasa_core::effective_tier` returns Premium only with a live subscription and Free otherwise. The `Expired` tier variant is gone.
  - The worker still writes `accounts.tier = 'expired'` as a "lapsed" marker.
  - `qasa_core::is_lapsed` reads that marker, and the admin user list keeps showing such accounts as `expired`, so the dashboard filter is unchanged.
- **Device limit for already-linked devices.**
  - The new `device::is_within_limit` ranks linked devices oldest-first.
  - `register` and `GET /v1/license` license only devices within the tier's limit. Any other device gets the usual `device_limit_exceeded` 403, with the device list.
  - If a non-primary device unlinks the primary, it takes the free slot.
  - The primary ordering now has an `id` tie-break everywhere it is used.
- **Breach monitors.** `GET` and `DELETE /v1/breach/monitor` no longer require Premium. Registering a monitor still does, and the worker still alerts only Premium accounts.
- **Admin.**
  - "Paid Subscribers" now includes cancelled subscriptions that haven't expired yet (they paid for this period).
  - "Active Free Devices" now counts devices on any account without a live subscription, including lapsed accounts.

**Verification:**
- Unit tests pass.
- The DB integration suite passes: 29 tests, including `only_the_primary_is_within_a_free_limit`.
- A live API run covered the full lapse: the primary registers and refreshes as `free`, the second device gets a 403 on both, monitor list/delete work and monitor register is refused, and after the second device unlinks the primary it registers as `free`.

**Client follow-ups:**
- `GET /v1/license` can now return `device_limit_exceeded`. Before, only `register` returned it. Both apps should route it to the same "free a slot or upgrade" screen as register.
- A device whose existing token hasn't expired can still call Free-tier endpoints (device list/unlink, payments, subscription, notifications) until that token expires. This is deliberate: it lets the user unlink a device or pay again from that device.

## 6. Payments: from stubs to live

**Today:**
- All three gateways are `StubGateway` (`qasa-payments/src/stub.rs`). In prod they refuse to settle, so **nobody can pay in production**.
- The apps open a fake `fib://pay?...` deep link.
- No payment gateway has been chosen or integrated yet (product owner, 2026-11-01).

### Done 2026-11-01 (no gateway needed)

Migration `20261101000002_payments_non_gateway.sql`.

**Verification:**
- Unit tests pass.
- The DB integration suite passes: 35 tests, including 6 new ones in `qasa-db/tests/payments_non_gateway.rs`.
- A live API run checked grant, revoke, history paging and the users-list `accountId`, and a worker run showed which jobs are scheduled.

**What changed:**
- **No false "renewal failed" pushes.**
  - `qasa_payments::RENEWALS_ARE_CHARGED = false` gates every renewal-failure path: the worker job isn't scheduled, and a failed webhook or retry sends no push.
  - While it is false, the 3-day expiry reminder goes to **all** subscribers, auto-renew ones included, since nothing renews by itself.
  - Set it to `true` in the same change that adds recurring charges. (Replaced on 2026-11-04 by per-gateway `supports_recurring()`; the flag is gone.)
- **Abandoned checkouts expire.**
  - A new worker job runs every 5 minutes and marks `pending` payments older than 60 minutes as `expired`.
  - A gateway confirmation that arrives later still settles, because the money moved.
- **Payment history.**
  - `GET /v1/payments?limit=&before=` returns the account's payments newest first (limit 1–100, default 20).
  - Each row has `payment_id, status, gateway, gateway_order_id, amount_iqd, plan_id, auto_renew, subscription_id, created_at, completed_at`, and the response has `next_before` for the next page.
- **Staff Premium grants.**
  - `POST /v1/admin/accounts/{account_id}/grants` takes `{days 1–3650, reason (required), plan_id?}`.
  - A grant is an ordinary subscription row (`amount_iqd = 0`, `auto_renew = false`, plus `granted_by` / `grant_days` / `grant_reason`). Every entitlement check, the expiry job, VPN quota and stacking therefore apply unchanged.
  - It stacks onto any time the account already has.
  - `DELETE /v1/admin/grants/{subscription_id}` takes the granted days back off. It works once, and only while the grant is the current subscription. If a later payment has folded the time into a new row, it is refused as superseded.
  - `GET /v1/admin/grants` lists recent grants.
  - Grants and revokes write `admin_audit_log` in the same transaction. This is the first writer of that table.
  - "Paid Subscribers" leaves out granted rows.
  - `entitlement_overrides` stays unused, so it can be dropped in the cleanup in section 9.
- **Admin users list** now also returns `accountId` (the real UUID). It is needed for grants; `id` is the masked display form.
- **Fixed during verification:** the dev stub built order ids from only the first 8 hex digits of a UUIDv7, which is just a timestamp. Two checkouts within the same ~minute therefore collided on the unique constraint and returned a 500. It now uses the full ref.

### Still pending (needs a gateway)

- **Gateway integrations.** For FIB, FastPay and SuperQi: real `create_intent` (merchant API, QR/deep link, expiry), webhook signature verification, and a status-query API. Keep the stub only in dev.
  - When a gateway's QR/intent has its own expiry, set `ABANDONED_PAYMENT_MINUTES` (`qasa-worker/src/jobs.rs`) to match.
- **Reconciliation.** Ask the gateway for the status of `pending` payments before expiring them, and of `expired` ones for a while afterwards.
- **Recurring charges.** The renewal engine is built (2026-11-04, see ARCHITECTURE.md section 4.8). Each real gateway still needs `create_intent(…, save_method)` to request a token, the token read from its webhook, `charge_saved`, and `supports_recurring()` returning `true` only once the gateway confirms it allows merchant-initiated charges. A gateway that does not leaves its subscribers on manual renewal with the 3-day reminder.
- **Refunds.** These need the gateway's refund API. Revoking a grant is the only "take back" there is today.
- **Native stores.** Neither app uses StoreKit or Play Billing. If either app is distributed through the App Store or Play with digital-goods Premium, store policy may require IAP and server-side receipt validation. Decide this before launch.

**Client and dashboard follow-ups:**
- Apps: a payment-history screen on `GET /v1/payments`. Treat `expired` as "checkout not completed".
- Dashboard: a grant/revoke UI on the users page, a grants list, and show `expired` payments in the payments view.

## 7. Account lifecycle and sync

### Done 2026-11-01

Migration `20261101000003_account_lifecycle.sql` adds `accounts.deleted_at`.

**Verification:**
- Unit tests pass.
- The DB integration suite passes: 39 tests, including 4 new ones in `qasa-db/tests/account_lifecycle.rs` and `repos.rs`.
- A live API run covered every item below.

**Account deletion: `DELETE /v1/account`**
- **Request.** It is authenticated by the license, and the body must repeat `{confirm_account_id}`. The app shows its own confirm step first. The response returns counts of what was removed.
- **Removed, in one transaction** (`qasa-db/src/repos/account_deletion.rs`):
  - devices (names, fingerprints, locations, push tokens)
  - VPN sessions and usage
  - monitored email hashes
  - notification receipts, plus targeted notifications that were written only for this account
  - license revocations
- **Kept as financial records:** payments and subscriptions. Live subscriptions are ended (`cancellation_reason = 'account_deleted'`), and open checkouts are closed as `expired`.
- **Tombstone.** The account row stays with `deleted_at`.
  - `get_account` treats it as missing, so link-check returns 404, admin grants return 404, and the admin lists and counts leave it out.
  - `register` with that id returns **410 `account_deleted`** instead of reviving it.
- **Late payments.** A gateway confirmation that arrives after deletion grants nothing. `settle_payment` returns `AccountDeleted`, and the webhook event records "refund required".

**Device self-unregister and unlink teardown**
- `DELETE /v1/devices/me` lets the calling device unlink itself, for logout, "Clear local data" and vault reset.
- Every unlink (`/me`, `/{id}`, `/others`) now does all of this in one transaction:
  - drops the device's push token and turns notifications off, so an unlinked phone stops getting the account's alerts
  - disconnects its VPN session and recounts the node's peers
  - invalidates its tokens, as before

  This also covers "Unlink/logout don't tear down sessions" in section 2 and "clear the token on unlink" in section 9.

**Breach monitor restore**
- `GET /v1/breach/monitor` now returns a stable `id` per monitor, the full `email_hash` (the account sent it), `label_hint`, `created_at` and the account's `limit`. A new device can rebuild its list from this: it shows `label_hint`, because the server never had the email itself.
- `POST` returns the monitor's `id`.
- `DELETE /v1/breach/monitor/{id}` removes a monitor by id, scoped to the account.

**Breach limit from the server**
- `Entitlements.breach_monitor_limit` is 10 for Premium and 0 for Free.
- It is in the license JWT claims. The claim is `serde(default)`, so tokens issued before it still verify.
- It is also in the register and license responses, and in the monitor list. The API enforces this value instead of a hardcoded 10.

### Still open

- **Per-account salt for email hashes: rejected.** Matching depends on comparing the client's plain SHA-256 against `breach_hashes`, so a per-account salt would make server-side alerts impossible. The privacy model remains that the server holds hashes, not emails.
- **Email leak alerts: parked, much later (todo `email-leak-alerts`, product owner 2026-11-02).** No provider sells SHA-256 email lists, so `qasa-breach ingest` has nothing legitimate to load. Breached-password checks replaced the feature: they run on the device against HIBP Pwned Passwords, free for everyone (ARCHITECTURE.md §4.11). Premium dropped to 5,000 IQD a month / 50,000 IQD a year at the same time (migration `20261102000002_premium_price_5000.sql`).
  - What is switched off: the Alerts tab, Home card and Premium bullet in both apps (commented out where wired), the dashboard's "Breach monitors" tile, and the worker's `breach_alert_scan`. The `/v1/breach/*` endpoints, tables, license entitlement and email screens stay in the code.
  - To bring it back, the worker would query a provider per monitored email instead of matching a local list. Candidates:
    - **HIBP Pro:** `/breachedaccount/range/{prefix}` takes the first 6 hex characters of the email's **SHA-1**, so the apps must also send that prefix (the server only stores SHA-256). From $379 a month. CC BY 4.0 attribution required. Non-matching range results must not be stored.
    - **Enzoic:** partial **SHA-256** email lookups, which match today's hashes. Enterprise plan after vetting; ask for startup pricing.
  - Rough break-even for HIBP Pro: about 100 Premium subscribers at 5,000 IQD a month, before wallet fees.
- **Vault backup/sync: decided no (product owner, 2026-11-01).** There will be no cloud backup endpoint. Vault transfer between devices stays LAN-only (section 1).
- **Recovery: pending, design later (todo `account-recovery`).** There is no account recovery today. Keep `accounts.recovery_email_hash` until the design is done; deletion already clears it. Don't drop it in the section 9 cleanup.

**Client follow-ups:**
- Both apps:
  - Add a "Delete account" flow (confirm step, then `DELETE /v1/account`, then wipe locally). On 410 `account_deleted` from register, start a new vault.
  - Call `DELETE /v1/devices/me` before logout, "Clear local data" and `LocalVaultReset`.
  - Restore monitors from `GET /v1/breach/monitor`, merging by `email_hash`.
  - Read `breach_monitor_limit` from the license instead of `BREACH_MAX_SLOTS` / the hardcoded 10.
- Android: send a `location` on register (`device/DeviceRegistrationService.kt:54` sends `null`).

## 8. Admin and dashboard

### Done 2026-11-01

Migration `20261101000004_admin_hardening.sql`.

**Verification:**
- Unit tests pass.
- The DB integration suite passes: 44 tests, including 5 new ones in `qasa-db/tests/admin_hardening.rs`.
- A live API run exercised every item below, including a viewer getting 403, a demotion applying to an already-issued token, logout, and both login limits.

**Sessions and roles** (`qasa-api/src/auth.rs`)
- `AdminAuth` now checks the database on every request: the admin must exist, `admin_users.disabled_at` must be null, and the token must not be logged out. The token's `role` is replaced by the stored one, so a demotion or disable applies at once.
- A new `AdminWrite` extractor requires `role = 'admin'`. All 13 mutating admin routes use it, so `viewer` gets 403.
- The roles are `admin` and `viewer`, enforced by a CHECK constraint.

**Login hardening**
- At most 10 attempts per email and 30 per client IP every 15 minutes. Over that is a 429. The IP comes from the trusted-proxy rules.
- When the email is unknown, the password is checked against a dummy hash, so response timing doesn't reveal which emails are admins.
- `POST /v1/admin/auth/logout` revokes the token (`admin_token_revocations`). The worker purges a revocation once the token would have expired anyway.

**Audit log**
- `admin_audit_log` is written for:
  - grants and revokes
  - plan upserts
  - notifications
  - license revoke and unrevoke
  - block and unblock
  - admin device unlinks
  - VPN node create, update, rotate and retire
- `GET /v1/admin/audit` (`?target_id=&before=&limit=`) is the read side. It also serves as the "events" feed for the Telemetry page.

**Accounts**
- `GET /v1/admin/accounts/{id}` returns the account, entitlements, all devices (primary marked), the last 20 subscriptions (grants included), the last 20 payments, monitor count, VPN usage, and that account's audit entries.
- `POST` and `DELETE /v1/admin/accounts/{id}/block` block (`{reason}` required) and unblock an account.
  - While blocked, the account's tokens fail (`is_license_live` checks the account), `register` returns 403 `account_blocked`, and VPN sessions end.
  - Unblocking restores tokens that haven't expired.
  - The user list shows status `blocked`.
- `DELETE /v1/admin/accounts/{id}/devices/{device_id}` unlinks a device with the full teardown.

**VPN nodes** (`qasa-api/src/admin_vpn.rs`)
- `POST /v1/admin/vpn/nodes` validates the IP, the WireGuard key (base64, 32 bytes), the port, max peers and the pool CIDR.
  - It returns a random 256-bit agent token **once**; only its hash is stored.
  - New nodes start `draining`.
- `PATCH` edits name, city, max peers, port and key, and sets `online` or `draining`.
- `DELETE` retires a node (soft, `status = 'retired'`). It is refused while the node has live sessions.
- `POST …/agent-token` rotates the token, and the old one stops working at once.
- Heartbeats no longer overwrite `draining` or `retired`.
- The node list now includes `id`, `maxPeers`, `endpointPort` and `lastHeartbeat`. Uptime leaves out retired nodes.

**Payments, webhooks, charts**
- `GET /v1/admin/payments` takes `?q=` (order-id prefix or account id), `&status=`, `&gateway=`, `&limit=` and `&before=`. It returns `next_before`, and each row has `paymentId`.
- `GET /v1/admin/payments/{id}` returns the payment with every webhook event for its order. This replaces the payload sheet the dashboard used to make up.
- `GET /v1/admin/webhooks?gateway_order_id=` filters by order.
- `GET /v1/admin/timeseries?days=` returns daily revenue per gateway, daily signups, daily new paid subscriptions and the gateway mix. It reads the tables directly; `mv_revenue_daily` is still unused, so it can be dropped in section 9.

**Users list**
- One query, instead of three per account.
- `?q=` matches an account-id prefix or the masked public id; `&limit=` (default 200, max 500) and `&before=` page through it.
- It is still a bare array, so the dashboard is unaffected. The next cursor is in the `X-Next-Before` header.

**MRR**
- The KPI now sums live paid subscriptions at their monthly equivalent (amount × 30 / plan days). For example, a 100,000 IQD yearly plan counts as about 8.2K. Grants are left out.
- The old figure is still shown, as "Collected This Month".

### Still open

- **2FA for admin login: pending (todo `admin-2fa`).** There is no OTP method yet (2026-11-01). Revisit once one is chosen (TOTP app, SMS or email).
- **Admin-user management API: not needed** (product owner, 2026-11-01). Admins are managed with SQL, as the README documents.
- **Dev seed.** `admin@qasa.io` / `admin123` is created only when `QASA_ENV=dev`. Don't use it, or the dev `.env`, in any shared environment.
- **Rate limiting** is in memory per API process. The shared limiter is in section 9.
- **Dashboard wiring:** moved into section 9.

## 9. Ops and hygiene — server done 2026-11-01

Migration `20261101000005_ops_hygiene.sql`.

**Verification:**
- Unit tests pass.
- `scripts/test-integration.sh` now runs 44 DB tests plus 14 `qasa-api` tests (8 new router tests), and all pass.
- A live API run checked the limits, the CORS header, the dropped schema and the worker job list.
- `docker compose config` refuses to start without its secrets, and `caddy validate` accepts the Caddyfile.

**TLS and prod compose**
- A Caddy service (ports 80/443, auto TLS for `QASA_PUBLIC_HOST`) fronts the API. The API no longer publishes 8080.
- Caddy drops client-sent `X-Forwarded-For`, blocks `/metrics` publicly, and sets HSTS, `nosniff` and `no-referrer`.
- The compose network is fixed at `172.30.0.0/24`, and `QASA_TRUSTED_PROXY_CIDRS` defaults to it. The admin CIDR guard and the rate limits therefore see real client IPs instead of Caddy's.
- `POSTGRES_PASSWORD`, `QASA_ADMIN_JWT_SECRET` and `QASA_LICENSE_PRIVATE_KEY` are required (`:?`). Postgres no longer defaults to `qasa`.
- Redis is removed from both compose files; nothing used it.

**Config**
- The `DATABASE_URL` default is now `localhost:5433`, matching the dev compose.
- In prod there is no default, and the API refuses to start without it.

**Shared rate limiter**
- `rate_limit_buckets` is an UNLOGGED Postgres table with fixed windows and an atomic upsert per key (`qasa-db/src/repos/rate_limit.rs`). It replaces the per-process in-memory limiter, which reset on restart and multiplied with each replica.
- Limits are defined in `qasa-api/src/limits.rs`:
  - breach prefix lookup: 60 per hour, per account. It now counts only after the premium check.
  - admin login: 10 per email and 30 per IP, every 15 minutes
  - **new:** `register`: 30 per account and 600 per IP, every 10 minutes
  - **new:** `link-check`: 120 per IP every 10 minutes
- Per-IP allowances are high on purpose because of carrier CGNAT.
- A worker job purges old windows. The old in-memory `qasa_breach::RateLimiter` is deleted.
- A router test confirms two API instances share one count.
- Webhooks are not limited: they are the gateway's retries, and an invalid signature is already refused.

**FCM dead tokens**
- An FCM `UNREGISTERED` (404), or an `INVALID_ARGUMENT` that names the registration token, now clears `devices.push_token`. Payload, auth, quota and server errors keep the token.
- This has unit tests (`qasa-push`).

**Device prefs**
- `PATCH /v1/devices/prefs` updates only the fields that are sent. An empty `push_token` clears it, and the response returns the stored values (section 3).

**Unused schema dropped**
- `entitlement_overrides`, `app_settings`, `mv_revenue_daily` and `mv_active_subscribers` are dropped, along with the worker's refresh job.
- `accounts.vault_initialized` is dropped, along with its write on every register.
- Kept: `vpn_sessions.bytes_*`, for VPN metering in section 2, and `recovery_email_hash`, for the recovery design.

**API tests**
- `qasa-api/src/router_tests.rs` drives the real router with HTTP requests. It covers:
  - an unlinked device getting 401, then re-linking
  - the price coming from the plan
  - prefs PATCH semantics
  - the device-limit 403 body
  - link-check 404 and account deletion / 410
  - lapse to Free with the breach limit
  - viewer 403 and logout
  - a limiter shared across instances
- The integration script runs these after the `qasa-db` suite.

**CORS**
- `X-Next-Before` is exposed, so the dashboard can read the users-list cursor.

**Not done**
- **Admin access over Tailscale through Caddy.** With `QASA_ADMIN_ALLOW_CIDRS=tailscale`, admin requests must reach Caddy from a Tailscale address. That means a Caddy site for the host's Tailscale name, or the dashboard calling the API over the tailnet. Decide this when deploying.

### Dashboard wiring — done 2026-11-01

All the admin endpoints from sections 6–8 are now used by `apps/dashboard/`.

**What changed:**
- **Session:** role and email are stored at login. Logout calls the endpoint. A 401 sends the user back to login, and the error messages explain 403 (read-only) and 429.
- **Users:** search and "Load more" run server-side, using `X-Next-Before`. Block and unblock take a reason.
- **New account page** (`/users/:accountId`):
  - devices, with unlink
  - subscriptions, with grant and revoke-grant
  - payments
  - VPN usage and breach monitors
  - staff audit
  - block and unblock
- **Payments:** status filters (including `expired`), search and paging. The payment sheet now shows the real payload and the webhook events, replacing the fabricated one. The gateway mix uses the time series, and CSV export is real.
- **Overview:** the growth chart and the gateway mix use `/v1/admin/timeseries`, and the "Collected This Month" KPI shows.
- **VPN nodes:**
  - create, edit, online/drain, rotate token and retire
  - the agent token is shown once, with a copy button
  - each node shows its last heartbeat and peers / max peers
- **Telemetry:** the events list is the admin audit feed, with paging.
- **Viewers:** write controls are hidden on Users, Account, VPN, Pricing and Notifications. Demo mode (no `VITE_QASA_API_URL`) still runs on mock data.

**Verification:**
- `bun run build` (vue-tsc + vite) passes.
- A contract script runs the dashboard's own `src/api/*.ts` against a live API. All 43 checks are good: 42 pass, and one fails only because it hard-codes a 10,000 IQD amount while the run priced the plan at 15,000.
- The agent that did the wiring also walked through it in a browser as both admin and viewer, and checked demo mode.

**Still mock (no endpoint behind them):** the decorative network-traffic chart on the VPN page, and the region and resources cards on Telemetry.

**Behaviour the dashboard relies on (intended):**
- A rejected webhook (for example, an amount mismatch) is stored `processed = true` with an `error_message`, so the retry job leaves it alone. The UI shows it as "rejected".
- Revoking a grant that was stacked on paid time leaves the subscription `active` (the paid days remain). `grant_revoked_at` marks the revoke.
- Grants on a blocked account are allowed by the API, though they give no access while the block lasts. The UI disables the button.

## Not in scope here (tracked elsewhere)

- Native ar/ckb review of every string key added on 2026-11-01 (product owner will check).
- Side-by-side device QA in en/ar, light/dark (product owner will check).
