# iOS parity shots: notes

Taken on an iPhone simulator (1206 × 2622 capture, status bar at 9:41) with a DEBUG build,
`-screenshotVault -screenshotPremium`, saved as 500 px wide WebP at quality 80.

## Coverage

All 64 shots were taken (16 screens × `en-light`, `en-dark`, `ar-light`, `ckb-dark`). Nothing was skipped.

- `01`–`04` in `en-dark`, `ar-light` and `ckb-dark` come from a fresh first launch (`-wipeLocalVault`) in that language and theme.
- `02-tour` is a frame from a screenshot burst: page 2 ("Qasa fills it in") after the demo has typed the email and filled the password. The tour advances by itself, so this was the only reliable way to catch it.
- `08-detail` is Gmail. `09-editor` is "Password for a site or app" from the add menu. `14-settings` is the top of Settings. `15-collections` is "Folders" (You → Folders).

## Things that looked wrong

Re-checked on a rebuilt app: findings 2, 3, 4, 5 and 8 are now fixed. 1, 6, 7, 9, 10, 11, 12 and 13 are unchanged where they were visible in the re-taken shots (1 is still visible in `11-alerts-*`).

1. **Alerts title vs tab label.** The Alerts tab opens a page titled "Notifications" (ar: الإشعارات) while the tab says "Alerts" (ar: التنبيهات). In ckb both say ئاگادارییەکان. The landing page used to show "Breached passwords" here.
2. **Fixed:** generator ranges now read (A-Z), (a-z), (0-9) in ar and ckb (`10-generator-ar-light`, `10-generator-ckb-dark` re-taken). Was: **Generator range labels flip in RTL.** ar shows "أحرف كبيرة (Z-A)" and "أرقام (9-0)". ckb shows "(9-0)" for numbers. The ranges need LTR isolation.
3. **Fixed:** the gauge is red to gold in ar and ckb, matching LTR (`12-health-ar-light`, `12-health-ckb-dark` re-taken). The arc now fills from the left in RTL too, the same as LTR. Was: **Health gauge colour in RTL.** In LTR the arc runs red to orange. In ar and ckb (both themes) it's a purple gradient. The arc mirrors, but its gradient doesn't match.
4. **Fixed:** ckb now uses Western digits everywhere I re-took (Alerts 119,167 and "1", Health 39 / 100 and the stat cards, Settings, generator length 16). `15-collections-ckb-dark` was not re-taken and still shows the old ١ / ٦. Was: **Mixed numerals in ckb.** Health shows the score as ٣٩ and "له ١٠٠", but the four stat cards use 8 / 2 / 2 / 1. Settings shows "5 خولەک" and "0 چالاک" in Western digits, while Alerts (١١٩,١٦٧) and Folders (١ بڕگە) use Eastern Arabic digits. ar uses Western digits throughout.
5. **Fixed:** the ckb autofill hint now uses "←" (`14-settings-ckb-dark` re-taken). It still wraps onto two lines, with "AutoFill Passwords" on the second line. Was: **Autofill hint arrow in ckb Settings.** ckb shows "Passwords → AutoFill Passwords" (wrapped over two lines, arrow pointing the LTR way). ar shows "←".
6. **Editor placeholders differ between RTL languages.** In ar, the Email and Website placeholders (name@example.com, example.com) are left-aligned. In ckb they're right-aligned.
7. **Unlock password dots in RTL.** After pasting, the dots sit in the middle of the field instead of against either edge (ar and ckb).
8. **Fixed:** in ar and ckb, all three language rows now follow the app's RTL direction, with icons and dividers lined up (`01-welcome-ar-light`, `01-welcome-ckb-dark` re-taken). Was: **Welcome language list.** Each row lays itself out in its own direction (English LTR, کوردی and العربية RTL), so the icons sit on opposite sides and the dividers are inset from one side only. It looks uneven in every language.
9. **Latin "?" in Arabic script.** ar name screen "بماذا نناديك?", ckb name screen "چیت پێ بڵێین?", ckb add menu "دەتەوێت چی پاشەکەوت بکەیت?" (ar add menu correctly uses "؟").
10. **Detail section headers are all caps.** In en, "DETAILS" and "ORGANIZATION" are in capitals. Every other screen uses sentence case ("Security", "Tools").
11. **New Login sheet has no Cancel/close button.** You can only dismiss it by swiping down.
12. **Dark background colour changed.** Dark mode is now a neutral grey (about rgb 36, 37, 41). The previous landing shots were a purple-tinted near-black (about rgb 22, 13, 30). If that's intended, ignore this.
13. **Create screen (ckb-dark, ar-light).** The faint shield watermark in the "Only you can see your passwords" card sits behind the title text on the leading side.
14. **System edit menu in ckb.** iOS has no Sorani UI. Before the locale change the Paste / AutoFill menu appeared in Arabic; with `ckb@numbers=latn` it appeared in English in ckb-light and still in Arabic in ckb-dark. Either way it's an iOS limitation, not an app bug.
