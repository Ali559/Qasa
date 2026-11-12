# Audit findings (2026-11-12)

The 3 × 64 shots in `ios/`, `android/` and `desktop/`, compared screen by screen. Each app's
own `NOTES.md` has the capture details and the smaller observations. iOS is the reference.

How they were taken:
- **iOS:** the screenshot vault with `-screenshotPremium`.
- **Android:** a hand-made free vault with four logins, so its Alerts shows the Premium card and its Home shows the upsell cards.
- **Desktop:** the browser mock in Premium, with the iOS health numbers.

So data and plan differ between apps; compare layout, type and colour, not content.

## Fixed in this pass

| Finding | Apps | Fix |
| --- | --- | --- |
| Kurdish mixed Eastern (٣٩, ١٠٠) and Western digits; Arabic strings had a few Eastern ones too | all | Every language writes 0-9. iOS `ckb@numbers=latn` / `ar@numbers=latn`, Android `-u-nu-latn`, desktop `formatLocale()`. 19 strings converted. `l10n/generate.py` now refuses Eastern digits. |
| Phone and card numbers reversed in RTL lists (`1290 555 0770`) | desktop | Row subtitle in `<bdi>` |
| Tour card digits reversed in RTL (`4821 ••••`) | Android | Subtitle `TextDirection.ContentOrLtr` |
| Generator ranges read `(Z-A)`, `(9-0)`, `($#@!)` in ar/ckb | all | Ranges isolated in the strings (U+2066…U+2069). The symbols label was fixed after the re-takes, so the 10-generator ar/ckb shots still show it reversed |
| ckb autofill hint arrow pointed → in RTL | all | `←`, like ar |
| Welcome language rows each laid out in their own direction (icons zig-zag) | iOS, Android | Rows follow the app direction; each name keeps its script (as the desktop already did) |
| Health gauge purple instead of red → gold | iOS in RTL; Android always | iOS pins the dial left-to-right; Android's sweep gradient now starts where the arc does and turns around the arc's centre |
| Keyboard stayed up after saving a new login | Android | Editor clears focus on save |

## To decide (owner)

1. **Section labels:** iOS and the desktop write "DETAILS", "ORGANIZATION" in caps on item detail; Android writes "Details". Every other screen uses sentence case. Suggest sentence case everywhere.
2. **Linked devices count:** iOS and Android say "0 Active" while listing this device; the desktop says "1 Active". Suggest counting this device.
3. **Home card actions:** iOS and the desktop show "Set up backups" as a filled chip; Android shows a plain text link.
4. **Generator card:** Android tints the card warm and the Copy button purple; iOS and the desktop use a white card with an outlined Copy button. Android also has an extra disabled "Pronounceable" row.
5. **Tab bar:** iOS uses a floating pill tab bar, Android a flat bottom bar. That's probably a platform convention; keep or match.
6. **Language icon:** the `language` glyph reads as a radio or broadcast symbol on all three. Swap it for a globe in `assets/icons/icons.json`.
7. **Tour sign-up demo:** once the suggestion fades out it leaves an empty band in the card, on all three. Either keep the suggestion showing in a "used" state, or collapse it.
8. **Alerts tab vs title:** the tab says "Alerts" (ar التنبيهات) but the screen's title says
   "Notifications" (ar الإشعارات), on iOS and the desktop. Suggest one word for both.
9. **Health gauge in RTL:** it now fills from the left in Arabic and Kurdish too, like a dial
   (iOS and Android). Keep it, or mirror the whole gauge so it fills from the right.
10. **Add button:** the desktop uses a circled plus in a smaller round button; iOS uses a plain bold "+".
9. **Section 3's known platform differences, still open:**
   - iOS system Switch, Stepper and menu Picker vs Android M3 Switch, a hand-made stepper and a DropdownMenu. The shots show Android's M3 slider with its stop dot next to iOS's thumb.
   - iOS shrinks long button labels to 0.8; Android doesn't.
   - Toast offset: 88 on Android vs 76 on iOS.
   - The desktop gauge eases its count up in JS.

## Left as is (noted)

- **iOS New Login sheet** has no Cancel; it closes by dragging down. That's by design (rule 6a, the app's own sheets).
- **Android launch splash** stays light when the app is set to dark, before the app's theme loads.
- **Android Home in dark mode:** the "reused" segment of the health bar is near white.
- **Create screen in RTL:** the shield watermark sits behind the card title on iOS and under the warning text on Android.
- **iOS unlock in RTL:** pasted password dots sit mid-field.
- **ar/ckb editor placeholders:** on iOS, email and website placeholders align left in ar and right in ckb.
- **Android emulator typing:** text doubled ("Sam" → "SSaamv") under load. Probably the emulator; check on a device.
- **Desktop:** one 404 in the console on each load, not yet traced.
