# Qasa iOS

SwiftUI password manager wired to the Qasa Rust API for licensing, payments, device limits, breach monitoring, and VPN session state.

## API URL

The app reads `QASA_API_URL` from Info.plist (generated via Xcode build setting `INFOPLIST_KEY_QASA_API_URL`).


| Environment     | URL                             |
| --------------- | ------------------------------- |
| Simulator / Mac | `http://localhost:8080`         |
| Physical device | `http://<your-mac-lan-ip>:8080` |


To change for a device build, edit **qasa** target → Build Settings → `QASA_API_URL`, or override in a `.xcconfig`.

Start the API from the repo root:

```bash
cd server && cargo run -p qasa-api
```

`NSAppTransportSecurity` allows local networking for HTTP in development.

## Architecture

```
qasa_ios/
  Config/QasaConfig.swift          # Base URL helpers
  Services/Vault/                  # PBKDF2 + AES-GCM vault, blind search index
  Services/Network/                # QasaAPIClient + DTOs
  Services/License/                # JWT parse + refresh
  Services/Device/                 # Register + list/unlink devices
  Services/Payments/               # Payment intents + polling
  Services/Breach/                 # Pwned Passwords check (on device); email leak alerts parked
  Services/VPN/                    # Connect/status/disconnect (API only)
  Services/Identity/AppBootstrap.swift
```



### Unlock flow

1. User creates vault → PBKDF2 root key, AES-GCM item encryption, HMAC search tokens.
2. `account_id` + `device_fingerprint` generated and stored in Keychain.
3. On unlock → `POST /v1/devices/register` → license JWT stored in Keychain.
4. `AppSession.isPremium` derives from JWT `tier`, not UserDefaults.



## Manual QA checklist



### Vault (local)

- [x] Create vault with master password; kill and relaunch app.
- [x] Unlock with password; list loads in <300ms for ~2k items (seed creates a small sample).
- [x] Add/edit/delete login; search by name finds item.
- [x] Logout locks vault; data not visible without password.



### API / registration

- [x] Server running; unlock registers device (check server logs or admin dashboard).
- [x] Settings → Linked Devices shows this device.
- [x] Free tier: second device registration returns limit error in Linked Devices banner.



### Premium / payments

- [x] Upgrade → monthly/yearly carousel + auto-renew → Wayl (its page offers FIB, Qi Card and other wallets).
- [x] After webhook/simulated payment, license refresh sets Premium.
- [x] Settings → Manage Subscription (premium): details, auto-renew, cancel + reason.
- [x] Breach Monitoring and VPN menu entries accessible when Premium.



### Breach monitoring (Premium)

- [x] Add email → prefix lookup runs (requires ingested breach hashes on server).
- [x] Monitored email status updates to At Risk when hash matches.



### VPN (Premium, API state only)

- [x] Connect toggles API session; status shows node/city/assigned IP.
- [x] Disconnect clears session. No WireGuard tunnel yet.



### Regression

- [x] Password Health still scores login items.
- [x] Collections count matches items assigned to collection.
- [x] Protected notes require biometrics to reveal/copy.



## Notes

- VPN WireGuard Network Extension is **out of scope** for this pass.
- Schema changes reset the local SwiftData store in development (see `qasaApp.swift`).
- License JWT TTL is 30 days; refresh via `POST /v1/license/refresh` on unlock/bootstrap.

