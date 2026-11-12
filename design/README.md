# Qasa design system

**The iOS app is the reference design.** Android, the desktop app and the browser extension
match it to the pixel: same sizes, radii, gaps, fonts, weights, colors, component shapes and
animation timings. When an app differs from iOS, that app changes. 1pt (iOS) = 1dp (Android)
= 1px (CSS).

## Files

| File | What |
| --- | --- |
| `design/tokens.json` | The values. The only place a size, radius, gap, type style, timing or color is written. |
| `design/generate.py` | Writes every app's token file. `--check` fails when one is stale (CI runs it). |
| `apps/ios/SharedAutofill/QasaTokens.swift` | Generated: `QasaTokens.Radius/Size/Space/Typography/Motion/Shadow/Light/Dark` (app and extensions). |
| `apps/ios/qasa_ios/Theme/ThemePalette+Tokens.swift` | Generated: `ThemePalette.light` / `.dark`. |
| `apps/android/.../theme/QasaTokens.kt` | Generated: `QasaTokens.Radius/Size/Space/Typography/Easings/Motion/Shadow/Light/Dark`. |
| `apps/desktop/src/theme/tokens.css` | Generated: CSS variables, used by the desktop app and the extension. |

To change the design: edit `tokens.json`, run `python3 design/generate.py`, build every app.
A change that only one app picks up is not done.

## Rules

1. **No raw values in views.** Sizes, radii, gaps, font sizes and weights, durations, curves
   and colors come from the tokens (through each app's theme layer: `Theme` / `Font.qasa` on
   iOS, `QasaDimens` / `QasaTypography` / `QasaMotion` / `qasaColors` on Android, CSS
   variables on the desktop). A value the spec lacks gets added to `tokens.json` first.
2. **One font: IBM Plex Sans Arabic**, bundled, four weights (400/500/600/700), for every
   language. Line height is the font's own, **1.5 × the size** (its ascent + descent): iOS
   gets it by default; Android sets `lineHeight = size × 1.5` with no extra font padding and
   centered line-height trimming off (`LineHeightStyle(Center, Trim.None)`); CSS sets
   `line-height: var(--line-height)`. Letter spacing is 0 in Arabic and Sorani.
3. **Controls.** Every single-line field and every full-width button is
   `size.fieldHeight` / `size.buttonHeight` (48) tall with `radius.control` corners and a
   1pt stroke (fields, secondary buttons). Multi-line notes start at `size.notesMinHeight`
   (112) and grow; same fill, stroke and radius as a field, padding `space.notesHorizontal` ×
   `space.notesVertical`. Buttons: `type.button` (16 semibold); pressed = opacity 0.85 and
   scale 0.98 over `motion.press`; disabled = opacity 0.45.
4. **Labelled fields are rows in a grouped card** (chosen 2026-11-11, like iOS Contacts).
   No label above a field and no separate box per field:
   - **Group**: the fields of one form section share one card: `cardBackground` fill, 1pt
     `stroke`, `radius.group` (20). Between rows a 1pt `stroke` divider, inset
     `space.groupDividerInset` (14) on the leading side, running to the trailing edge. Groups
     are `space.formGap` (16) apart. A screen with one field has a group of one row.
   - **Row**: at least `size.groupRowMinHeight` (60) tall, `space.groupRowVertical` (8) top
     and bottom, `space.groupRowHorizontal` (14) sideways. Inside, top to bottom, with no gap:
     the label, `type.groupLabel` (12 semibold) `textSecondary`, always visible; then the value,
     `type.groupValue` (16 regular) `textPrimary`, with an example hint ("name@example.com",
     never the label repeated) in `textPlaceholder` while empty. While the row is focused its
     label is `accent`. No leading icons. Tapping anywhere in the row focuses its input.
   - **Trailing controls** (reveal, generate, copy, scan, remove) sit at the row's trailing
     edge, vertically centered, `size.fieldIconButton` (32), `space.groupRowTrailingGap` (8)
     after the value.
   - **Errors about one field go on that field's row**: wrong or too-short password, passwords
     that don't match (on the confirm row), a wrong pairing code or account ID. Only messages
     about the whole form (a newer vault, no connection) stay as a line under the group.
   - **2FA row**: empty, the value is the accent "Add 2FA code" menu (scan, or type the setup
     key in the one-field prompt sheet) with the help text as a 12 caption; set, the value is
     the live code (mono 16), the issuer is a 12 caption, and a ✕ trailing removes it.
   - **Not rows**: the note colour swatches and the protect toggle keep their own layout
     (label above the swatches).
   - **Pickers, menus and dates** are rows too: label on top, the chosen value under it, a
     13pt chevron (`RowChevron`) trailing; tapping the row opens the menu or picker.
   - **Errors**: the row's label turns `danger` and a `size.groupErrorStripe` (3) `danger` bar
     runs down the row's leading edge (it mirrors in RTL). The message, `type.fieldError`
     (11) `danger`, sits `space.fieldErrorGap` (6) under the card, prefixed with the field's
     label when the group has more than one row ("Email: Enter an email like …").
   - **Notes / multi-line** fields get their own card below the group: `radius.control`
     corners, the label inside at top `space.groupNotesLabelTop` (9), text under it, min
     height `size.notesMinHeight` (112), growing.
   - Screen readers read each row's label as the input's name.
   - Not grouped: search fields and one-field prompt sheets with no label keep a plain
     48-tall box with a placeholder.
5. **Bottom sheets** (every platform, every sheet):
   - Corner radius `radius.sheet` (28), background `background`, no elevation tint.
   - Grabber: `size.sheetGrabberWidth` × `size.sheetGrabberHeight` (36 × 5), fully rounded,
     `textTertiary`, `space.sheetGrabberTop` (5) from the top edge.
   - Title: `type.sheetTitle` (17 semibold), `textPrimary`, **centered**, its top
     `space.sheetTitleTop` (20) from the sheet's top edge, then `space.sheetTitleBottom`
     (16) to the content.
   - **No Cancel button in the header.** A sheet closes by dragging it down, tapping outside,
     or the system back gesture. The sheet's own action (Save, Done, Connect…) stays as a
     full-width button at the bottom of the sheet. Confirmation sheets (title, message,
     confirm / cancel buttons) keep their buttons: they are the dialog's two answers.
   - **One-field prompt sheets** (new folder, rename folder, 2FA setup key) are sheets like
     any other: the shared header title, an optional `type.dialogMessage` line, the field,
     then the action (Create / Save / Add) as the bottom button, `space.sheetActionsGap`
     (20) apart. No Cancel: they close by dragging down (Esc or a click outside on the
     desktop).
   - Content sized to fit, never taller than 92% of the window.
6a. **Menus are the app's own sheets** (chosen 2026-11-12). No screen opens a system menu
   (`Menu`, `DropdownMenu`, `<select>`, a hand-made popover), except the system share sheet
   and file pickers. Segmented controls and date pickers are not menus and stay.
   - **Action sheet** (an item's ⋯, a folder's ⋯, "Add 2FA code"): a bottom sheet with the
     shared header (the item's or folder's name, or the action's own title), then one
     `SettingsSection` of rows: icon badge, label, `RowDivider` between, no chevron. A
     destructive row (Delete) has a `danger` badge and label and comes last. No Cancel row:
     the sheet closes by dragging down (Esc / click outside on the desktop). Tapping a row
     closes the sheet first, then runs the action (so a follow-up sheet, such as a delete
     confirmation, opens after it).
   - **Picker sheet** (every dropdown: type, country, collection, phone, language, theme,
     auto-backup): a bottom sheet with the shared header (the field's label), then one card
     of rows: the option's label (`type.rowTitle`), at least `size.rowMinHeight` (60) tall,
     a `textPrimary` `check` (16) trailing on the current choice, a full-width 1pt `stroke`
     divider inset 14 between rows. Tapping a row picks it and closes the sheet.
   - **Long lists** (more than 8 options; countries) add a search field under the header:
     the plain 48-tall field with the `common.search` placeholder, matching any word of the
     label (and the dial code); with no match, `common.noMatches` in `textSecondary`. A long
     list opens at full height and scrolls; a short one sizes to fit.
   - **The row that opens a picker** keeps rule 4's look: label, chosen value, `RowChevron`.
6. **Rows.** `size.rowMinHeight` (60), icon badge 36 with radius 10, gap 14, title
   `type.rowTitle`, subtitle `type.rowSubtitle`, chevron 13. Rows that share a card (Settings,
   You → Tools, Account, About) are a `SettingsSection` with a `RowDivider` between each two:
   1pt `stroke`, inset badge + gap (50) on the leading side, running to the trailing edge.
   A page's header says what the page is, the same on every app: the Alerts tab is titled
   "Notifications" (`notifications.title`), whatever it shows inside.
6b. **Protected items** (a secure note with "Protect item" on; chosen 2026-11-12).
   - **Unlock first.** Opening one shows a plain cover (the `background` with the item's
     icon badge and `lock`, nothing of the item) and the unlock sheet on top. The item is
     never drawn behind it. Cancelling (dragging the sheet down, back, Esc) goes back to
     the list.
   - **The unlock sheet** is the app's own sheet: the shared header
     (`vault.unlockProtectedNote`), the `protected.title` message, the main password as a
     one-row group, then Unlock as the bottom button. With fingerprint / face unlock on, the
     biometric prompt starts by itself as the sheet opens and a secondary
     `auth.unlock.biometrics` button can start it again; the password always works.
     A wrong password is an error on its row (`auth.unlock.wrongPassword`).
   - **Every value is hidden.** Once open, each value in the item's details (title,
     content; not the yes/no "Protected" flag) shows as dots with the eye button (`show` /
     `hide`) trailing. The item's name in the hero stays, as it does in the list. The eye
     reveals that one value; copying (tapping the row, or the list's copy) works without
     revealing.
   - **Hidden again.** Revealed values hide when the user leaves the item, and the whole
     item locks again (cover and sheet) when the app goes to the background. Each opening
     asks again: an unlock is never remembered between openings.
6c. **Suggest a strong password at sign-up** (chosen 2026-11-12; audit in
   `docs/strong-password-signup.md`). Wherever Qasa sees a new-password field (a sign-up or
   change-password form), it offers "Use a strong password" (`fill.strongPassword`) with the
   generated value as the row's subtitle, from the shared generator and its default
   settings. Choosing it fills the new password and its confirm field. Saved logins are
   never offered on a new-password field. When the form is submitted Qasa offers to save the
   login (site, username, the generated password), even though Qasa filled it.
6d. **First launch: language, live tour, name** (direction B "Live demo", chosen 2026-11-12;
   preview in `design/previews/first-launch.html`). A fresh install shows, in order:
   - **Language only** (`onboarding.welcomeTitle`): English, العربية, کوردی and Get started.
     No theme picker; the app follows the system theme until it is changed in Settings.
   - **The tour**: four pages about free features (vault, autofill and strong passwords,
     two-step codes, password health), story bars and Skip at the top, Next (Continue on
     the last page) at the bottom. Each page plays a tiny version of the app: rows rise in
     on `motion.tourEnter` `tourStagger` apart; the sign-up demo types the email
     (`tourTypeStep` a character), shows "Use a strong password" and fills it; the code ring
     counts down and the code changes; the health gauge sweeps to 82 over `motion.gauge`. A
     page moves on by itself after `tourDwell` seconds; tapping the leading or trailing half
     goes back or forward, and so does a swipe. With reduce motion on, pages fade, the demos
     show their end state and nothing moves on by itself.
   - **Name** (`onboarding.nameTitle`): one optional field, "Let's get started" and Skip.
     The name is kept on the device only and greets the user ("Hi, %@" above the main
     password screen, the home greeting, the profile card); Settings can change it.
7. **Motion.** Use the named timings in `motion`: `press` 120 ms ease-out, `standard` 220 ms
   ease-out, `fast` 180 ms ease-in-out, springs `toggle` / `toast`. iOS ease-out is
   `cubic-bezier(0, 0, 0.58, 1)`; Android uses `QasaTokens.Easings`, never M3's default
   `FastOutSlowIn`. With reduce motion on, slides become fades (`motion.fade`).
8. **Icons: one library, Solar Linear** (chosen 2026-11-12), in `assets/icons`, generated
   into every app by `assets/icons/generate.py`: `QasaIconImage(.copy, size:)` on iOS,
   `QasaIcons.Copy` on Android, `<QasaIcon name="copy" :size>` on the desktop and in the
   extension. No app uses SF Symbols, Material icons or Lucide. Sizes stay what each place
   used before (16 in fields, badges and row buttons; 22 in the tab bar). A navigation tab's
   icon is the linear glyph, and the filled glyph (`.fill`, Solar Bold) while it is the
   active tab. To add an icon: name it in `icons.json`, run `import_solar.py`, then
   `generate.py`. Solar is CC BY 4.0: every app credits "Icons: Solar by 480 Design" on its
   licences screen (`assets/icons/LICENSE.md`).
   **One meaning, one icon, on every app**: personal info `identity` (as ID documents), Delete
   account `device-remove`, Auto-backup `backup-schedule`, Restore `history`, a row's options
   `more-vertical` (never `more` turned 90°), scan a QR from a picture `image`, the browser
   extension `extension` (a Tabler puzzle, like fingerprint), "Open Qasa" `open-app`.
9. **Color.** The brand palette is four colors: purple `#722F99`, grey `#C5C1C1`, beige
   `#FEE7C8`, peach `#FF9292`. `tokens.json` derives every light and dark token from them
   (table in `docs/design.md` §2.1); no app writes a hex of its own. The only exceptions are
   other brands' logo colors (site icons, payment wallets) and the six note colors, which
   are user data. Dark mode is a neutral ash grey (`background` `#1C1D1F`, cards `#26272A`,
   chosen 2026-11-12), clearly apart from the purple accent.
   **Item colours**: the six note colours a secure note or card can carry are tokens too
   (`noteMint` … `noteGray`, each with a `…Tile` fill), a light and a dark value each. The
   item's tile in the vault list and its detail hero show the glyph in the colour on its tile
   fill (the hero drops its stroke). In the picker, the selected swatch has a check in
   `background` and a 2pt `textPrimary` ring 2 outside it. On Android every Material color role is mapped to a token (`QasaTheme.kt`),
   `surfaceTint` included, so no Material default (lavender, tonal tint) shows through. Each
   app follows the system light/dark setting unless the user picks one in Settings, so two
   phones in different modes show the two halves of the palette.
10. **Language.** Every string comes from `l10n/strings.json` and follows the language picked
   in the app's settings, at once, on every screen, sheet, alert and toast, without a restart.
   Never the device's language when the user picked another one.
