# Desktop app and browser extension plan

Qasa for macOS, Windows and Linux (`apps/desktop/`), plus a thin browser extension
(`apps/extension/`) that fills passwords from the desktop app. Decided 2026-11-08 (product
owner): native desktop instead of a browser-hosted vault, so the rule in
[ARCHITECTURE.md §1](../ARCHITECTURE.md) — the vault never leaves the device — stays true.

**Source of truth for design and behaviour: the iOS app.** As in
[`docs/design.md`](design.md), `apps/ios/qasa_ios/` wins when anything disagrees. Two
warnings before using `design.md` as a reference:

- **Colors come from `design/tokens.json`** (brand purple `#722F99`, grey `#C5C1C1`, beige
  `#FEE7C8`, peach `#FF9292`), generated into every app by `design/generate.py`.
- **Its shell section is out of date.** The app now has four tabs (Home, Passwords, Alerts,
  You) in `AppShellView.swift`, not a side menu.

## Progress log

**2026-11-08 — Phase 6: release pipeline (ready, waiting on certificates).**
- **Desktop app:**
  - nm-host is a bundled sidecar: `scripts/sidecar.ts` builds it for each target, lipo'd
    for universal macOS.
  - The icon has the macOS shape (`scripts/app-icon.py`).
  - Hardened runtime with no entitlements.
  - `tauri-plugin-updater`: a quiet check 10 s after launch, Settings → Check for updates,
    and an "Install and restart" prompt. The archives are signed with
    `~/.tauri/qasa-desktop-updater.key`.
  - A local release build bundled `qasa-nm-host` and signed `Qasa.app.tar.gz`.
- **Fix:** the four `bridge_*` commands were missing from the isolation allow-list, which
  would have blocked the extension prompts in the real app. A test now checks every
  registered command against it.
- **CI:**
  - `desktop-release.yml` (tag `desktop-v*`): universal dmg, NSIS and msi, AppImage and
    deb; a draft release with `latest.json` and version-free asset names. Apple and Azure
    signing are used only when their secrets exist.
  - `clients-ci.yml`: tests for the core, desktop and extension.
  - `extension-release.yml`: the store zips.
- **Server:** Caddy redirects `qasa-app.com/desktop/latest.json` and
  `/desktop/download/<os>` to the latest published GitHub release. Not deployed yet.
- **Landing:** a "Qasa on your computer" section with OS detection, and `/download` pages
  in en, ar and ckb.
- **Extension:** `bun run package` makes the store zips (no `key`) and a source zip whose
  build reproduces byte-identical output. `store/listing.md` has the listing text,
  permission justifications and privacy answers.
- **Runbook:** [`desktop-release.md`](desktop-release.md).
- **Still to do, by the account owner:**
  - Developer ID certificate and notarization secrets.
  - Azure Trusted Signing.
  - The updater key as a secret.
  - Deploy the Caddyfile.
  - Tag and publish the first release.
  - Submit the extension to the stores, then add the store ids to `CHROME_EXTENSION_IDS`
    and release the desktop app before the listings go live.
  - A privacy policy page on qasa-app.com, which the stores require.
- **Not checked:**
  - The workflows haven't run on GitHub yet.
  - Signed, notarized and Windows/Linux builds.
  - An update installing over an older version.

**2026-11-08 — Phase 5: the browser extension.**
- **Protocol:** written down in §8.2.1:
  - P-256 pairing with a 6-digit code on both screens;
  - AES-256-GCM requests bound to the client id;
  - timestamp and replay checks;
  - every fill checked by the desktop against the frame's own site (score ≥ 60).
  Cross-checked by a shared vector that both the Rust and the WebCrypto code pass.
- **`apps/desktop/nm-host`:** std-only relay, built by `make nm-host`.
- **Desktop app:**
  - Registers nm-host on every launch: user-level manifests for Chrome, Chromium, Edge,
    Brave, Vivaldi and Firefox on macOS and Linux; on Windows, per-user registry keys
    through `reg.exe`, which are untested.
  - Listens on `bridge.sock` (0600) or the per-user named pipe.
  - Matching uses the Public Suffix List, so `x.github.io` and `y.github.io` are different
    sites, and IP addresses and `localhost` are their own sites.
  - The pairing prompt and the save/update prompts appear over any screen.
  - Settings → Browser extension lists the allowed browsers, with Remove.
  - Pairings are kept in the keychain.
- **`apps/extension`:**
  - MV3 build for Chrome and Firefox.
  - The popup reuses the desktop's tokens, components, fonts and i18n.
  - The content script finds login, signup, change-password, multi-step and one-time-code
    fields, including in shadow roots and frames.
  - The in-page menu sits in a closed shadow root, with the clickjacking checks of §8.3.
  - Save capture runs on submit.
  - 33 `ext.*` strings, with drafted Arabic and Sorani for review.
- **Tests:**
  - Extension: unit 43; Chromium e2e 33 (real nm-host, fake desktop); one e2e against
    the desktop's real Rust bridge (`bun run e2e:real`: pair, per-site fill, update, new
    save).
  - Desktop: 35, of which 18 are bridge tests.
  - nm-host: 3.
  - `web-ext lint`: 0 errors.
- **Not yet checked:**
  - Fixtures in Firefox. Its host manifests live outside the profile on macOS, so a test
    run would touch the real user folder.
  - Real Chrome or Edge (Chromium 131 only).
  - The toolbar popup itself (tests open `popup.html` as a page).
  - Windows registration.
  - Dark-mode screenshots.
- **Known gaps:**
  - The in-page menu uses system fonts.
  - The multi-step username lives only in the service worker's memory.
  - The Chrome `key` field must be dropped for store builds, and nm-host must become a
    bundled sidecar (`externalBin`); both are phase 6.

**2026-11-08 — Phase 4, second half: the phones.**
- `core/qasa-ffi`: UniFFI 0.28 bindings over `qasa-sync` (`SyncHost`, `sync_join`, a
  `SyncStoreCallback` the app implements, typed `FfiSyncError`s) plus
  `payload_to_canonical`/`payload_to_android`. `make ios-ffi` builds an XCFramework,
  `make android-ffi` builds `jniLibs` with `cargo-ndk`; both outputs are gitignored.
- Core fixes found on the way: item and collection IDs are uppercased on the wire (iOS
  writes uppercase UUIDs, Android lowercase), and a joiner with a wrong code now sends an
  empty confirm frame so the host reports a wrong code instead of a dropped connection.
- iOS: `deletedAt`/`updatedAt` tombstones in SwiftData, `NWBrowser` discovery, host and
  join panels in Linked Devices, real pairing on "I already have Qasa", the code no longer
  in the Bonjour name, the old `LANVaultSyncService` removed.
- Android: Room v1→v2 migration for tombstones, `NsdManager` discovery, `WifiSyncService`
  and the same panels; IDs lowercased on the way in and compared case-insensitively.
- Strings: 24 `sync.*`/`pair.*` keys promoted into `l10n/strings.json`.
- Tests: core 77, desktop 17, Android 120, iOS 33 — all pass. End to end: a desktop-side
  host synced its vault into a fresh iPhone simulator, which then unlocked with the same
  password and showed every item in its folder.
- **Not yet checked:** Android on a real device or emulator, and phone ⇄ desktop through
  the desktop window itself (the host used was the core's demo binary). IPv6-only peers
  are not supported.

**2026-11-08 — Phase 4, first half: the protocol and the desktop (phones not yet).**
- `core/qasa-sync`: the code is stretched with Argon2id (19 MiB, 2 passes, per-session salt)
  into SPAKE2; both sides confirm the key (HMAC over the transcript, constant-time check,
  host first) before anything else; then AES-256-GCM frames with per-direction keys and
  counters. Items cross still encrypted with the vault key. Intros decide the plan: a
  device without a vault adopts the other's vault and account (only inside the
  authenticated channel, closing the rebinding hole); same vault → both merge; different
  vaults or none → refused, nothing changes. Merge: newest `updated_at` wins per item and
  collection, ties break identically on both sides, deletes are tombstones kept 90 days.
  11 tests over real local TCP (adopt, two-way merge with a delete, different vaults,
  wrong code moves nothing and rebinds nothing).
- Desktop: snapshots read raw rows (tombstones included); applied rows are written as
  ciphertext, and their search tokens are rebuilt at the next unlock; adopting never
  overwrites an existing vault. Collections gained `updated_at` (migration for existing
  files). Hosting advertises `_qasa-sync._tcp` under the computer's name — never the code,
  which iOS currently puts in the service name — on a random port; a code works for one
  session, 3 attempts or 2 minutes. mDNS round trip checked on this network.
- Screens: Settings → Linked Devices → Sync on this network (Premium; host shows the code
  with a countdown, join lists devices and takes the code), and "I already have Qasa on
  another device" on Create vault, which copies the vault and sends you to Unlock.
- **Still to do in phase 4:** the phone ports — UniFFI builds of `qasa-sync` (XCFramework,
  `cargo-ndk`), `deletedAt` tombstones in SwiftData and Room, iOS `NWBrowser`/Android
  `NsdManager` discovery feeding the shared session, Android's payload shape mapped to the
  canonical (iOS) one, the new `sync.*` strings promoted into `l10n/strings.json`, and
  removing the code from the iOS service name. Until then the desktop can only sync with
  another desktop.
- Build note: the disk filled during this phase; build with `CARGO_INCREMENTAL=0`.

**2026-11-08 — Phase 3 built (Touch ID checked on hardware; Windows Hello type-checked only).**
- `apps/desktop/biometric` (`qasa-biometric`): seals the 32-byte root key with AES-256-GCM
  under a key only the platform can produce after the user authenticates.
  - macOS: ECDH between a Secure Enclave P-256 key created with `.biometryCurrentSet`
    (via a small Swift bridge, `swift/`, built by `swift-rs`) and a software ephemeral key.
    Works in unsigned dev builds: CryptoKit's Enclave keys need no keychain entitlement.
    Adding or removing a fingerprint destroys the Enclave key, so the seal fails closed.
  - Windows: a Windows Hello key (`KeyCredentialManager`, RSA PKCS#1 v1.5, deterministic)
    signs a stored random challenge. Creating it asks for Hello once. Type-checked with
    `cargo check --target x86_64-pc-windows-msvc` (rustup's toolchain); **never run**.
  - Linux: not offered.
- Vault: the iOS rule — turning it on seals at the next main-password unlock (or right away
  when chosen on Create vault); a master-password change reseals; a seal that no longer
  matches the verifier is dropped and the next password unlock makes a new one; Clear local
  data removes it. The prompt runs outside the vault lock.
- UI: the Create vault card (on by default, as iOS), the Unlock row, Settings → Fingerprint
  or face unlock with iOS's three subtitles, and protected notes ask for Touch ID / Hello
  first, falling back to the main password.

**2026-11-08 — Phase 2 built.**
- Desktop core `src-tauri/src/account/`: account id, device fingerprint, license token and
  the cached verifying key in the OS keychain (`keyring`); the API client (debug:
  `QASA_API_URL` or `http://localhost:8080`; release: https only); Ed25519 license
  verification that fails closed (bad signature, expiry, `alg: none`), and never lets a bad
  token replace a good one; registration with the device-limit answer kept for the UI;
  license refresh with a retry after key rotation; locale sync (also on a language
  change); the Pwned Passwords check (5-character SHA-1 prefix, `Add-Padding`, cached per
  unlock, cleared on lock); devices, inbox, pricing, payment intents and status (license
  refreshed on success), subscription, payment method, clear local data (server unlink
  first, best effort) and account deletion (server first, then the local wipe).
- Bootstrap runs after unlock and after vault creation and never blocks the vault: offline
  just means Free features until the next unlock.
- The inbox has no push on desktop: the unread count is checked on unlock and every 5
  minutes.
- Screens: the You profile card with plan and Upgrade, the Home bell with an unread badge,
  the breached card and Alerts tab on Premium (found / none / offline, "seen N times"),
  Health's breach stat, Settings' profile card, Linked devices section and **Delete
  account** (new; the phones only have it in their follow-up list), Premium upgrade,
  Manage subscription, Linked devices, Notifications.
- Payments on a computer: Wayl's https page opens in the browser; a wallet deep link
  (`fib://…`) is shown as a QR code for the phone. Development builds can confirm stub
  wallet payments, as iOS Debug does.
- Checked against a local API (`live_account_flow` test, ignored by default): register,
  verified license, devices, inbox count, stub FIB payment → Premium (5 devices, active
  subscription), the real Pwned Passwords range API, account deletion → token rejected.

**2026-11-08 — Phase 1 built (not yet checked by hand in the app window).**
- `core/qasa-vault` gained the payload model (`payload.rs`: reads iOS's `{"login":{"_0":…}}`
  and Android's `{"type":…}` shapes, writes the iOS shape), the country list, 2FA codes
  (`totp.rs`), CSV import/export (`csv.rs`), password health (`health.rs`) and the
  generator (`generator.rs`). 61 tests.
- Desktop core (`src-tauri/src/vault/`): SQLite vault with tombstones and per-item search
  tokens, create/unlock (raw then trimmed password, as iOS), lock, auto-lock timer
  (15 s tick, prefs minutes, UI reports activity), save/delete/list, folders, change
  master password as one all-or-nothing re-key, clear local data, health, generator,
  2FA, CSV through native file dialogs, secret copying with a 30 s clear for card/bank
  fields that only clears if Qasa's value is still there, and an http(s)-only
  open-website command.
- Screens: Welcome, Create vault, Unlock, the sidebar shell (Home, Passwords, Alerts,
  You), Passwords as list + detail, add-item drill-down, the editor for all 13 types,
  generator (page and sheet), Password health with issue lists, Folders, Settings
  (Change main password, Auto-lock, Appearance, CSV, Clear local data, Logout), Terms
  and Privacy.
- Desktop-specific choices:
  - Protected notes ask for the main password once per unlock (iOS asks for Face ID or
    the passcode); Touch ID / Windows Hello replace this in phase 3.
  - 2FA QR codes are read from an image file (a screenshot) instead of the camera.
  - The Suggested section is left out: on iOS it comes from a mock context provider.
  - iOS no-ops are implemented or dropped: generator "Save to my passwords" opens a new
    login; the disabled "Pronounceable" toggle is gone.
  - Phone-only wording ("on this phone") has desktop overrides in
    `l10n/platform/desktop.json`, with drafted ar/ckb text for a native speaker to review.
- Left for later phases: account rows (profile, plan, upgrade, linked devices, pairing,
  notifications, VPN), biometrics, Wi‑Fi sync, and the browser-extension row in Settings.
- Checked: Rust tests (core 61, desktop 9 including every item type in the UI's JSON
  shape), `vue-tsc` and the production build, every screen in a browser against an
  in-memory stand-in for the core, and the real app starting and creating its vault
  file. **Not checked:** clicking through the real app window (macOS blocks screen
  capture from this session), and the native file dialogs.

**2026-11-08 — Phase 0 done.**
- `core/qasa-vault`: PBKDF2/HKDF key derivation, the verifier, AES-256-GCM, kdfVersion
  encoding and the blind search index. `tests/vectors.json` holds the golden vectors
  (7 KDF cases including Arabic, Sorani, emoji, a legacy zero salt and a 16-byte salt;
  3 AES-GCM blobs; 10 search cases).
- The same vectors pass on iOS (`qasaTests/CoreVectorTests.swift`) and Android
  (`vault/crypto/CoreVectorTest.kt`).
- **Android bug fixed on the way:** its search tokenizer split on `[^a-z0-9]`, so Arabic and
  Kurdish text produced no tokens and could not be searched (design.md §12.4). It now
  matches iOS. Items saved before the fix get Arabic/Kurdish tokens the next time they are
  saved or the master password changes; there is no reindex-on-unlock yet.
- `apps/desktop`: Tauri 2 + Vue scaffold with the isolation pattern and a strict CSP;
  prefs saved by the Rust core; tokens generated from `Theme.swift`
  (`bun run tokens`, `--check`); IBM Plex Sans Arabic bundled; RTL root; the Welcome screen as
  the first iOS screen ported.
- `l10n/generate.py` writes `apps/desktop/src/i18n/{en,ar,ckb}.json`
  (`%@` → `{0}`), covered by `--check`. Desktop-only strings go in
  `l10n/platform/desktop.json`.
- Not done yet: the macOS icon is the iOS full-bleed square; macOS expects the rounded
  shape with margins (phase 6).

---

## 1. Scope

**In v1**

- Everything the phone apps do on the vault: create, unlock, auto-lock, every item type,
  search, collections, generator, 2FA codes, password health, breached-password check, CSV
  import and export.
- Account and server features: device registration, licensing, linked devices, inbox,
  Premium upgrade and subscription management, account deletion.
- Biometric unlock on macOS (Touch ID) and Windows (Windows Hello).
- Wi‑Fi vault sync with the phones, which also means building the real protocol on iOS and
  Android (section 6).
- The browser extension for Chrome, Edge, Brave and Firefox.

**Not in v1**

- VPN. The Home VPN card and the You → VPN row point to the phone app instead (decision 2).
- Email leak alerts: still parked on the phones.
- A Safari extension. The macOS app can add one later through Xcode's Safari Web Extension
  wrapper around the same code.
- The payment-notification extension. It is an iOS-only surface.

---

## 2. Repository layout

```
qasa/
├── core/                         NEW Rust workspace shared by every client
│   ├── qasa-vault/               crypto, payload model, search index, CSV, health, TOTP, generator
│   ├── qasa-sync/                LAN discovery, PAKE pairing, transfer, merge
│   └── qasa-ffi/                 UniFFI bindings for iOS and Android (phase 4)
├── apps/
│   ├── desktop/                  NEW Tauri 2 app
│   │   ├── src-tauri/            Rust: commands, storage, keychain, native messaging IPC
│   │   ├── nm-host/              Rust: the native messaging host binary
│   │   └── src/                  Vue 3 + TypeScript UI
│   ├── extension/                NEW Manifest V3 extension (Vue 3 popup, TS content scripts)
│   ├── ios/  android/  dashboard/
├── l10n/                         generate.py gains a web output for desktop + extension
```

- **`core/` is top level, not under `server/`.** The server never links vault code, and the
  phones will link `qasa-sync` through UniFFI. This settles the open crate-location
  question in [`server-followups-plan.md` §1](server-followups-plan.md).
- **One bun workspace for `apps/desktop` and `apps/extension`.** The extension imports the
  desktop's theme, icons and i18n runtime through a path alias. No third package.
- **Tooling:** Vue 3.5, TypeScript, Vite and bun, like the dashboard. No Tailwind or
  shadcn: components are scoped CSS over the generated tokens, so they read like the iOS
  components they copy.
- **Makefile targets:** `make desktop`, `make desktop-build`, `make extension`,
  `make core-test`.

---

## 3. Architecture

```
┌──────────── apps/desktop (one signed app) ─────────────┐
│ Webview (Vue)  ──Tauri commands──▶  Rust core process   │
│  screens only, never keys         ├─ qasa-vault (crypto) │
│                                   ├─ SQLite vault store  │
│                                   ├─ OS keychain         │
│                                   ├─ reqwest → qasa-api  │
│                                   ├─ qasa-sync (mDNS/TCP)│
│                                   └─ local IPC socket ◀──┼── nm-host ◀── browser ◀── extension
└─────────────────────────────────────────────────────────┘
```

- **Keys never enter the webview.** The root key and subkeys live in the Rust process,
  zeroized on lock (`zeroize`). The UI asks for decrypted envelopes for the list and one
  payload at a time for detail, which is the same two-part split the phones use.
- **Tauri hardening:** a strict CSP, no remote content, the isolation pattern, capabilities
  that expose only Qasa's own commands, and no shell or filesystem plugins in the webview.
- **Network calls go out from Rust,** not from the webview, so server CORS is irrelevant and
  the base URL works like `QasaConfig`: localhost in debug, `https://api.qasa-app.com` in
  release, HTTPS required in release (the config-error banner from iOS).
- **Storage:** SQLite (`rusqlite`, bundled) in the OS app-data folder. Its tables mirror
  `VaultModels.swift`:
  - `items`: id, kind, envelope, payload, schema_version, legacy_payload, created_at,
    updated_at, last_used_at, collection_id, **deleted_at** (new, for sync)
  - `search_tokens`, `collections`, `notifications`
  - `vault_meta`: salt, verifier, kdf_version
- **Secrets in the OS keychain** (`keyring` crate: macOS Keychain, Windows Credential
  Manager, Linux Secret Service): account id, device fingerprint, license token, cached
  license verifying key, and the biometric-sealed unlock secret.

---

## 4. Logic parity contract

Everything below must match iOS byte for byte or rule for rule. `qasa-vault` gets a
**golden test vector file** (`core/qasa-vault/tests/vectors.json`) built from iOS and
checked by iOS and Android tests too, so all three cannot drift apart.

| Area | Rule | iOS reference |
| --- | --- | --- |
| KDF | PBKDF2-HMAC-SHA256, 310,000 iterations, 32-byte salt (reject < 16), kdfVersion big-endian Int32, absent = 1, refuse newer | `Services/Vault/MasterKeyDeriver.swift` |
| Subkeys | HKDF-SHA256, info `qasa-enc` and `qasa-search` | same |
| Verifier | SHA-256(root key), constant-time compare | same |
| Cipher | AES-256-GCM, `nonce(12) ‖ ciphertext ‖ tag(16)` | `VaultCrypto.swift` |
| Payload JSON | Read both the iOS synthesized-enum shape and Android's `type` discriminator, plus old lowercase spellings. Write the canonical sync shape (section 6.4) | `VaultEnums.swift`, parity plan P3 note |
| Item taxonomy | All 13 subtypes: login, secure note, card, bank account, name, email, phone, address, company, website, national ID, passport, driver licence | `VaultEnums.swift` |
| Field validation | One Rust implementation (`qasa-vault::validate`, FFI on the phones): typed per-field issues, `normalize` before every save (ISO dates, `MM/YY` expiry, grouped card numbers), user saves with issues refused, CSV/sync/restore accepted and flagged. Golden vectors in `core/qasa-vault/tests/validation_vectors.json`, checked by iOS and Android. ARCHITECTURE.md §5.1 | `qasaTests/ValidationVectorTests.swift` |
| Search | Unicode tokenizer (`\p{L}\p{N}`), words + 2–3 char n-grams, HMAC-SHA256 with the search key. Tests in Arabic and Sorani | `VaultSearchIndex.swift` |
| Password health | Length ≥ 10, character-class scoring, trimmed, blanks skipped, −15 per breached login | `PasswordHealthAnalyzer.swift` |
| Generator | One character from each selected class, then shuffle (Android's version, which design.md says to port to iOS). One length cap for all three | `GenerateView.swift` |
| TOTP | RFC 6238, the same `otpauth://` parsing | `SharedAutofill/TOTP.swift`, `TOTPTests.swift` |
| CSV | The fixed 16-column header, fingerprint dedupe on import, export to a temp file, never the clipboard | `VaultCSV.swift` |
| License | Ed25519 verify against a pinned key, refresh from `/.well-known/qasa-license-key`, fail closed, reject a bad token and keep the good one, retry once after key rotation | `LicenseTokenVerifier.swift`, `LicenseManager.swift` |
| Breached passwords | HIBP range API, SHA-1, 5-char prefix, `Add-Padding: true`, Premium only, never a partial "all clear" | `PwnedPasswordsService.swift` |
| Auto-lock | 1–60 min, presets `[1,2,5,10,15,30,45,60]`. On desktop also lock on OS sleep and screen lock | `AutoLockMonitor.swift` |
| Clipboard | Card and bank fields expire after 30 s. Clear only if Qasa still owns the clipboard (compare contents or the change counter). 2FA codes follow the iOS lifetime | `PaymentClipboard.swift`, `TOTPPasteboard.swift` |
| Bootstrap order | Open the vault → warm the cache → license → register → refresh → (no push) → sync locale. Reverse on logout, calling `DELETE /v1/devices/me` | `AppBootstrap.swift` |

---

## 5. Design and layout

### 5.1 Tokens, type and icons

- **One generator** (`design/generate.py`) writes `src/theme/tokens.css` from
  `design/tokens.json`, the same source as iOS and Android: colors in light and dark, sizes,
  radii, gaps, type and motion as CSS variables, so nothing can drift.
- **Fonts:** bundle IBM Plex Sans Arabic (400/500/600/700) for every language, in
  `src/assets/fonts/` (design.md §3.1). Follow the same rules as iOS: in ar/ckb, mono
  text falls back to the body font, letter spacing is zero, and nothing is uppercased.
- **Type scale:** the table in design.md §3.2, as named CSS classes. No raw sizes in
  components.
- **Icons:** Lucide, mapped one-to-one to the SF Symbols the iOS views use (`house`, `key`,
  `bell.badge`, `person.crop.circle`, `shield`, …), with RTL-mirrored versions for direction
  glyphs.
- **Theme:** System / Light / Dark, persisted, resolved once at the root.
- **Motion:** the five design.md §8.1 durations, all off under `prefers-reduced-motion`.
- **RTL:** `dir="rtl"` on the root for ar/ckb, logical CSS properties only (`margin-inline`,
  `inset-inline-start`), mirrored chevrons.

### 5.2 Shell

The phone shell is four bottom tabs, with other pages opening on top with a back chevron.
On the desktop:

| Phone | Desktop |
| --- | --- |
| Bottom tab bar: Home, Passwords, Alerts, You | A left sidebar with the same four entries, icons and order (on the right in RTL). Unread badge on Alerts |
| Pages pushed over a tab (Notifications, Generator, Health, Collections, Settings, Terms, Privacy, Premium) | Open in the content area with the same 64 px `AppMenuHeader` and back chevron. The sidebar stays visible |
| Sheets (editor, add-item menu, generator, settings sub-pages) | Centered modal dialogs with the sheet's 20 px radius and the `chrome` surface |
| Pair existing vault (full-screen cover) | A full-window flow |
| Premium-gated page redirects to Upgrade | Same rule, in the same router guard |

- **Passwords becomes two-pane above 900 px:** the vault list on the left, item detail on
  the right. Below 900 px it falls back to the phone's list → detail push. This is the one
  layout change, and it is the standard desktop treatment of the same screens.
- **Content width is capped** (about 720 px) on single-column pages such as Home, Settings
  and Health, so cards keep phone proportions instead of stretching.
- **Minimum window size** 760 × 560.
- **Privacy shield:** the window content is protected from screen capture
  (`content_protected` on macOS and Windows), and the iOS `VaultPrivacyShield` covers the
  vault when it auto-locks.
- **Keyboard:** ⌘/Ctrl+F search, ⌘/Ctrl+N new item, ⌘/Ctrl+L lock, ⌘/Ctrl+G generator, Esc
  closes dialogs. Every control is reachable by Tab, with a visible focus ring in `accent`.

### 5.3 Screen map

Every iOS screen, with its desktop counterpart. "Same" means the same cards, rows, order,
copy keys and states (loading, empty, error, locked/Premium).

| iOS view | Desktop | Notes |
| --- | --- | --- |
| `WelcomePreferencesView` | Same | Language + theme, first run only |
| `CreateVaultView` | Same | Biometrics card shows Touch ID / Windows Hello, hidden on Linux |
| `LoginView` (unlock) | Same | "Local unlock only" card. Biometric button where available |
| `PairExistingVaultView` | Same 4-step flow | Real LAN pairing once phase 4 lands (section 6) |
| `VaultStoreErrorView` | Same | No retry, no reset (intentional, design.md §12.13) |
| `HomeView` + `HomeCards` | Same: status hero, health card, breached card | VPN card says "VPN is on your phone" with no toggle |
| `VaultListView` + `VaultItemRow` | List pane | Filter tabs, search, Suggested and Most Recent sections, row copy, overflow menu |
| `VaultItemDetailView` | Detail pane | Detail fields, tap to copy, Organization and History cards, protected-note gate |
| `VaultItemEditor` + `FormHelpers` | Modal | Every field for all 13 types: country, collection, colour, honorific pickers, validated dates |
| `AddItemMenuView` | Modal | Drill-down by type, seeded from the current filter tab |
| `TOTPViews` | Same | Code with countdown ring. QR from a screenshot or image file, or paste `otpauth://` |
| `PasswordGeneratorModal` / `GenerateView` | Same | "Use Password" in the editor; "Save to vault" works (fixes the phone no-op) |
| `BreachedPasswordsView` (Alerts tab) | Same | Premium, locked card for Free |
| `RotatePasswordSheet` | Same | Opens the site in the default browser |
| `PasswordHealthView` | Same | Half-circle gauge, issue lists |
| `CollectionsView` | Same | Create, rename, delete, drill in |
| `YouView` | Same | Account, Tools, About groups |
| `SettingsView` | Same sections | Security, Linked devices, Sync network, Appearance, Data & account. "Autofill" becomes "Browser extension" (connected browsers, approve or revoke) |
| `AutoLockSettingsView` | Same | Stepper + presets |
| `ChangeMasterPasswordView` | Same | Re-encrypts and re-indexes; warns that other devices need to re-sync |
| `LinkedDevicesView` | Same | Platform icons gain macOS / Windows / Linux |
| `LANSyncView` | Same | Premium |
| `NotificationsView` | Same | Inbox polling plus OS notifications |
| `PremiumUpgradeView` | Same carousel and wallet rows | Payment opens in the browser or as a QR code (section 7) |
| `ManageSubscriptionView` | Same | One cancel behind a confirm, then the reason sheet |
| `LegalPlaceholderView` | Same | Terms, Privacy |
| `VPNView` | Not in v1 | Decision 2 |

**Fix, don't port** (from design.md §13 and §12.12): the raw `common.cancel` key, the
generator "Save to vault" no-op, repeated biometric prompts on the detail view, the empty
rotate-password button, and the inconsistent root backgrounds.

---

## 6. Wi‑Fi vault sync

This is what makes the desktop useful, and it is not built anywhere yet: both phones send
`qasa-vault-placeholder:{accountId}`, Android has no networking, and both have the
account-rebinding hole (design.md §12.9). The desktop can't ship before the phones can sync
with it, so **this phase covers all three clients.**

### 6.1 Protocol (`core/qasa-sync`)

1. **Discovery:** mDNS `_qasa-sync._tcp` (desktop: `mdns-sd`; iOS: `NWBrowser`; Android:
   `NsdManager`). The listener also works by typed IP:port as a fallback for networks that
   block mDNS.
2. **Pairing:** the host shows a 6-digit code that expires after 2 minutes and works once.
   Both sides stretch it with Argon2id (as decided) and run SPAKE2 over TCP. Three wrong
   attempts close the session. Neither side can read anything without the code, and a
   network eavesdropper learns nothing.
3. **Channel:** the PAKE key derives AES-256-GCM session keys with per-direction counters.
4. **Account binding:** the joining device adopts the account id only from inside this
   authenticated channel. This closes the rebinding hole.
5. **Trusted pairs:** after the first pairing, both devices store a long-term pair key, so
   later syncs need no code: "Sync now", or automatic when both are on the same Wi‑Fi and
   unlocked.

### 6.2 What moves

- **First sync of a new device:** the vault metadata (salt, kdfVersion, verifier) and every
  encrypted record. The new device stores the ciphertext as it is. The user unlocks with the
  same master password, which derives the same keys from the same salt. The master password
  and keys never cross the wire.
- **Later syncs:** a two-way merge of records and collections.

### 6.3 Merge rules

- **Per item, last writer wins** on `updated_at`, ties broken by item id. Deletes are
  tombstones (`deleted_at`), kept for 90 days.
- **Collections merge by id.** Their names are encrypted with the vault key, like item
  envelopes, at rest and on the wire (`WireCollection::name_enc`, protocol 2; decision 5).
  Names saved before that are encrypted at the next unlock, and a tombstone carries no name.
- **Changing the master password** on one device changes the verifier. A peer with a
  different verifier refuses to merge and asks the user to re-sync as a new device.
- **Premium:** sync is Premium on the phones (`LANSyncView`), so it stays Premium here.

### 6.4 Canonical wire format

iOS and Android payload JSON have different shapes (parity plan, P3 note). The wire
carries one canonical JSON shape defined in `qasa-vault`. Each platform maps to and from it,
and every client reads all the old shapes.

### 6.5 Phone work

- `qasa-ffi` (UniFFI) builds `qasa-sync` and `qasa-vault`'s canonical mapping into an
  XCFramework for iOS and `cargo-ndk` `.so` files for Android.
- Replace `LANVaultSyncService` on both phones, and `PairingSessionStore` with the real
  protocol.
- Add `deletedAt` to `VaultItemRecord` (SwiftData migration) and to the Room entity, and
  make every delete a tombstone.
- Free → upgrade actions on the Pair screen, as already planned in server-followups §1.

---

## 7. Server and account

Little changes on the server.

- **Platforms:** register with `platform` = `macos` | `windows` | `linux` (a free-form
  column today). `location` stays null, as on Android.
- **Push:** desktops have no FCM token, so `qasa-push` skips them. The app polls
  `notifications/unread-count` every 5 minutes while open and on unlock, and shows OS
  notifications for new items.
- **Payments:** Wayl's hosted page opens in the default browser. The wallet stubs return app
  deep links (`fib://pay…`) that a desktop can't open, so the desktop shows them as a QR code
  to scan with the phone. Status polling stays 30 × 2 s, then a "Check again" button.
- **Dashboard:** platform icons and the platform filter gain the three desktop values.
- **Device limit:** the desktop is a linked device and takes a slot (decision 1).

---

## 8. Browser extension (`apps/extension/`)

### 8.1 How it talks to the desktop

```
content script ⇄ service worker ⇄ (native messaging, stdio) nm-host ⇄ (Unix socket / named pipe) desktop app
```

- **The nm-host binary** ships inside the desktop installer. The installer registers it
  with each installed browser: a manifest JSON under the browser's NativeMessagingHosts
  folder on macOS and Linux, a registry key on Windows. `allowed_origins` lists only Qasa's
  extension ids.
- **First connection:** the desktop shows "Allow Chrome to use Qasa?" with a short matching
  code shown in both places. Approving stores a shared key on both sides, and every message
  is authenticated with it. Other local processes that reach the socket get nothing. Revoke
  from Settings → Browser extension.
- **The extension stores no vault data** and no keys, only the pairing key in
  `chrome.storage.local`. It asks for one credential at a time after a user action.

### 8.2 Messages

| Message | Returns |
| --- | --- |
| `status` | locked / unlocked / not paired / app not running |
| `unlock` | Brings the desktop window forward on its unlock screen |
| `matches(origin)` | Ranked logins for the page: id, title, username. No secrets |
| `fill(itemId, origin)` | Username, password, current TOTP, only if the item matches that origin |
| `save(origin, username, password)` / `update(itemId, password)` | The desktop asks the user to confirm |
| `generate(options)` | A password, using the shared generator |
| `search(query)` | Titles for the popup's search |

Ranking ports `AutofillContextMatcher.swift`: exact host +100, subdomain +80, same
registrable domain +60 (Public Suffix List, bundled in the desktop), substring +30,
title/subtitle +20, base 10, ties by title.

### 8.2.1 Wire protocol (v1)

**Framing.** Browser ⇄ nm-host uses the standard native messaging framing: a 4-byte
length in native byte order, then UTF-8 JSON, at most 1 MiB. nm-host ⇄ desktop uses the
same framing, little-endian. nm-host is a dumb pipe. It opens one socket connection per
native port, and its first frame is `{"type":"hello","origin":"<argv[1]>"}`. While the
app isn't running, it answers every frame with `{"type":"error","code":"appNotRunning"}`,
and it retries the connection on the next frame.

**Socket.** On macOS and Linux it is `<app data dir>/bridge.sock`, mode 0600:

- macOS: `~/Library/Application Support/com.qasa-app.desktop/`.
- Linux: `$XDG_DATA_HOME` or `~/.local/share`, then `com.qasa-app.desktop/`.

On Windows it is `\\.\pipe\qasa-bridge-<USERNAME>`. `QASA_BRIDGE_SOCKET` overrides the
path on every OS, for tests.

**Pairing** (in the clear; the matching code stops a man in the middle):

1. Extension → `{"type":"pair","client":"Chrome","pub":"<b64 raw P-256 public key, 65 bytes>"}`.
2. Desktop → `{"type":"pairPending","pub":"<b64 desktop P-256 key>"}`. Both sides then
   derive the same values:
   - `okm = HKDF-SHA256(ikm = ECDH secret, salt = extPub ‖ deskPub, info = "qasa-bridge-v1", 36 bytes)`;
   - `key = okm[0..32]`, used for AES-256-GCM;
   - `code = big-endian u32(okm[32..36]) mod 1 000 000`, written as 6 digits with leading zeros.
3. The desktop shows "Allow Chrome to use Qasa?" with the code, and the extension shows
   the same code. One pairing can be pending at a time, for 2 minutes.
4. Desktop → `{"type":"paired","clientId":"<uuid>"}`, or
   `{"type":"pairDenied"}` / `{"type":"pairExpired"}`. The extension stores
   `{clientId, key}` in `chrome.storage.local`. The desktop stores it in the OS keychain.

**Requests:**

- The extension sends `{"type":"req","clientId":…,"n":"<b64 12-byte nonce>","c":"<b64 ciphertext+tag>"}`.
- The message is AES-256-GCM encrypted with `AAD = UTF-8 clientId`. The plaintext is
  `{"id":"<16 random hex>","ts":<ms>,"op":"…",…args}`.
- The desktop rejects:
  - a `ts` more than 2 minutes off;
  - an `id` it has seen in the last 5 minutes;
  - an unknown client, with `{"type":"error","code":"notPaired"}` in the clear.
- The reply is `{"type":"res","n":…,"c":…}` (same key and AAD). Its plaintext is
  `{"id":…,"ok":true,…}` or `{"id":…,"ok":false,"error":"locked|noVault|notFound|noMatch|bad"}`.

| `op` | Args | Reply |
| --- | --- | --- |
| `status` | — | `state`: `locked`, `unlocked` or `noVault`; `device` |
| `unlock` | — | — (brings the window forward) |
| `matches` | `url` | `items: [{id, title, username, website, hasTotp}]`: score ≥ 60 only, best first (`website` picks the row's bundled site icon) |
| `fill` | `itemId, url` | `username, password, totp?` (current code). `noMatch` unless the item scores ≥ 60 for `url` |
| `totp` | `itemId, url` | `code, period, remaining`. Same match rule |
| `copy` | `itemId, field` (`username`, `password` or `totp`) | Copied by the desktop. It clears after 30 s, or for a 2FA code when the code expires |
| `search` | `query` | `items: [{id, title, username, website, hasTotp}]`: logins only, 50 at most |
| `generate` | — | `password` (the shared generator's defaults) |
| `save` | `url, username, password` | `result`: `unchanged`, `prompted` or `locked`. The desktop then asks to save or update |

- **Trusted origin.** The service worker takes `url` from `sender.url` (the frame's own
  URL), never from what the content script claims.
- **Popup only.** `copy` and `search` run only for requests from the popup.
- **Clean URLs.** Only `http(s)` URLs are accepted, and the desktop removes the path and
  query before matching.

### 8.3 Features

- **Popup:** status, logins for the current site, search, generator, copy username /
  password / 2FA code, "Open Qasa". Same tokens, fonts and RTL as the desktop.
- **Autofill:** field detection for login, signup and change-password forms, including
  same-origin iframes and open shadow roots. A small Qasa icon in the field opens a menu
  drawn in a **closed shadow root, with clickjacking protection** (hidden-overlay and
  opacity checks, no fill on a first click into a fresh frame). **Fill only after a user
  action, never silently** (the iOS rule). Cross-origin iframes fill only when the frame's
  own origin matches the item.
- **Save and update prompts** after a form submit, confirmed in the desktop app.
- **2FA:** fill the code into one-time-code fields, or copy it after the password fill, like
  `TOTPPasteboard`.
- **Cards and identities:** off in v1 (decision 3).
- **Locked app:** the popup shows "Unlock Qasa", which brings the desktop app forward. App
  not installed: a link to the download page.
- **Languages:** the extension's own i18n runtime, fed from `l10n/strings.json`
  (en/ar/ckb), with RTL. `_locales/` holds only the store name and description (en, ar).
  Chrome doesn't support `ckb` there, so Kurdish users see the English store listing.
- **Permissions:** `nativeMessaging`, `storage`, `activeTab`, `scripting`, and content scripts
  on `<all_urls>`. Each one is justified in the store listing.

---

## 9. Localization

- `l10n/generate.py` gains a `web` target. It writes `apps/desktop/src/i18n/{en,ar,ckb}.json`
  (shared by the extension), converting `%@` / `%lld` to `{0}` / `{1}`. `--check` covers the
  new files.
- Desktop-only and extension-only strings (keyboard shortcuts, the browser-approval prompt,
  nm-host errors) go into `strings.json` with drafted ar/ckb text, for a native speaker to
  review, as with the parity work.
- Server-side copy (push, pricing) is unchanged; the desktop sends its locale on
  `PATCH /devices/prefs` like the phones.

---

## 10. Packaging and release

| Target | Format | Signing |
| --- | --- | --- |
| macOS | Universal `.dmg` | Developer ID + notarization (existing Apple account). Hardened runtime, no entitlements: Touch ID uses CryptoKit Enclave keys, which need no keychain access group |
| Windows | NSIS `.exe` + `.msi` | Azure Trusted Signing (about $10 a month) |
| Linux | AppImage + `.deb` | Update signature only |
| Extension | Chrome Web Store, Edge Add-ons, Firefox AMO | Store review. Firefox needs a stable `browser_specific_settings.gecko.id` |

- **Auto-update:** `tauri-plugin-updater` reads `qasa-app.com/desktop/latest.json`, which
  Caddy redirects to the latest published GitHub release; archives are signed with the
  updater key. The nm-host updates with the app (it is a bundled sidecar). Steps and
  secrets: [`desktop-release.md`](desktop-release.md).
- **Distribution:** direct download only. The Mac App Store is avoided because of the
  in-app-purchase rule for subscriptions and the sandbox limits on native messaging.
- **Landing page:** a Download section that detects the OS, plus extension store links.

---

## 11. Biometric unlock

| OS | How | Fails closed when |
| --- | --- | --- |
| macOS | An unlock secret in the Keychain with `biometryCurrentSet` access control, read through LocalAuthentication | No Touch ID, or fingerprints changed |
| Windows | Windows Hello (`KeyCredentialManager`) signs a challenge that unwraps the unlock secret, which is stored with DPAPI | Hello not set up |
| Linux | Not offered; master password only | — |

The secret is a random key that wraps the root key. It is never the master password itself,
so the Android defect in design.md §12.7 is not repeated.

---

## 12. Phases

Each phase ends with its own tests passing and a design check against iOS screenshots
(`-screenshotVault` seed) in light and dark × en / ar / ckb.

| Phase | Delivers | Done when |
| --- | --- | --- |
| **0. Foundations** | `core/qasa-vault` crypto + golden vectors checked by iOS and Android tests; Tauri + Vue scaffold; tokens script; fonts; i18n target; RTL root | A vault created on iOS decrypts in `qasa-vault` from the vectors, and the reverse |
| **1. Local vault** | Welcome, create, unlock, auto-lock, shell, all item types, list/detail/editor, search, collections, generator, TOTP, health, CSV, appearance settings, change master password, clear local data | The whole vault works offline, with no server |
| **2. Account** | Register, license, linked devices, inbox, Premium upgrade and payment, manage subscription, breached passwords, delete account, logout | A Free account upgrades through Wayl test mode and Premium features unlock |
| **3. Biometrics** | Touch ID, Windows Hello | Unlock works and fails closed |
| **4. Wi‑Fi sync** | `qasa-sync`, UniFFI builds, phone ports, tombstone migrations, rebinding fix | iPhone ⇄ desktop ⇄ Android pair, transfer and merge; the hostile-peer test fails to rebind |
| **5. Extension** | nm-host, approval flow, popup, autofill, save/update, 2FA | Fills on a fixture suite (login, iframe, shadow DOM, multi-step, clickjacking pages) in Chrome and Firefox |
| **6. Release** | Signing, notarization, updater, store listings, landing download page | Signed builds install and update on all three OSes |

Phases 1–3 and phase 4's core crate can overlap. Phase 5 needs phase 1 only, so it can start
before sync lands.

**Tests:**

- `cargo test` for `core/` and `src-tauri`, including the cross-platform vectors.
- Vitest for UI logic.
- Playwright with the unpacked extension on local fixture pages.
- An integration test that runs two `qasa-sync` peers in one process.

---

## 13. Open decisions (recommendation first)

1. **Free tier and the desktop.** Free allows one device, so a phone user on Free can't add
   the desktop. **Recommend:** keep the limit. The desktop works on Free as someone's only
   device, and Premium covers phone + desktop. Alternative: let one desktop not count.
2. **VPN on the desktop.** It needs a privileged helper on every OS (a macOS Network
   Extension, wireguard-nt on Windows, root on Linux). **Recommend:** after v1.
3. **Cards in the extension.** *Replaced 2026-11-10 by [`autofill-plan.md`](autofill-plan.md):
   cards and profiles fill, cards after a Touch ID / Hello or master password confirmation.*
   iOS deliberately never returns cards as credentials.
   **Recommend:** no card autofill in v1. The popup shows the card in the 30-second copy
   drawer instead.
4. **Sync conflicts.** **Recommend:** last writer wins per item. Alternative: keep the losing
   version as a "conflict copy" item, which is safer but noisier.
5. **Encrypt collection names** in the phase 4 schema change. **Done** (2026-11-09), as its own
   change after phase 4: sync protocol 2, vault-key ciphertext at rest and on the wire on
   every platform, migration at unlock.
6. **Safari extension.** **Recommend:** after v1, wrapping the same extension code.
