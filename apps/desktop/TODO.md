# Desktop app and browser extension: before release

Steps that need the account owner. The details for each are in
[`docs/desktop-release.md`](../../docs/desktop-release.md).

- [x] **Updater key.** Backed up, and added as the GitHub secret
      `TAURI_SIGNING_PRIVATE_KEY` (2026-11-09). The key has no password, so there is no
      `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` secret: a missing secret reads as empty.
- [ ] **macOS signing.** Create an Apple Developer ID Application certificate and an
      app-specific password for notarization. Add them as secrets: `APPLE_CERTIFICATE`,
      `APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD`,
      `APPLE_TEAM_ID`.
- [ ] **Windows signing.** Set up Azure Trusted Signing (about $10 a month) and add its
      secrets: `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID`,
      `AZURE_SIGNING_ENDPOINT`, `AZURE_SIGNING_ACCOUNT`, `AZURE_SIGNING_PROFILE`.
- [ ] **Server.** Deploy the Caddyfile (`server/deploy/deploy.sh`), so the
      `qasa-app.com/desktop/…` download and update links work.
- [ ] **First release.** Tag `desktop-v0.1.0` and push. Test the draft release on macOS,
      Windows and Linux, then publish it. First delete the `desktop-v0.1.0-rc2` test draft
      and its tag (`gh release delete desktop-v0.1.0-rc2 --cleanup-tag`); it is a working
      unsigned build from the 2026-11-09 pipeline test, kept for trying on other computers.
- [ ] **Extension stores.** Getting the browser extension listed:
  1. Accounts: Chrome Web Store developer account ($5 once), Microsoft Partner Center for
     Edge (free), Firefox Add-ons account (free).
  2. Packages: tag `extension-v<version>` and push; the Extension release workflow tests
     the extension and attaches the store zips to the run (or `bun run package` in
     `apps/extension`).
  3. Uploads: `qasa-chrome-<ver>.zip` to Chrome and to Edge; `qasa-firefox-<ver>.zip` to
     Firefox, with the source zip it asks for. Fill each store's form from
     `apps/extension/store/listing.md` (descriptions, permission justifications, privacy
     answers, screenshots at the sizes listed there) and give the privacy policy URL
     `https://qasa-app.com/privacy`, so deploy the landing page first.
  4. Review: usually a few days; Chrome often takes longer for password managers
     (`nativeMessaging` and access to every site get a closer look).
  5. Store ids: Chrome and Edge assign new extension ids. Add them to
     `CHROME_EXTENSION_IDS` in `src-tauri/src/bridge/manifest.rs` and release the desktop
     app **before** the listings go public, or the store version can't connect to the
     app. Firefox keeps `extension@qasa-app.com`, so it needs no change.

## Decisions

- [x] **Free plan and the desktop** (plan open decision 1). Decided 2026-11-09: the desktop
      counts as a device, so phone plus computer needs Premium; the download section's
      wording now says so (Free: the desktop on its own; Premium: synced with your phone).
- [x] **Encrypt folder names** (plan open decision 5). Done 2026-11-09: vault-key ciphertext
      at rest and on the wire on iOS, Android and the desktop (sync protocol 2), plaintext
      names encrypted at the next unlock. Unit tests pass on all three; still to do: a
      Wi‑Fi sync between two real devices with the new build (the emulator run was cut short
      by the Mac's load).
- Settled, and followed in the code:
  - VPN on the desktop comes after v1 (decision 2).
  - No card autofill in the extension (decision 3).
  - Sync conflicts: the newest change to each item wins (decision 4).
  - A Safari extension comes after v1 (decision 6).

## Also open

- [ ] **Privacy policy.** Written (2026-11-09): `apps/landing` `/privacy` in en/ar/ckb, and
      the in-app summary. Before it goes live: create `privacy@qasa-app.com` (or change the
      address), add a company name/address/jurisdiction if any, name the server's country,
      then deploy the landing page. Open points in `apps/landing/TODO.md`.
- [x] **Privacy mailbox.** Done 2026-11-09 with Cloudflare Email Routing:
      `privacy@qasa-app.com` → `biz@alibarznji.dev`, catch-all off, Cloudflare MX and SPF,
      DMARC `p=reject`. No mail ports on the server. Setup in `docs/deploy.md` → Email.
      Delete the `qasa-email-setup` API token in Cloudflare.
- [ ] **Push notifications (FCM).** Not set up yet: no Firebase project, no
      `google-services.json` / APNs key in the apps. The privacy policy already says push
      goes through Firebase Cloud Messaging; set it up, or change that section, before
      the policy goes live.
- [ ] **Wallet names in the apps.** The Premium screens on iOS, Android and the desktop
      still list FIB, FastPay and SuperQi as separate payment options
      (`premium.fastpay*`, `premium.superqi*`), while payment actually goes through Wayl.
      The landing page and `ARCHITECTURE.md` now say Wayl.
- [ ] **Account deletion in the phone apps.** Only the desktop can delete the account
      (`DELETE /v1/account`); App Store guideline 5.1.1(v) requires it in the iOS app too.
      The privacy policy says it's coming to the phone apps.
- [ ] **Translations.** Have a native speaker review the Arabic and Sorani text:
  - the bridge, extension and updater strings: `bridge.*`, `ext.*` and `update.*` in
    `l10n/platform/desktop.json`;
  - the landing page's desktop section and `/download` pages (`apps/landing/src/i18n.ts`);
  - the Arabic store listing (`apps/extension/store/listing.md`).
- [ ] **Not tested yet:**
  - real Chrome and Edge;
  - Windows and Linux builds;
  - an update installing over an older version;
  - phone ⇄ desktop through the desktop window itself (no tool here can drive a native
    window).

  The extension passed 15 checks in Firefox 159 on 2026-11-09 (`bun run e2e:firefox`).
  Android 16 KB pages: every native library is 16 KB aligned, and a fresh install on the
  16 KB emulator shows no compatibility warning (2026-11-09; the warning came from JNA 5.14,
  now 5.19.1).
  Android Wi‑Fi sync was tested on 2026-11-08 on the emulator: pair, adopt, unlock,
  items and folder. The emulator can't see the Mac's mDNS, so debug builds take a host
  with `adb shell setprop debug.qasa.sync_peer 10.0.2.2:<port>`.
