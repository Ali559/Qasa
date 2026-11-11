---
name: iOS Android Parity v2
overview: Re-audit of the three earlier parity plans (ios_android_ui_parity, android_app_parity, android_chrome_nav_overhaul — all marked completed) against the current code. The visual token layer is at parity; behaviour, flows, field coverage and localization are not. iOS stays the source of truth except where noted. This plan closes the remaining gaps in priority order.
todos:
  - id: p0-locale
    content: "P0: Android in-app language + RTL (setApplicationLocales, locales_config, layout direction, M3 fallback typography)"
    status: completed
  - id: p1-safety
    content: "P1: Broken/unsafe behaviour — confirmations, debug text, health scoring, wrong copy target, premium double-pay, collection orphan ids, visual bugs"
    status: completed
  - id: p2-strings
    content: "P2: Move every Android screen to stringResource using the shared l10n keys (copy = iOS)"
    status: completed
  - id: p3-vault
    content: "P3: Vault detail/editor field parity, reveal/copy, pickers, generator sheet, row overflow menu, tab-aware add, suggestions/recents"
    status: completed
  - id: p4-auth
    content: "P4: Welcome/Create/Unlock copy + layout; rebuild Pair as the iOS step flow"
    status: completed
  - id: p5-tools
    content: "P5: Health issues screen, generator polish, VPN layout, Breach results + flow, Collections detail/rename, Notifications, Premium cards/payment/state machine"
    status: completed
  - id: p6-settings
    content: "P6: Settings sections, auto-lock sheet, profile card, CSV import/export, Linked devices, Manage subscription, LAN, Legal, locale server sync"
    status: completed
  - id: p7-chrome
    content: "P7: Drawer + header + row typography polish, store error/privacy shield, root overlays"
    status: completed
  - id: p8-ios
    content: "P8: iOS-side alignment (password rule, generator class guarantee, dates, search empty hint, dead code)"
    status: completed
isProject: false
---

# iOS ↔ Android Parity v2

Paths: `I/` = `apps/ios/qasa_ios/`, `A/` = `apps/android/app/src/main/java/alibarznji/qasa/`.

## What the earlier plans got right / wrong

| Earlier plan | Status claimed | Reality |
|---|---|---|
| `ios_android_ui_parity` Phase A (kit) | done | **True.** All 25 colour tokens (light + dark) are identical; radii 12/16/10/8/4, padding 20/17, header 64, PrimaryButton, CardContainer, StatusPill, FilterChip, FAB, StrengthBar, ToggleRow, StatBox match. |
| Phase B (shell/auth/vault) | done | **Restyle only.** Screens look like iOS but fields, actions and flows underneath differ (see P3/P4). |
| Phase C (heroes) | done | **Widgets ported, features not.** Breach results, notifications, collection detail, health issues, in-editor generator are missing. |
| Phase D (settings) | done | **Partial.** CSV rows are no-ops, LAN shows "placeholder blob" text, subscription/devices lack confirmations and states. |
| `android_chrome_nav_overhaul` | done | Mostly true (NavHost, sheets, back policy exist). Remaining: edit-from-detail drops the detail screen. |
| Earlier rule "Android strings stay English hardcodes for this pass" | — | Never revisited. 575 keys are generated into `values`/`values-ar`/`values-ckb`, but only ~18 `stringResource` calls exist across 6 screens. |

## Acceptable platform differences (do not "fix")

System back / predictive back, Material bottom sheets and AlertDialogs vs iOS sheets/alerts, Material icons vs SF Symbols, Android Autofill/Credential Manager vs iOS Credential Provider, biometric re-seal after password change on Android.

---

## Progress log

**2026-11-01 — P0 + P1 done (Android only).**
- P0 used `attachBaseContext` + `recreate()` (`prefs/LocaleContext.kt`, `MainActivity.kt`) instead of `AppCompatDelegate.setApplicationLocales`. That avoids adding an AppCompat dependency and works on every API level. No `locales_config.xml` yet, so the system's per-app language screen does not list Qasa. Covered by `androidTest/.../LocaleContextTest.kt`, which passed on the API 37 emulator.
- P1: `theme/chrome/QasaConfirmDialog.kt` is the shared confirm for all destructive actions. Health scoring is covered by `test/.../PasswordHealthAnalyzerTest.kt`.
- Interim choices (revisit in P3/P6):
  - Android hides the CSV import/export rows until VaultCSV is ported.
  - Android hides the "Suggested" section until the suggestion engine is ported.
  - Row copy for a phone number has no dial-code prefix yet, so it differs from iOS.
- Open decisions 1–4 are untouched: the clear-data scope is unchanged, and the breach At Risk card is kept.
- Not checked visually: FLAG_SECURE blanks screenshots and uiautomator dumps. Check the screens by hand.

**2026-11-01 — P2 done.** About 300 Android literals now come from the shared catalog, using the iOS key wherever iOS has one. 26 new keys were added to `l10n/strings.json` with drafted ar/ckb text: `pair.adopt*/accountLabel`, `settings.saving/biometric*/autofillSubtitleAndroid/autoLockChipFmt`, `lan.pairing`, `sub.status/plan/billing`, `gen.regenerate`, `health.weak/reusedPasswords`, `vpn.upgradeHint/node/city`, `breach.atRiskSummary*`, and `vault.item/loading/copiedFmt/noSearchMatches/itemType`. **A native speaker should review those ar/ckb drafts.** The grep gate is clean; what remains is clipboard labels, animation labels and numbers. Android lint crashes in this toolchain (RememberInCompositionDetector), so a script checked format placeholder and argument counts instead.
- Copy fixes still owed to later phases:
  - The VPN auto-connect subtitle now says "on public Wi-Fi" (iOS copy). *(Corrected in P5: Android never connected on app open. Neither app acts on this toggle; see the P5 entry.)*
  - Premium amounts use `"%,d"`, not locale grouping (P5).
  - The "Free" tier label comes from `LicenseManager.tierLabel`, which is unlocalized on both apps.
  - `LegalPlaceholderScreen` picks its body by comparing the title; pass `bodyRes` explicitly (P6).

**2026-11-01 — P3 done.**
- **Editor** (`screens/vault/editor/*`, `theme/ValidatedDateField.kt`): every iOS field for all 13 types; pickers for country, collection, colour, honorific and email/phone kind; validated dates; a generator sheet with "Use Password"; Save disabled until the item has a name; a "Could Not Save" alert.
- **Detail** (`screens/vault/detail/*`): the exact iOS `detailFields`; tap-to-copy plus `markUsed`; Organization and History cards; Edit and Delete in the top bar; reloads after an edit.
- **List** (`screens/vault/list/*`): the editor opens over the detail screen; row overflow menu; per-subtype icons; tab-aware add flow; Suggested and Most Recent sections.
- `vault/Country.kt` is a port of the iOS country list.
- **Pre-existing bug fixed:** Name, Email and Phone items could never be saved on Android. Their `type` field clashed with the sealed-class JSON discriminator `"type"`, so every encode threw. The fields are now serialized as `honorific` / `emailType` / `phoneType`. They store the iOS raw values ("None of these", "Work Cell"), and the older lowercase spelling is still read. Covered by `test/.../VaultPayloadRoundTripTest.kt`.
- **Format note:** iOS and Android payload JSON are structurally different. iOS uses the synthesized enum Codable (`{"name":{"_0":{…}}}`); Android uses a `type` discriminator. Any future cross-platform transfer (CSV in P6, LAN sync) needs an explicit mapping.
- **Remaining:**
  - `NoteColor` is defined twice (editor and detail).
  - A protected note asks for biometrics once per unlock on Android, but on every copy on iOS.
  - Copy feedback is a Toast, not iOS's flash on the row.
  - The date picker is the Material dialog, not iOS's inline calendar.
  - Not checked on a device.

**2026-11-01 — P4 done.**
- **Welcome:** iOS-style divided row groups, 17pt semibold rows, System/Light/Dark icons.
- **Create:** 40dp fingerprint well on the biometrics card; `lock.rotation`-style icon on the confirm field.
- **Unlock:** a "Local unlock only" card with shield and title.
- **Pair** (`screens/auth/pair/*`): rebuilt as the iOS 4-step flow (intro → code/UUID → Premium-required or linked → Continue to vault). It only adopts the account id and then returns to the normal Create screen. Errors map to `pair.*` messages, never raw server text. `PairFlowLogicTest` covers it.
- **Bug fixed on both apps (iOS `LoginView.swift`, Android `VaultSession.unlockMatching`):**
  - iOS created the vault with the raw password but trimmed it on unlock, so a password with leading or trailing spaces could never unlock on iOS.
  - Both apps now try the password as typed, then trimmed. Change Master Password stores trimmed passwords on both, which is why the fallback is needed.
  - Android seals the variant that actually unlocked.
- **iOS:** the biometrics row on Unlock now shows only when biometrics are enabled for the vault, matching Android. The iOS build succeeded on the iPhone 17 Pro simulator.
- **Remaining:**
  - Pairing codes resolve only inside the app process that published them, on both apps; there is no server endpoint, so across devices only the UUID links.
  - Android Settings has no "Show pairing code" row yet (P6: call `PairingSessionStore.shared.publish`).
  - Create has no "Already have a vault? Unlock" link; the Android create screen only appears when no vault exists.

**2026-11-01 — P5 done.**
- `theme/PremiumGate.kt` is the shared iOS-style free-tier gate (hint + star "Upgrade to Premium"), used by VPN and Breach.
- **Health** (`screens/tools/health/*`): Passwords / Reused / Weak cards push an issues screen with pill tabs and the vault's own rows, which open item detail through a local stack. The gauge counts up, is coloured by score and shows "out of 100".
- **Generator:** monospace single line that shrinks to fit; copy turns into a check for 1.5 s; disabled "Pronounceable" row.
- **VPN:** iOS layout (title, server pill, dial, Virtual IP / WireGuard cards, Security Features card).
- **Breach:** "Email Monitoring" header; Unresolved/Resolved chips and a ported `BreachCard`; rescan when the screen appears. The Android-only At Risk card was dropped (open decision 3 resolved toward iOS).
- **Collections:** a detail stack (list → collection → item) with Rename/Delete in a ⋮ menu. Rename is a new `repo.renameCollection`, which keeps `createdAt`.
- **Notifications:** "From Qasa" and "On this device" sections with dates. Tapping a server notification opens a detail sheet; `GET /v1/notifications/{id}` marks it read on the server. Mark All Read and swipe-to-delete apply to local notifications only.
- **Premium:** feature rows; accent border on the selected card; plans sorted by `sort_order` with last-good caching; FIB/FastPay/SuperQi badges with a trailing check; "Pay 10,000 IQD via FIB" with locale grouping; the page stays open after activation on the drawer page and only closes when shown as a sheet.
- **Bug fixed:** `QasaFirebaseMessagingService` navigated to Notifications whenever a push *arrived*. It now navigates only on tap, as on iOS.
- **Product decisions open on both apps:**
  - The VPN kill switch and auto-connect toggles are not persisted or acted on, but the copy promises behaviour.
  - Breach "Rotate Password" does nothing.
  - The server has no mark-read, mark-all or delete endpoint for remote notifications.
  - Nothing creates local notifications outside iOS DEBUG sample data.
- `theme/QasaComponents.kt` is 600 lines, over the 400 cap; that predates this work, so split it in P7.

**2026-11-01 — P6 done.**
- **Settings** (`screens/settings/main/*`):
  - Sections in iOS order with a leading icon on every row; iOS profile card.
  - Auto-lock row opens `AutoLockSheet` (1–60 stepper, presets, help text).
  - Linked Devices section with "N Active", this device, "Show pairing code" (publishes via `PairingSessionStore`), and Sync on this network.
  - Language and Theme open dropdowns.
  - Status line under Data & Account.
- **LAN:** Host/Join segmented control with ≥4-digit validation.
- **Legal:** `bodyRes` is now required and the layout is plain, as on iOS.
- **CSV** (`vault/csv/*`, `screens/settings/csv/*`): a port of iOS `VaultCSV`. Same 16 columns, escaping and record parsing. The `fields` JSON is written like Apple's `JSONEncoder(.sortedKeys)` through an explicit Android↔iOS field mapper, so files exchange in both directions. Same fingerprint dedupe and status messages. `VaultCsvTest` (15 tests) checks the 13-type round trip and that Android's export of an iOS sample is byte-identical.
- **Linked Devices:** tier pill with dot, iOS banners and info note, richer cards, Log out / Unlink chips with confirmation, unlinking this device clears the licence, session-expired message and the lastLimitError fallback.
- **Manage Subscription:** iOS details card; `SubscriptionService` returns the updated subscription; Skip on the reason sheet sends whatever was typed, as on iOS.
- **Android-specific copy kept on purpose:** the Autofill subtitle uses `settings.autofillSubtitleAndroid`, because iOS's text names an iOS Settings path.
- **New keys** need native ar/ckb review: `common.decrease`, `common.increase`. (`settings.exportSaved` was removed in P8.)
- **iOS copy bugs found (P8):**
  - `settings.exportPrepared` still says "copied to the clipboard", but iOS saves a file.
  - A bad CSV header shows "Import failed: Import failed: …" because the message is wrapped twice.
- **Still open:** clear-data wipe scope and the biometric subtitle (decisions 1 and 2) are unchanged.

**2026-11-01 — P7 done.**
- **Drawer** (`screens/shell/DrawerPanel.kt`, `DrawerWidgets.kt`):
  - The Android-only Upgrade row is gone.
  - Header: fixed above the list with shield + "Qasa" in sans ExtraBold.
  - Rows and labels: iOS selected/unselected styles, 12pt semibold section labels in `textTertiary`.
  - Trailing `strokeChrome` edge, which flips in RTL; scrim 0.45.
  - iOS-mapped icons; Privacy uses PanTool (`hand.raised`).
- **Headers:** titles are 20 Bold; lockup gaps are 12 and 10; BrandHeader no longer has a phantom leading slot.
- **Theme:**
  - `QasaComponents.kt` is split into `QasaControls.kt`, `QasaTextField.kt` and `QasaPressable.kt`.
  - NavChevronRow: 15 medium title, 12 regular sans subtitle.
  - Press scale is now opt-in, used only on buttons and the FAB. **Bug fixed:** the interaction source was never wired, so the scale had never fired anywhere.
- **VaultStoreErrorView:** respects system-bar insets; uses iOS spacing.
- **Privacy shield:** 180 ms fade; iOS spacing.
- **Root overlays** (`theme/RootOverlays.kt`): a "Securing vault" card while `onVaultUnlocked` runs, and an API-misconfigured banner (`QasaConfig.isBaseUrlValid`: release builds require https).
- Every Android Kotlin file is now ≤400 lines.
- **Smoke test:** the APK installs and launches on the API 37 emulator with no crash. The UI was not inspected, because FLAG_SECURE blanks screenshots.
- **Remaining:**
  - iOS waits for bootstrap before showing the shell; Android shows the shell with the card over it.
  - The banner sits under the privacy shield on Android and outside it on iOS.

**2026-11-01 — P8 done (iOS).**
- `CreateVaultView`: at least 8 characters, the Android rule. A too-short password shows `settings.weakMasterPassword`.
- `PasswordGeneratorModal.regenerate`: one character from each enabled class, then fill and shuffle (Android `PasswordGenerator` parity).
- `VaultItemEditor`: ID issue and expiry dates use `ValidatedDateField`.
- `VaultListView`: an empty search shows `vault.noSearchMatches`.
- Deleted the unused SwiftData template files `ContentView.swift` and `Item.swift`. Backups are in the session scratchpad `ios_backup/`.
- **Catalog fixes:**
  - `settings.importBadHeader` no longer starts with "Import failed:" (iOS wrapped it twice), and its ckb text no longer says the import *succeeded*.
  - `settings.exportPrepared` no longer says "to the clipboard".
  - `settings.exportSaved` was removed; Android uses `exportPrepared` and wraps the bad-header error in `importFailed` like iOS.
- **Font:** resolved. Both apps now bundle IBM Plex Sans Arabic in four real weights for every language (design.md §3.1), so Kurdish/Arabic titles render bold.
- iOS build succeeded on the iPhone 17 Pro simulator. There is no iOS test target.

**2026-11-01 — Product decisions applied.**
- **Clear Local Data:** Android now also clears the licence, as iOS `resetVaultAuth` does (decision 1).
- **Biometric subtitle:** iOS now shows the same seal-state subtitle as Android (sealed / no biometrics / sealed on next unlock), via `VaultKeychainStore.biometricPasswordStored`, which checks without prompting (decision 2).
- **Breach Rotate Password,** on both apps from one spec:
  - Finds logins by email or username.
  - Generates a new password and offers Regenerate and Copy.
  - Saves only after the "Replace saved password?" confirmation, replacing just that item's password.
  - Files: iOS `Views/RotatePasswordSheet.swift`; Android `screens/breach/RotatePassword*.kt`, covered by `RotatePasswordLogicTest`.
- **VPN kill switch / auto-connect:** premium-only (the page is gated, and free users get the upgrade prompt). Now saved on both apps (`qasa.vpn.killSwitch` defaults on, `qasa.vpn.autoConnect` off).
- **Server-dependent items moved to `docs/server-followups-plan.md`:** LAN pairing with Argon2 (Rust) plus free tier → upgrade in Pair; the real VPN tunnel so the toggles act; remote-notification read/delete endpoints.
- Translation review and device QA: the product owner will do both.
- Android: 75 unit tests pass; the iOS build succeeded.

**Plan complete. Earlier list of open items (superseded by the entry above):**
1. **Decisions:**
   - Clear Local Data: should Android also clear the licence (iOS does)?
   - Biometric row subtitle: Android shows the seal state, iOS shows nothing.
2. **Copy that promises missing behaviour (both apps):**
   - VPN kill switch / auto-connect.
   - Breach "Rotate Password".
   - Pairing codes resolve only on the device that published them.
3. **Server gaps:** no mark-read / mark-all / delete for remote notifications; no pairing-code endpoint.
4. **Review:** native ar/ckb review of every key added on 2026-11-01. `error.decoding` ("Unexpected server response.") is also shown for a non-UTF-8 CSV on both apps.
5. **Device QA:** none of P0–P8 was inspected visually, because FLAG_SECURE blanks screenshots and uiautomator. Run side-by-side manual QA in en and ar, light and dark.

## P0 — Android language + RTL (blocker for everything else)

Today, choosing ckb/ar on Android only swaps the font (`A/MainActivity.kt` passes `language` to `QasaTheme` only). Strings and layout direction follow the **device** locale.

- Use `AppCompatDelegate.setApplicationLocales(...)` when `prefs.language` changes (activity → `AppCompatActivity`), add `res/xml/locales_config.xml` + `android:localeConfig`.
- Make sure `LocalLayoutDirection` is RTL for ckb/ar (the drawer in `A/screens/shell/AppShellScreen.kt` already reads it).
- `A/theme/QasaTheme.kt`: build the M3 `Typography` from the selected language instead of `forLanguage(AppLanguage.EN)`, so dialogs use the Arabic-script rules (zero tracking, no mono).
- `A/prefs/AppPreferences.kt`: default language from the device locale (iOS `inferredLanguage`), and PATCH locale to the server like iOS `syncLocaleToServer`.

**Exit:** Switching to Arabic in Welcome or Settings flips the whole app to Arabic + RTL without a restart, on an English phone.

---

## P1 — Broken or unsafe behaviour (small, high impact)

| Fix | Where (Android unless noted) |
|---|---|
| Confirm before delete item ("Delete Item?") | `screens/vault/VaultItemDetailScreen.kt` (deletes immediately) |
| Confirm before delete collection; clear `collectionId` on its items | `screens/tools/CollectionsScreen.kt`, `vault/VaultRepository.kt` `deleteCollection` |
| Confirm unlink / log out / unlink-all devices; guard unlinking the primary | `screens/settings/LinkedDevicesScreen.kt` |
| Single cancel button → "Keep Premium / Continue" confirm → reason sheet | `screens/settings/ManageSubscriptionScreen.kt` (two buttons, both cancel at once) |
| Confirm before removing a breach-monitored email | `screens/breach/BreachMonitorScreen.kt` |
| "Lock Vault?" confirm; render as row not filled button | `screens/settings/SettingsScreen.kt` |
| Remove user-visible debug text: "Import CSV (parse only)", "Empty export stub (iOS parity)", "Secure LAN sync (placeholder blob — iOS parity)", "Checkout context (mock)", "Secure (matched=…)", raw `statusRaw` "atRisk" | Settings, LANSync, VaultList, BreachMonitor |
| Health `isWeak`: port iOS rule (<10 chars, <2 classes, or <12 with <3 classes, or common); trim and skip blank passwords | `screens/PasswordHealthAnalyzer.kt` — same vault currently scores differently per app |
| Row copy button copies the **password** (`primaryCopy`), not the subtitle/email | `screens/vault/VaultListScreen.kt` |
| Premium: disable Pay while processing (double tap can create two intents); only close when licence confirms premium; block when already premium | `screens/premium/PremiumUpgradeScreen.kt` |
| Subscription load/actions wrapped in try/catch with an error state | `ManageSubscriptionScreen.kt` |
| VPN dial: `contentAlignment = Center` (rings stack top-left); rings turn accent when connected; kill switch default **on**; `lastError`/busy as `StateFlow` | `theme/QasaHeroWidgets.kt`, `screens/vpn/VPNScreen.kt`, `vpn/VPNService.kt` |
| CheckToggle off-state thumb sits in the centre (offset 1dp → −12dp) | `theme/QasaListWidgets.kt` |
| Editor password / security code shown as plain text → secure field + eye toggle | `screens/vault/VaultItemEditorScreen.kt` |
| Detail screen can never reveal a secret → eye toggle | `VaultItemDetailScreen.kt` |
| Change master password: ≥8 chars, new ≠ current, mapped errors ("Wrong master password") instead of raw `e.message`, success message | `screens/settings/ChangeMasterPasswordScreen.kt` |

**Exit:** No destructive action without confirmation; no debug strings visible; health score identical on both apps for a shared test vault (add test vectors to both test suites).

---

## P2 — Android string sweep

Replace every `Text("…")`, `title = "…"`, `label = "…"`, `placeholder = "…"` in `A/screens/**` with `stringResource(R.string.<key>)` from the shared catalog (`l10n/strings.json` → `generate.py`). Where Android copy differs from iOS, **iOS wins** (examples: "Unlock Qasa", "Link Device", "Secure Notes", "Personal Info", "Copied!", "Numbers (0-9)", "No Items", "No Collections", "No Notifications", "Manage Subscription", "Email Monitoring", "Qasa Premium"). Add new keys to `l10n/strings.json`, never to platform files directly. Can be done area by area alongside P3–P6, but every screen touched in later phases must leave with zero literals.

**Exit:** `grep -rn 'Text("' A/screens` returns nothing user-facing; `generate.py --check` clean.

---

## P3 — Vault (largest functional gap)

The Android payload model (`A/vault/VaultPayload.kt`) already stores every iOS field; the UI just doesn't expose them.

1. **Editor** (`VaultItemEditorScreen.kt`, ~171 lines vs iOS `VaultItemEditor.swift` ~761): port every field per kind from `I/Views/Vault/VaultItemEditor.swift`: login collection; note colour, Protect Item, collection; card note + colour; bank country; name honorific/middle/birth date/birth place; email type; phone type/country/dial code; address zip/country/recipient/building/floor/apartment/door code/linked phone; ID country/issue/expiry/issue place. Pickers: Country, Collection, NoteColor, Honorific, Email/Phone kind, AddressPhone. Split into per-kind files to stay under the 400-line cap.
2. **Validated date field** (port `I/Components/ValidatedDateField.swift`) for card expiry, birth date, ID issue/expiry.
3. **Generator sheet** from the password field with "Use Password" (reuse `GenerateScreen` content).
4. Editor chrome: kind-specific title ("New Login", "Edit Passport"), Save disabled until a name is entered, "Could Not Save" error.
5. **Detail** (`VaultItemDetailScreen.kt`): port `detailFields` from `I/Models/VaultEnums.swift` exactly (hide blank fields instead of "—"; bank routing and ID number not secret), tap-row-to-copy, Organization card (colour, collection) and History card (created/updated/last used) + `markUsed`, Edit/Delete as top-bar icons.
6. Edit from detail opens the editor sheet **over** the detail screen (today it pops to the list first).
7. **List row**: overflow menu (per-field copy, Open Website, Edit, Delete), "Copied X" snackbar + haptic, per-subtype icons.
8. **Add flow**: tab-aware FAB (Login/Note tabs open the editor directly; other tabs show their subtypes; "All" shows 5 kinds → subtypes). Title "Add New".
9. **Suggested + Most Recent** sections: port `VaultSuggestionEngine` / `SuggestedContext` and the recents rule.

**Exit:** Create one item of each of the 13 kinds on iOS with all fields filled, then on Android: same fields, same order, same secrecy, same actions.

---

## P4 — Onboarding / auth

- Welcome: title "Welcome", "Appearance" section with System/Light/Dark icons, divided list, localized theme names.
- Create: pair link copy "I already have Qasa on another device", 40pt fingerprint icon well, hint copy. Password rule: see P8.
- Unlock: iOS copy, "Local unlock only" card with shield icon, trim password on both. Decide biometrics row behaviour when unavailable (recommend: hide on both).
- **Pair** (`A/screens/auth/PairExistingVaultScreen.kt`): rebuild as the iOS 4-step flow from `I/Views/PairExistingVaultView.swift` — intro → 6-digit code (or UUID) → Premium-required card or success → "Continue to vault" → normal Create screen. Title "Link Device" with Cancel. Today Android is a single UUID + password form with no confirm field or strength meter.

---

## P5 — Tools + Premium

| Screen | Work |
|---|---|
| Password Health | Cards = Passwords / Reused / Weak, each pushes an issues screen (pill tabs, tappable rows → detail, empty states); gauge number coloured by score with count-up + "out of 100" |
| Generator | Monospace 28 bold single line with auto-shrink; copy icon → check, resets after 1.5 s; disabled "Pronounceable" row |
| VPN | iOS structure: "Qasa VPN" title, server pill (city — node / Auto-select), Virtual IP + WireGuard metric cards, "Security Features" heading, spinner in dial while busy |
| Premium gates (VPN, Breach) | One shared gate composable matching iOS: hint text + "Upgrade to Premium" button with star, no card |
| Breach | Unresolved/Resolved chips + `BreachCard` composable (port `I/Views/BreachCard.swift`); rescan on appear; email validation + dedupe; slot-limit dialog; "Saving…" state; header "Email Monitoring" + "Premium" pill |
| Collections | Collection detail screen (items in it), overflow with Rename + Delete |
| Notifications | "From Qasa" + "On this device" sections, date per row, tap → detail sheet that marks read, Mark All Read, swipe to delete, deep link, error text |
| Premium | Feature bullets with checks + accent border on selected plan; FIB/FastPay/SuperQi badges and iOS names, trailing check; "Pay 10,000 IQD via FIB" (grouped amount, short name); sort plans by `sort_order`; localized period/plan titles; header "Upgrade" / "Qasa Premium" with X when a sheet |

---

## P6 — Settings

- Sections in iOS order: Security / Linked Devices ("N Active", this device, Show pairing code, Sync on this network) / Appearance / Data & Account. Leading icons on every row.
- Profile card: person icon, email, "Premium Account"/"Free Account" pill with trophy, full-width CTA.
- Auto-lock: row showing "N min" → sheet with 1–60 stepper, presets 1,2,5,10,15,30,45,60, help footer.
- Theme/Language: native language names (کوردی, العربية), localized theme labels.
- **CSV import/export**: port `I/Services/Vault/VaultCSV.swift` (parse, fingerprint dedupe, status messages) with SAF `OpenDocument` / `CreateDocument`. Effort L.
- Status line for import/export/clear results.
- Linked devices: "Premium · 2 / 5" pill with tier dot; platform + location icons, "Activated …" date, lock + tap-to-upgrade on free cards; per-device Log out / Unlink chips; "Device limit reached" banner; info note; loading + session-expired states; unlink-all visible only when non-primary devices exist.
- Manage subscription: plan display name, Active/Cancelling pill, amount in IQD, started/cancelled dates, explanatory copy; auto-renew disabled when cancelled, rollback on failure; loading state.
- LAN sync: Host/Join segmented control, ≥4-digit code validation, "6-digit code" placeholder, `lan_*` copy.
- Legal: separate `legal_termsBody` / `legal_privacyBody`, localized "Last updated", plain layout (no card), title 24 bold.

---

## P7 — Chrome polish

- Drawer (`AppShellScreen.kt`): **remove the extra "Upgrade" row**; shield glyph + "Qasa" header fixed above the scroll; selected = `cardBackground` fill + `textPrimary` 16 semibold + accent icon; unselected = `textSecondary` 16 regular, ~18dp icon, 2dp row spacing; section label 12 semibold sans `textTertiary`; trailing 1dp `strokeChrome` edge; scrim 0.45; distinct Privacy icon; premium lock 11–12dp.
- Page title 20 **bold** (`theme/chrome/AppChromeHeaders.kt`); brand lockup gap 12 / 10.
- `NavChevronRow`: title 15 medium, subtitle/trailing 12 regular sans (not mono).
- `VaultStoreErrorView`: `statusBarsPadding()`, spacing 24/20/12/8.
- Privacy shield: gap 14, fade in/out.
- Root overlays: "Securing vault" progress card and API-misconfigured banner (`I/qasaApp.swift`).
- Drop the 0.97 press-scale on list rows (keep on primary buttons) so rows don't shrink on tap.

---

## P8 — iOS-side changes

| Change | Why |
|---|---|
| Create vault requires ≥8 characters (`I/Views/CreateVaultView.swift`) | Android requires 8, iOS accepts 1 — pick the safer rule |
| Generator guarantees one char of each enabled class (`I/Views/Vault/PasswordGeneratorModal.swift`) | Android already does; iOS can return a "symbols on" password with no symbol |
| ID issue/expiry use `ValidatedDateField` | Only card/birth dates are validated today |
| "No matches for this search" hint on empty search | Android has it; iOS shows a generic empty state |
| Delete unused SwiftData template `I/ContentView.swift` (+ `Item.swift` if unused) | Dead code |
| ~~Bundle a real Bold weight for Kurdish/Arabic~~ Done: IBM Plex Sans Arabic, four weights | Resolved |

---

## Open decisions (recommendation first)

1. **Clear data scope** — iOS also clears the licence, Android only clears the vault. *Recommend: iOS behaviour on both, with the iOS copy.*
2. **Biometric row subtitle in Settings** — Android shows seal state, iOS shows nothing. *Recommend: show state on both.*
3. **Breach "At Risk" summary card** (Android only). *Recommend: drop it, iOS layout.*
4. **iOS DEBUG stub payment** (fakes the webhook). *Recommend: keep, debug-only.*

## Verification

- Shared test vectors for health score, password strength and generator class guarantee, run in both test suites.
- Side-by-side screenshots per screen in **en** and **ar** (RTL), light and dark — iOS simulator + Android emulator.
- 13-kind create/edit/detail round-trip (P3 exit).
- Grep gate for string literals in `A/screens` (P2 exit).
