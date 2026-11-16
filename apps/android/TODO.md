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
- [x] **Site icons on login items.** Replaced 2026-11-16 (TODO Phase 7, `docs/site-icons.md`):
      the bundled marks are gone; a login shows its site's own favicon, asked only from that
      site, or the installed app's icon for app logins, cached encrypted per device
      (`siteicons/`), else the calm letter. Check on a device with **Website icons** on and off.
- [ ] **Try the new flows on a phone (2026-11-10):** fingerprint unlock after an app restart
      (fixed key reuse), the autofill banner's Activate, Settings sheets opening and closing,
      the card editor's expiry/code row, Backups' "Only on this phone" card with a Google Drive
      folder.
- [x] **Scan with NFC removed (2026-11-10).** Built, tried once on a phone, then dropped on
      every platform as not needed: no NFC button in the editors, no NFC permission.
