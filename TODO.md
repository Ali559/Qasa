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
- [ ] **Icons, follow-up:** walk each app side by side and make the same thing use the same
      icon everywhere. Each app was converted separately, and a few were picked by judgement:
      desktop "Delete account" uses `device-remove`, its add-item "Personal info" uses `person`,
      and Auto-backup and Restore both show `history`. Wanted in the library: a puzzle
      (browser extension), an "open app" window, an image (scan a QR from a picture) and a
      vertical ellipsis. Not yet looked at on Android or desktop screens beyond the unlock
      and create-vault screens.
- [ ] **Suggest a strong password when the user signs up somewhere.** Check every app: when
      autofill sees a new-account or new-password form (sign-up on a site or app), offer to
      generate a strong password and save it as a new login. Very important.
- [ ] **Item colours do nothing.** Picking a colour on an item must change how the item shows
      (its tile and accents), with a light and a dark value for each colour. The picker must
      also show which colour is selected.
- [ ] **No Cancel when adding or renaming a folder.** The folder sheet closes by dragging down,
      like every other sheet.
- [ ] **Item options (the ⋯ button) and every dropdown use the app's own sheet**, not the
      system menu: same sheet header, rows and colours on every app.
- [ ] **Dark mode: the background is too close to the primary colour.** Make the dark
      background a dark ash grey (reference: WhatsApp's text-field background in dark mode),
      clearly apart from the purple accent. The Home screen may keep its warm/calm blend.
- [ ] **Protected items:**
  - Opening one shows only the main password / biometric sheet, on a plain cover: the item
    must not be visible behind it.
  - Once open, its contents stay hidden like a password, with the eye button (crossed /
    uncrossed) to show each value.
- [ ] **You → Tools: dividers between rows**, like the Security section in Settings.
- [ ] **The Notifications page always says "Notifications"** in its header, never "Breached
      passwords". The Premium prompt for breached passwords inside it stays.
- [ ] **First launch:**
  - Screen 1: choose the language only (no theme picker).
  - After "Get started": at most 5 carousel screens about the app's free features, in the
    chosen language, with a sleek animated design.
  - The last screen has "Let's get started" and a name field with a cheeky, short prompt
    (along the lines of "What should we call you?").
  - **Show previews before building it.**

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

- [ ] **Look at the result.** Nothing past the welcome screens was checked by eye:
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
