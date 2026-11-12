# Design parity across every app

Added 2026-11-11. **The iOS app is the reference design.** Android, the desktop app and the
browser extension follow it as closely as their platforms allow: same sizes, radii, spacing,
fonts, weights, colors, animation timings and component shapes, down to the pixel. No
shortcuts: when an app differs from iOS, the other app changes.

The rules are in [`design/README.md`](design/README.md). Per-app open items stay in
`apps/<app>/TODO.md`; this file is for work that spans apps.

**Every item below is a shared rule** for iOS, Android, the desktop app and the browser
extension (where it applies). No app makes a design rule of its own: a rule goes into
`design/README.md` (and its values into `design/tokens.json`) first, then into every app.
Any change to what a screen looks like also updates the landing page's app mockups
(`apps/landing`).

## 0. Next (added 2026-11-12)

- [x] **Icons: one Solar Linear library for every app** (2026-11-12, branch `solar-icons`).
      `assets/icons` → every app (`generate.py`, checked in CI); SF Symbols, Material and
      Lucide are gone. Active navigation tabs show the filled glyph. Fingerprint and Wi-Fi
      come from Tabler at Solar's line weight. Credit under About on the You screen.

### How each phase runs

1. Write the rule into `design/README.md` (and any values into `design/tokens.json` /
   `assets/icons/icons.json`) first.
2. Build it on iOS, then Android, the desktop and the extension: one branch and one PR per
   phase, so no app is ever ahead of the others.
3. Check it on screen on every app (light and dark, English and Arabic), and update the
   landing page mockup of any screen that changed.

### Phase 1: Small fixes (one PR)

- [x] **No Cancel when adding or renaming a folder.** (2026-11-12, branch `phase-1-small-fixes`:
      the shared one-field prompt sheet on iOS, Android and the desktop; also the 2FA setup key.) The folder sheet uses the shared sheet
      header and closes by dragging down (Esc / click outside on the desktop); its own action
      (Create / Save) stays as the bottom button. Check every one-field prompt sheet for the
      same Cancel.
- [x] **You → Tools: dividers between rows** (Android was the one missing them; it now uses
      `SettingsSection` like iOS), the same `RowDivider` inset as Settings →
      Security, on every app.
- [x] **The Notifications page always says "Notifications"** (the Alerts tab header) in its header, never "Breached
      passwords". The Premium prompt for breached passwords inside it stays.
- [x] **Icons follow-up:** (personal info `identity`, Auto-backup `backup-schedule`, row options
      `more-vertical`, plus `image`, `extension`, `open-app`; Delete account was already the same) walk the apps side by side and make the same thing use the same
      icon everywhere. Known: the desktop's "Delete account" (`device-remove`) and add-item
      "Personal info" (`person`); Auto-backup and Restore both show `history`. Add the icons the
      desktop asked for (puzzle for the browser extension, an "open app" window, an image for
      "scan a QR from a picture", a vertical ellipsis) to `assets/icons/icons.json`.

Done when: all four apps show the same thing for each, in light and dark, LTR and RTL.
Still open: the on-screen check on each app, and re-taking the landing `alerts-*` screenshots
(the header now says "Notifications"; Phase 7 re-takes them all).

### Phase 2: Colours (one PR)

- [x] **Dark mode background.** (2026-11-12: candidate A, neutral ash #1C1D1F / cards #26272A.) Today the dark background (#17111D) is too close to the purple
      accent. Make it a dark ash grey, in the spirit of WhatsApp's dark text-field background,
      clearly apart from the accent (#B67FDB). Adjust the cards, strokes and chrome that sit on it
      so the steps between them stay visible. Keep the Home screen's warm/calm blend. Only
      `design/tokens.json` changes, then `design/generate.py`. Show 2–3 candidate greys as a
      preview before choosing.
- [x] **Item colours.** (2026-11-12: `note*` / `note*Tile` tokens; tile, hero and picker on iOS,
      Android and the desktop. The extension shows no notes or cards.) Picking a colour on an item does nothing today. Each of the six note
      colours gets a light and a dark value in `tokens.json`. The item's tile (vault list and
      detail hero) and its accent use the chosen colour on every app. The picker shows the
      selected colour clearly in both themes.

Done when: dark mode reads as grey with purple accents on all four apps, and a coloured item
looks the same everywhere.

### Phase 3: The app's own menus (one PR)

- [x] **Item options (⋯) and every dropdown use the app's own sheet**, not the system menu
      (2026-11-12: `OwnSheets.swift`, `QasaOwnSheets.kt`, `ActionSheet.vue` / `PickerSheet.vue`;
      design/README.md rule 6a). Original list:
      - An action sheet component on each app: the shared sheet header, settings-style rows with
        icon, label and destructive red, and no Cancel.
      - A picker sheet for dropdowns (type, country, collection, phone): rows with a check on the
        current choice and search for long lists (countries).
      - Replace every system `Menu` / `DropdownMenu` / `<select>` on all apps.
      - This also fixes the oversized icons that iOS system menus draw.

Done when: no screen in any app opens a system menu, except the system share sheet and the
file pickers.

### Phase 4: Protected items (one PR)

- [x] **The unlock sheet only.** (2026-11-12, rule 6b: `ProtectedUnlockSheet` on every app) Opening a protected item first shows a plain cover with the main
      password / biometric sheet; the item is never drawn behind it. If the user cancels, they
      go back to the list.
- [x] **Hidden values.** (`ProtectedItemGate` on every app, with unit tests) Once open, every value of a protected item is masked like a password,
      each with the eye button (crossed / uncrossed). Copy works without revealing. Values hide
      again when the user leaves the item or the app.

Done when: on all apps a protected item's contents never show without the unlock and a tap on
the eye. Add a test for each app's gate.

### Phase 5: Suggest a strong password at sign-up (one PR per platform; very important)

- [x] **Audit what exists.** (2026-11-12: `docs/strong-password-signup.md`)
      - The extension's `detect.ts` already tells sign-up and change-password forms from
        logins.
      - Android's autofill service knows `newPassword` fields and can save.
      - iOS has the AutoFill "insert text" entry point (`prepareInterfaceForUserChoosingTextToInsert`).

      Write down, per platform, what the user sees today on a sign-up form.
- [x] **Browser extension:** on a sign-up or change-password form, the in-page menu offers
      "Use a strong password". It fills both password fields with a password from the shared
      generator, and once the form is submitted it offers to save a new login (site, username,
      password).
- [x] **Android:** for a `newPassword` field, the autofill service returns a "Generate strong
      password" dataset. Choosing it fills the generated password, and `onSaveRequest` saves the
      login.
- [x] **iOS:** find what AutoFill allows a third-party provider on iOS 17 and 18+ (text
      insertion of a generated password, the save prompt). Ship the best that's allowed, and
      write down any limit Apple imposes.
- [x] **Desktop:** covered through the extension (accepting a save reloads the list); make sure a login saved from the extension
      shows up in the app straight away.

Done when: signing up on a test site (and a test app on each phone) offers a strong password in
one or two taps and ends with the login saved.

### Phase 6: First launch (preview first, then one PR)

- [x] **Previews.** 2–3 directions for the welcome flow, animated, in the app's design and in
      English and Arabic. The owner picks one before anything is built. (2026-11-12: three
      directions in `design/previews/first-launch.html`; the owner picked B "Live demo".
      Rule 6d in `design/README.md`.)
- [x] **Screen 1: language only.** Remove the theme picker; the app follows the system theme
      until changed in Settings.
- [x] **Carousel.** After "Get started": at most 5 screens about the free features, in the
      chosen language, with a sleek animated design (respecting reduce motion).
- [x] **Last screen.** "Let's get started" and a name field with a short, cheeky prompt
      (something like "What should we call you?"). The name is stored on the device and used in
      greetings.
- [x] Same flow, copy and timings on iOS, Android and the desktop; new strings in
      `l10n/strings.json` (en/ar/ckb).

Done when: a fresh install on every app goes language → carousel → name → create vault, in all
three languages.

### Phase 7: Wrap-up

- [x] **Landing page.** Re-take every app screenshot and mockup on `apps/landing` from the
      updated apps (icons, fields, colours, dark mode, onboarding), in all three languages.
      (2026-11-12: all 18 iOS shots in `apps/landing/public/screens/` re-taken.)
- [x] **Side-by-side audit.** Section 3 below, with screenshots kept in `design/parity/`.
      (2026-11-12: 3 × 64 shots, findings and open decisions in `design/parity/NOTES.md`,
      browsable in `design/parity/index.html`.)

## 1. One shared design spec

- [x] **Spec from iOS** (2026-11-11): `design/tokens.json` holds every color, radius, size,
      gap, text style (IBM Plex Sans Arabic, line height 1.5 × size), timing, spring and
      shadow, read out of the iOS code.
- [x] **Generated into each app**: `python3 design/generate.py` writes `QasaTokens.swift` +
      `ThemePalette+Tokens.swift` (iOS), `QasaTokens.kt` (Android) and `tokens.css` (desktop
      and extension). Every app's theme layer reads them, iOS included. CI fails when a
      generated file is stale (`--check`).
- [x] **Rule checks**: `python3 design/check.py` (in CI) fails on a sheet Cancel/Done
      button, a hard-coded sheet radius, raw system error text, or a language read that
      bypasses the app setting (iOS).
- [ ] **Extend `design/check.py` to Android and the desktop** (raw `N.dp` / `N.sp` /
      `tween(N)` in `screens/`, raw px radii and font sizes in `.vue` styles).
- [ ] **Tokens still missing** (screens use raw values meanwhile): body 14/400, card titles
      16/600, hero titles 22/700, auth titles 28/700, card padding (17, iOS also uses 16/20/21:
      settle on one), FAB 56, home tile 40 r12, header button 44 r14, stepper 94×32 r8,
      segmented control 32 (r9/7), selection mark 20/22, tile radius 12, onDanger white.
- [ ] **iOS views still write raw sizes** (`.font(.qasa(13))`, paddings) instead of named
      styles. They are the reference, so the values are right, but moving them onto
      `QasaTokens.Typography` / `Space` is what keeps the other apps from drifting again.

## 2. Reported mismatches

- [x] **Notes field in the item editor**: Android and the desktop now use the iOS notes box
      (min height 112, grows, same fill, stroke and radius as a field), for every item type,
      create and edit, plus the cancel-subscription reason.
- [x] **Input and button corners**: smaller, 10 on every field and full-width button in
      every app (`radius.control`; it was 14 on iOS, 20 on Android).
- [x] **Bottom sheets**: one header everywhere: grabber 36×5, title 17 semibold centered
      20 from the top, 16 to the content, radius 28, no Cancel / Close / Done / ✕ / Skip.
      A sheet closes by dragging down (Esc or click outside on the desktop). Full-screen
      pages that can't be dragged (restore during setup) get a back button instead.
      Confirmation sheets keep their confirm + cancel buttons.
- [x] **Fonts**: every text style carries IBM Plex Sans Arabic and its weight on Android,
      the desktop, the popup and the in-page menu; line height 1.5 everywhere. Secrets stay
      in the system monospace, as on iOS.
- [ ] **(Item 4 of the original list was cut off at "there": fill in what it was.)**

## 3. Side-by-side audit

- [x] **Look at the result.** (2026-11-12: done, `design/parity/`; what's left to decide is
      in its `NOTES.md`.) Nothing past the welcome screens was checked by eye:
      the iOS simulator stalled under load, no Android emulator was running, and the
      desktop was only opened at its welcome screen. Walk every screen and sheet on all
      apps in light and dark, English, Arabic and Sorani, and keep screenshots in
      `design/parity/`.
- [ ] **Screens not yet compared line by line**: Android item detail, home, password health
      and generator bodies (only their shared parts changed); Android IconButtons with
      ripple in detail rows, TOTP row, collections and email rows.
- [ ] **Known platform differences, to decide**: iOS system Switch / Stepper / menu Picker
      vs Android M3 Switch, a hand-made stepper and a DropdownMenu; iOS shrinks long button
      labels to 0.8, Android doesn't; Android toast sits 88 above the bottom (iOS 76) because
      its tab bar is taller; the desktop health gauge counts up with a JS ease.
- [ ] **Keep it matched.** Any new screen or component lands on all apps with spec values;
      a UI change to one app is not done until the others match.

## 4. Language switching

- [x] **iOS password health in the wrong language** (fixed 2026-11-11): more than 200 lookups on
      password health, breach monitor, the item editor and detail, 2FA, rotate password and
      collections read the language straight from `UserDefaults`, which SwiftUI doesn't
      observe and which could disagree with Settings. They now read `AppPreferences`, like
      the rest of the app. Unit tests: `qasaTests/LanguageSwitchTests.swift`.
- [x] **Error messages**: iOS showed system and server errors in the device's language or
      in English ("Payment failed.", vault errors). They now go through `L10n.message(for:)`
      with new `error.generic`, `error.network` and `sub.notFound` strings.
- [x] **Android**: the autofill "Search Qasa" labels and VPN errors used the device language;
      numbers on health score, 2FA countdown and VPN load now use the app's locale.
- [x] **Desktop and extension**: the premium screens fell back to built-in English; the
      in-page menu kept the language the page loaded with. Both fixed.
- [ ] **Desktop**: native file-dialog filter names are English (`src-tauri/src/backup.rs:345`
      "Qasa backup", `src-tauri/src/vault/tools.rs` "Image", "CSV"); collections and vault
      sort with the system locale (`vault/session.ts`, `localeCompare` /
      `Intl.Collator(undefined)`).
- [ ] **Android** applies a language change by recreating the activity: immediate, but it
      drops open menus and scroll position. iOS keeps them.
- [ ] **Try a language switch by hand** on each app: change it in Settings and walk every
      screen, sheet, alert, toast, empty state and autofill UI.

## Also found

- [ ] `qasaTests/VaultSyncTests.swift` `deletedItemsNeverAppearInReads` fails in the full
      iOS run but passes alone: suites share `VaultRepository.shared` in parallel. Serialize
      those suites or give each its own repository.
