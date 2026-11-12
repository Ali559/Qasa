# Desktop parity notes

Taken 2026-11-12 from `phase-7-wrap-up`, with the Vite dev server (`desktop-ui` in
`.claude/launch.json`) and the browser mock backend (`src/vault/mock.ts`,
`src/account/api.ts`, `src/vault/tools.ts`), driven by headless Chromium (Playwright
1.49) at a 1100 × 760 window, 2× pixels, scaled to 500 px WebP at quality 80.

## Coverage

64 of 64 shots. Every screen on the shot list exists on the desktop:

- `09-editor` and `16-add-menu` are modal sheets over the Passwords screen.
- `11-alerts` is the Alerts tab (breached passwords).
- `15-collections` is "Folders" (You → Folders).

## How the shots were set up

- **Theme and language:** the app's own prefs (`qasa.prefs` in localStorage, the browser
  fallback), with the theme set to `light` or `dark` explicitly and the emulated colour
  scheme matching.
- **Reduced motion:** on for every shot so that screens settle. The tour therefore shows
  page 2's demo in its end state (email typed, password filled), which is what "once it has
  finished playing" asks for.
- **Fake vault:** the mock's built-in seed (Gmail, Facebook, Instagram, FIB, Asiacell,
  Snapchat, Netflix, Slack, FIB Card, two notes; folders Work, Personal and Bank). The name
  was set to "Sara" on the name screen. No real data.
- **Premium:** turned on through the mock's own `paymentStatus` so that Alerts and Health
  match the iOS reference, which is premium.
- **Health report:** the browser mock's `toolsApi.health()` is hard-coded to
  `breachLocked: true`, so Alerts can never show results. For the shell shots it was
  replaced at runtime, in the page and not in the source, with the iOS reference numbers:
  8 passwords, 2 reused, 2 weak, 1 breached (Gmail, seen 119,167 times), score 39.
- **Unlock:** biometrics defaults to on at sign-up in the mock, and the Unlock screen then
  starts the mock prompt and unlocks by itself. The page was loaded with `?mockBio=cancel`
  so that `05-unlock` stays on the Unlock screen, with the fingerprint row showing.

## Findings

1. **Fixed: RTL phone numbers and card digits in list rows (ar, ckb).** The Passwords
   list subtitles used to show `1290 555 0770` for Asiacell, `4821 123 0750` for FIB and
   `1111 ••••` for FIB Card. With the subtitle now in `<bdi>` (VaultRow.vue), the re-taken
   `07-passwords-ar-light` and `07-passwords-ckb-dark` show `0770 555 1290`,
   `0750 123 4821` and `•••• 1111`, as on iOS. Phone-number fields in the detail pane were
   not checked.
2. **Fixed: mixed digit systems in ckb.** Password health used to show the score `39` with
   the caption `لە ١٠٠`. Numbers now go through `formatLocale()` (`-u-nu-latn`) and the ar
   and ckb strings no longer contain Eastern digits. The re-taken shots use Western digits
   throughout: `لە 100` and "How scoring works" (`12-health-*`), `119,167` (`11-alerts-*`),
   `5 خولەک` and `1 چالاک` (`14-settings-*`), and `4 بۆ چاککردن` (`06-home-ckb-dark`).
3. **FAB glyph differs from iOS.** The desktop's + button uses a circled-plus glyph in a
   smaller round button. iOS uses a plain bold "+" in a larger button. See `07-passwords-*`.
4. **The Alerts title reads "Notifications".** The landing-page iOS screenshot
   (`alerts-en-light.webp`) is titled "Breached passwords", but the current iOS code
   (`BreachedPasswordsView.swift` → `notifications.title`) also says "Notifications", so
   the desktop matches the code. The landing screenshot is probably out of date.
5. **Language rows on the Welcome screen** use the `language` glyph, which reads as a
   radio or broadcast symbol at 16 px. This is worth a look next to iOS. See
   `01-welcome-*`.
6. **The add-item menu is a bottom sheet** centred on the content area and flush with the
   bottom of the window. All five rows fit at 760 px, with no space below the last row.
   See `16-add-menu-*`.
7. **The tour page 2 demo card has an empty band at the bottom** in its end state: the
   space kept for the "Use a strong password" suggestion, which has gone by then. iOS
   should be checked for the same thing.
8. **A 404 on load.** Each load of the dev page logs one `Failed to load resource: 404`
   in the console. It is probably a missing static asset such as a favicon; this was not
   traced. It doesn't affect the UI.
9. **Browser mock gaps (not app bugs):** health is always breach-locked, and biometrics
   auto-unlocks on the Unlock screen unless `?mockBio=cancel` is set. Either could gain a
   URL switch, like `?mockBioDue`, to make future captures simpler.
10. **Fixed: generator ranges in RTL.** The character-set labels now read `(A-Z)`,
    `(a-z)` and `(0-9)` in ar and ckb; they are isolated as left-to-right text. See the
    re-taken `10-generator-ar-light` and `10-generator-ckb-dark`.

Re-taken on 2026-11-12 after these fixes, with the same setup: `07-passwords`,
`10-generator`, `11-alerts`, `12-health` and `14-settings` in ar-light and ckb-dark, and
`06-home-ckb-dark`. No new problems showed up in them.

Nothing else looked broken. Dark mode, RTL mirroring (sidebar on the right, back
chevrons, toggles, sliders and strength bars) and the Kurdish and Arabic copy all rendered
without clipping or overlap at 1100 × 760.
