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

## Also open

- [ ] **Privacy policy.** Publish one on qasa-app.com. The extension stores require it.
- [ ] **Download page wording.** It says the desktop pairs with your phone. On Free that
      needs a second device slot (plan open decision 1). Confirm the copy, or change the
      limit.
- [ ] **Firefox data declaration.** AMO may ask for
      `browser_specific_settings.gecko.data_collection_permissions` on the first upload.
- [ ] **Translations.** Have a native speaker review the Arabic and Sorani text:
      `bridge.*`, `ext.*` and `update.*` in `l10n/platform/desktop.json`, the landing
      page's desktop section, and the store listing.
- [ ] **Not tested yet:**
  - real Chrome and Edge;
  - Windows and Linux builds;
  - an update installing over an older version;
  - phone ⇄ desktop through the desktop window itself (no tool here can drive a native
    window).

  The extension passed 14 checks in Firefox 159 on 2026-11-09 (`bun run e2e:firefox`).
  Android Wi‑Fi sync was tested on 2026-11-08 on the emulator: pair, adopt, unlock,
  items and folder. The emulator can't see the Mac's mDNS, so debug builds take a host
  with `adb shell setprop debug.qasa.sync_peer 10.0.2.2:<port>`.
- [ ] **16 KB pages on Android.** The emulator flags the WireGuard libraries (`libwg*.so`)
      and `libandroidx.graphics.path.so` as not 16 KB compatible. Google Play requires 16
      KB support for apps targeting Android 15+. The sync library is aligned, and JNA was
      raised to 5.19.1 for its RELRO fix.
