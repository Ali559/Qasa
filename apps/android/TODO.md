# Android: open items

- [ ] **Try 2FA codes and the Premium breach lock by hand.** Built 2026-11-06 with unit
      tests (`vault/totp/TotpTest`, `VaultCsvTest`), but never run on a device or emulator:
      the QR scanner, the live code and countdown, copying, autofill copying the code (it
      fills through `AutofillTotpActivity` only when the user picks that login), and the
      locked breach screens (Home card, Alerts tab, password health) on a Free account.
- [ ] **Scan a QR code on a real phone.** The scanner is Google's code scanner
      (`play-services-code-scanner`), which needs Google Play services and may download its
      module on first use; until then it falls back to manual entry. Check that on a device.
- [ ] **Autofill index written before the update.** Logins carry their 2FA code into the
      autofill index only once the index is rebuilt (on any save or delete). Until then,
      autofill doesn't copy the code.
- [ ] **Premium status refresh.** `AppSession.isPremium` re-syncs only on unlock, purchase
      or cancel. If Premium expires mid-session, the breach check and its locked screens
      only follow at the next sync.
