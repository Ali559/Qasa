# Android: open items

- [ ] **Try 2FA codes and the Premium breach lock by hand.** Built 2026-11-06 with unit
      tests (`vault/totp/TotpTest`, `VaultCsvTest`), but never run on a device or emulator:
      the QR scanner, the live code and countdown, copying, autofill copying the code (it
      fills through `AutofillTotpActivity` only when the user picks that login), and the
      locked breach screens (Home card, Alerts tab, password health) on a Free account.
- [ ] **Scan a QR code on a real phone.** The scanner is Google's code scanner
      (`play-services-code-scanner`), which needs Google Play services and may download its
      module on first use; until then it falls back to manual entry. Check that on a device.
- [x] **Delete account in the app** (2026-11-09): Settings > Data & Account; not yet tried
      on a device against a real server.
- [ ] **Site icons on login items (decided 2026-11-10: not yet).** Login rows show a letter
      tile. Fetching favicons would tell a third party which sites the user has, and bundling
      icons grows the app. Option when wanted: a small bundled set of monochrome icons for the
      ~50 most common sites (about 100–200 KB), matched by registrable domain, same set on
      every platform; anything else keeps the letter tile.
- [ ] **Try the new flows on a phone (2026-11-10):** fingerprint unlock after an app restart
      (fixed key reuse), the autofill banner's Activate, Settings sheets opening and closing,
      the card editor's expiry/code row, Backups' "Only on this phone" card with a Google Drive
      folder, and Scan with NFC on a real card, ID card and passport.
