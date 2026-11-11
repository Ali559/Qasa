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
4. **Bottom sheets** (every platform, every sheet):
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
   - Content sized to fit, never taller than 92% of the window.
5. **Rows.** `size.rowMinHeight` (60), icon badge 36 with radius 10, gap 14, title
   `type.rowTitle`, subtitle `type.rowSubtitle`, chevron 13.
6. **Motion.** Use the named timings in `motion`: `press` 120 ms ease-out, `standard` 220 ms
   ease-out, `fast` 180 ms ease-in-out, springs `toggle` / `toast`. iOS ease-out is
   `cubic-bezier(0, 0, 0.58, 1)`; Android uses `QasaTokens.Easings`, never M3's default
   `FastOutSlowIn`. With reduce motion on, slides become fades (`motion.fade`).
7. **Language.** Every string comes from `l10n/strings.json` and follows the language picked
   in the app's settings, at once, on every screen, sheet, alert and toast, without a restart.
   Never the device's language when the user picked another one.
