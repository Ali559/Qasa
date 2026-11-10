# iOS: open items

- [ ] **Try 2FA codes and the Premium breach lock by hand.** Built 2026-11-06 with unit
      tests (`qasaTests/TOTPTests.swift`), but never run on a device or simulator by hand:
      the QR scanner, the live code and countdown, copying, AutoFill copying the code, and
      the locked breach screens (Home card, Alerts tab, password health) on a Free account.
- [ ] **Scan a QR code on a real iPhone.** `TOTPScannerSheet` uses VisionKit's
      `DataScannerViewController`, which needs a real camera: the simulator only offers
      manual entry. On a device without a supported camera it shows `totp.cameraDenied`,
      whose text ("allow it in Settings") doesn't quite fit that case.
- [x] **iOS 18 one-time codes.** Done 2026-11-10 (docs/autofill-plan.md §3.4). AutoFill only puts the 2FA code on the pasteboard (local,
      60 seconds). Filling it straight into the code field needs `ASOneTimeCodeCredential`:
      the `ProvidesOneTimeCodes` capability in the extension's Info.plist, registering
      one-time-code identities with the identity store, and new extension entry points.
- [x] **Delete account in the app** (guideline 5.1.1(v)): Settings > Data & Account,
      `DELETE /v1/account`, then the local erase (`Services/Account/AccountDeletion.swift`).
- [ ] **Clear local data leaves the vault rows.** It deletes the keychain keys (salt, verifier,
      account id…), so the rows can't be decrypted any more, but the encrypted rows stay in
      the SwiftData store until a new vault replaces them. Delete account erases them; Clear
      local data should too.
- [x] **Site icons on login items.** Done 2026-11-10: 57 bundled monochrome icons
      (Simple Icons, `assets/site-icons`, template SVGs in `Assets.xcassets/SiteIcons`),
      matched by the login website's registrable domain (`SiteIcon.swift`), on `VaultItemRow`
      and the detail hero; other sites keep the letter tile, nothing is fetched. Tested on the
      simulator only: check them on a device in light and dark.
