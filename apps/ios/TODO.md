# iOS: open items

- [ ] **Try 2FA codes and the Premium breach lock by hand.** Built 2026-11-06 with unit
      tests (`qasaTests/TOTPTests.swift`), but never run on a device or simulator by hand:
      the QR scanner, the live code and countdown, copying, AutoFill copying the code, and
      the locked breach screens (Home card, Alerts tab, password health) on a Free account.
- [ ] **Scan a QR code on a real iPhone.** `TOTPScannerSheet` uses VisionKit's
      `DataScannerViewController`, which needs a real camera: the simulator only offers
      manual entry. On a device without a supported camera it shows `totp.cameraDenied`,
      whose text ("allow it in Settings") doesn't quite fit that case.
- [ ] **iOS 18 one-time codes.** AutoFill only puts the 2FA code on the pasteboard (local,
      60 seconds). Filling it straight into the code field needs `ASOneTimeCodeCredential`:
      the `ProvidesOneTimeCodes` capability in the extension's Info.plist, registering
      one-time-code identities with the identity store, and new extension entry points.
- [ ] **Premium status refresh.** `AppSession.isPremium` re-syncs only on unlock, purchase,
      logout or license refresh. If Premium expires mid-session, the breach check stops at
      once (the analyzer also checks the live license), but the locked screens appear only
      at the next re-sync.
