# Qasa Android

Kotlin + Jetpack Compose password manager matching the Qasa iOS app — local vault, licensing, payments, breach monitoring, VPN session state, autofill, and FCM.

## Package

`alibarznji.qasa` (matches iOS `PRODUCT_BUNDLE_IDENTIFIER`)

## API URL

Debug builds use `http://10.0.2.2:8080` (emulator → host loopback).

| Environment | URL |
|-------------|-----|
| Emulator | `http://10.0.2.2:8080` |
| Physical device | `http://<your-mac-lan-ip>:8080` |

Override via `buildConfigField` `QASA_API_URL` in `app/build.gradle.kts`.

Start the API:

```bash
cd server && cargo run -p qasa-api
```

## Build

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Optional Firebase: place `app/google-services.json` to enable FCM token upload. Without it, push degrades gracefully (same as iOS without `GoogleService-Info.plist`).

## Architecture

```
app/src/main/java/alibarznji/qasa/
  vault/       # PBKDF2 + AES-GCM + Room + blind search
  network/     # OkHttp API client + DTOs
  license/     # JWT parse + refresh
  device/      # Register + list/unlink
  payments/    # Payment intents + polling + subscription
  breach/      # SHA-256 k-anonymity
  vpn/         # Connect/status/disconnect (API only)
  credentials/ # AutofillService + Credential Manager provider
  push/        # FCM MessagingService + token store
  screens/     # Compose UI (welcome → shell → vault/settings/…)
```

## Crypto (must match iOS)

- PBKDF2-HMAC-SHA256, **310_000** iterations, 32-byte salt/root
- Verifier = SHA-256(root)
- HKDF-SHA256 infos `qasa-enc` / `qasa-search` (empty salt → 32 zero bytes)
- AES-256-GCM combined `nonce||ciphertext||tag`

### Unlock flow

1. Create vault → PBKDF2 root key, AES-GCM item encryption, HMAC search tokens.
2. `account_id` + `device_fingerprint` stored in EncryptedSharedPreferences / Keystore.
3. On unlock → `POST /v1/devices/register` (`platform: android`) → license JWT.
4. `AppSession.isPremium` derives from JWT `tier`.

## Manual QA checklist

### Vault (local)

- [ ] Create vault with master password; kill and relaunch app.
- [ ] Unlock with password; list loads; add/edit/delete login; search by name.
- [ ] Lock from Settings; data not visible without password.
- [ ] Auto-lock after configured minutes / return from background past threshold.
- [ ] Biometric unlock when enabled.
- [ ] Collections CRUD; Password Health scores logins; Generator Save is no-op.
- [ ] Suggested section mock on vault list; NFC button is no-op; CSV export empty stub.

### API / registration

- [ ] Server running; unlock registers device (`platform: android`).
- [ ] Settings → Linked Devices shows this device.
- [ ] Free tier: second device registration returns limit error.
- [ ] Pair existing vault (UUID account id) creates local vault.
- [ ] LAN sync UI: premium gate + placeholder blob `qasa-vault-placeholder:{accountId}`.

### Premium / payments

- [ ] Upgrade → monthly/yearly + auto-renew → FIB/FastPay/SuperQi.
- [ ] Deep-link failure uses DEBUG stub webhook; license refresh sets Premium.
- [ ] Settings → Manage Subscription: details, auto-renew, cancel + reason.
- [ ] Breach Monitoring and VPN menu entries route to upgrade when Free.

### Breach monitoring (Premium)

- [ ] Add email → prefix lookup runs (requires ingested breach hashes on server).
- [ ] Monitored email status updates to At Risk when hash matches.
- [ ] Free tier: 2 monitor slots; Premium: 10.

### VPN (Premium, API state only)

- [ ] Connect toggles API session; status shows node/city/assigned IP.
- [ ] Disconnect clears session. No WireGuard tunnel yet.
- [ ] Kill-switch & auto-connect are local toggles only.

### OS integration

- [ ] Enable Qasa in system Autofill / Credential provider settings.
- [ ] Autofill fills login fields from vault index after unlock.
- [ ] FCM: with `google-services.json`, token uploaded via `PATCH /v1/devices/prefs`; tap opens Notifications.
- [ ] Notifications page shows API inbox + locale titles (en/ckb/ar).

## Notes

- VPN WireGuard `VpnService` is **out of scope** for this parity pass.
- Room `fallbackToDestructiveMigration` is **debug-only** (mirrors iOS SwiftData wipe-in-dev).
- Real encrypted vault sync/backup, TOTP, passkeys beyond provider scaffolding — later cross-platform phase.
