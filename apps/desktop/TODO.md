# Desktop app and browser extension: before release

Steps that need the account owner. The details for each are in
[`docs/desktop-release.md`](../../docs/desktop-release.md).

- [ ] **Updater key.** Back up `~/.tauri/qasa-desktop-updater.key` and add it as the
      GitHub secret `TAURI_SIGNING_PRIVATE_KEY` (`TAURI_SIGNING_PRIVATE_KEY_PASSWORD` is
      empty). If it is lost, installed apps can never update.
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
      Windows and Linux, then publish it.
- [ ] **Extension stores.** Submit the extension to the Chrome Web Store, Edge Add-ons and
      Firefox AMO, using `apps/extension/store/listing.md`.
- [ ] **Store ids.** Add the ids the stores assign to `CHROME_EXTENSION_IDS` in
      `src-tauri/src/bridge/manifest.rs`. Then release the desktop app **before** the
      listings go live, or the store version can't connect to the app.

## Decisions

- [ ] **Free plan and the desktop** (plan open decision 1). Free allows one device, so a
      phone user on Free can't add the desktop. The download page says the desktop pairs
      with your phone. Either let one desktop not count toward the limit, or change the
      wording (and say that phone plus computer needs Premium).
- [ ] **Encrypt folder names** (plan open decision 5). Folder (collection) names are
      stored and synced in plain text on iOS, Android and the desktop. The plan meant to
      encrypt them in the phase 4 schema change, but that was skipped. It now needs its
      own migration on all three, plus a sync protocol version so older apps don't
      receive names they can't read.
- Settled, and followed in the code:
  - VPN on the desktop comes after v1 (decision 2).
  - No card autofill in the extension (decision 3).
  - Sync conflicts: the newest change to each item wins (decision 4).
  - A Safari extension comes after v1 (decision 6).

## Also open

- [ ] **Privacy policy.** Publish one on qasa-app.com. The extension stores require it.
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
