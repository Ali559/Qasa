# Android parity capture notes

Captured on 2026-11-12 from branch `phase-7-wrap-up`, debug build (`./gradlew :app:installDebug`),
AVD `Pixel_10_Pro_XL` (1344 × 2992, Android 17). Status bar set with System UI demo mode (09:41,
full battery and signal, no notifications).

## Coverage

64 of 64 shots: all 16 screens × `en-light`, `en-dark`, `ar-light`, `ckb-dark`.

How they were taken:

- **01–04** were taken on a fresh install (`pm clear`) for each variant. The language and theme were
  written to `qasa.prefs` before launch, so the welcome screen opens with that language already
  ticked. The tour was skipped after page 2 and the name was entered as "Sam".
- **05–16** come from one vault, created in the app with a throwaway main password and fingerprint
  unlock turned off. It has four fake logins: Gmail and Facebook (`sara.ahmed@gmail.com`, sharing
  one weak password), Asiacell and FIB. They match the names in the iOS landing screenshots.
  Between variants only `qasa.language` and `qasa.theme` were changed, with the app stopped, so
  every variant shows the same data. Auto-lock was raised to 60 min to keep the vault open between
  screens, so Settings shows "Auto-lock 60 min" rather than the default 5.
- **Animations were off** (the emulator's animator scales set to 0). The app treats this as reduce
  motion, so `02-tour` shows the sign-up demo in its finished state without having to time it, and
  the tour does not move on to the next page by itself. The scales were set back to 1 afterwards.
- **There is no debug Premium override** (`PremiumUpgradeScreen` and `PaymentServices` only gate a
  stub webhook, and that needs the server). The account is on the free plan, so:
  - `11-alerts` shows the free Notifications screen: a "Find leaked passwords with Premium" upsell
    and "How the check works", with no list of breached logins. The iOS landing shot is the
    Premium version (breach summary and a Gmail row).
  - `12-health`: the "Breached" tile shows a lock instead of a number, and there is a Premium
    upsell card. The score (44) leaves out breaches.
  - `06-home` shows "VPN · Included with Premium" and a "Find leaked passwords with Premium" card.
- `06-home` says "Good afternoon". The greeting follows the real time of day; demo mode only
  changes the status bar clock.
- `15-collections` is the empty state, because no collection was created.
- The `16-add-menu` and `09-editor` sheets are open over the Passwords tab.

## Fix round 1 (re-taken 2026-11-12)

After the coordinator's fixes, I reinstalled the debug build over the existing app with
`./gradlew :app:installDebug` (data kept) and re-took these 14 shots, replacing the old files:

- `12-health`: en-light, en-dark, ar-light, ckb-dark
- `06-home`: ar-light, ckb-dark
- `14-settings`: ckb-dark
- `10-generator`: ar-light, ckb-dark
- `01-welcome` and `02-tour`: ar-light, ckb-dark (fresh install, as before)

| Fix | Result |
| --- | --- |
| Health gauge | **Fixed.** The arc is red → gold from the left in all four variants, and in RTL the gauge is no longer mirrored. |
| Western digits | **Fixed.** In ar and ckb, the score, "out of 100", the stat tiles, the Home "3 to fix" badge and its counts, Auto-lock "60" and "0 Active" are all Latin digits now. |
| Generator ranges in RTL | **Partly fixed.** "(A-Z)", "(a-z)" and "(0-9)" now read correctly. "Symbols (!@#$)" still displays as "($#@!)" in ar and ckb: `gen_symbols` in `values-ar` and `values-ckb` has no LRI/PDI isolates around `!@#$`, unlike the other three strings. |
| Welcome language rows | **Fixed.** Every row follows the app's direction (in ar/ckb the English row is now right to left too, with its icon on the right). |
| Tour card digits in RTL | **Fixed.** On tour page 1 in ckb, the Visa row reads "•••• 4821". Page 1 is not a parity shot; I checked it on screen. |
| Keyboard after saving a new login | **Fixed.** I saved a test login in ckb-dark: the sheet closed, the keyboard went down, and the search field was not focused. The vault was wiped by the fresh installs straight afterwards. |

Notes on the re-taken shots:

- **Status bar differs slightly.** On this boot the demo-mode mobile icon drew as a white "3G"
  that was invisible on light backgrounds and would not go away, so I hid the mobile signal. The 14
  re-taken shots show only Wi-Fi and battery. The other 50 also show cell bars.
- **Unlock button sometimes needs a second tap (not confirmed).** On ar-light and ckb-dark, after
  typing the main password with the keyboard up, the Unlock button kept its disabled colour and the
  first tap did nothing; a second tap unlocked. Seen twice, but the emulator was very slow, so it
  may be input lag. Worth checking on a device.

## Findings (Android against the iOS reference)

### Likely bugs

1. **[FIXED in round 1]** **Password health gauge has the wrong colours** (`theme/QasaHeroWidgets.kt`, `Brush.sweepGradient`).
   Compose sweep gradients start at 3 o'clock (0°), not 9 o'clock. With stops
   danger 0 / gold .25 / accent .5 / accent .75 / danger 1, the arc from 180° to 360° is accent
   purple from the left edge to the top, then turns red towards the right. A low score therefore
   draws a purple arc. iOS draws red → gold → purple from the left (`PasswordHealthView.swift`
   `AngularGradient` 180°→360°). You can see it in every `12-health-*` shot (score 44: the arc is
   purple, not red-orange). The code comment saying 180° "matches the arc origin" is wrong.
2. **[FIXED in round 1]** **Mixed digits in Kurdish on the health screen.** The score is drawn with `shownScore.toString()`,
   so it is always Latin ("44"). The stat tiles use `NumberFormat.getIntegerInstance()`, so in ckb
   they are Arabic-Indic ("٤", "٢", "٣"), and so is "out of ١٠٠". One screen mixes two digit sets
   (`12-health-ckb-dark`). In `ar` everything came out Latin. The iOS landing shot
   `health-ckb-dark` has the reverse mix (score ٣٩, tiles 2 / 8 / 1), so both apps need one rule.
3. **[FIXED in round 1]** **The language list mirrors rows by script, not by app direction** (`01-welcome-*`). In English,
   the کوردی and العربية rows have their icon on the right and their text right-aligned, while
   English has its icon on the left. In ar/ckb, the English row is laid out left to right. The
   comment in `WelcomeFlowScreen.LanguageRow` says "Every row follows the app's direction so the
   icons line up", so this contradicts the intent. The tick then sits on the far left of the RTL
   rows in an English UI. iOS lays every row out in the app's direction.
4. **[FIXED in round 1]** **The keyboard stays open after saving a new login.** After Save in the New Login sheet, the
   sheet closes but the IME stays up over the Passwords list. Once the keyboard is closed, the
   search field is left focused (caret in "Search your passwords"). Seen while seeding the vault;
   not in the shots.
5. **Typing duplicated characters while the emulator lagged** in the name field and the login name
   field ("Sam" became "SSaamv", "Gmail" became "GGmail") when the IME was still attaching. It is
   probably emulator or IME lag, not the app, but worth checking on a device.

### Visual differences / worth a look

6. **Language row icon.** Android's `QasaIcons.Language` draws as a radio-wave "(•)" glyph. Check
   that it matches the iOS `.language` icon (it does not read as a globe or "language").
7. **The tour's sign-up card leaves an empty band** under the password field once the demo is done
   (`02-tour-*`). On iOS a "Strong password" suggestion row lives there during the demo; after it
   ends, Android keeps the space.
8. **Create screen** (`04-create-*`): the "I already have Qasa on another device" and "Restore from
   a backup" links are indented about 12 dp more than the headings in LTR. The decorative shield in
   the "Only you can see your passwords" card sits under the end of the body text, which overlaps
   it ("password." and "recovered." run across the shield).
9. **The splash ignores the in-app dark theme.** With the theme set to Dark and the device in light
   mode, the cold-start splash is the light cream screen before the dark UI appears. Not visible in
   the final shots, but seen on every dark launch.
10. **The "reused" segment of the Home health bar is too pale in dark mode** (`06-home-en-dark`): it
    shows near-white cream rather than gold.
11. **"Linked Devices · 0 Active"** in Settings while "sdk_gphone16k_arm64 (This Device)" is listed
    under it (`14-settings-*`). Check whether iOS counts this device.
12. **Folders and collections.** The screen is titled "Folders" (and the editor says "Folder"), but
    the empty state says "No Collections" and "Tap + to create your first collection", and the
    editor's add button is "New Collection". The same strings are in iOS `Localizable.xcstrings`,
    so this is a shared copy issue, not Android only.
13. **[FIXED in round 1]** **Masked card number in RTL.** On the ar/ckb tour page 1, the Visa row reads "4821 ••••"
    instead of "•••• 4821" (bidi reordering of the mask).
14. **Generator** (`10-generator-*`): **[Round 1: the A-Z, a-z and 0-9 ranges are fixed; "Symbols (!@#$)" still shows as "($#@!)" in RTL.]** "Copy Password" is a muted pink/plum tinted button, and the
    length slider has a separate vertical thumb bar next to the filled track. Compare with iOS
    `PasswordGeneratorModal`.
15. **Passwords list against the iOS landing shot** (`07-passwords-*` vs `passwords-*-light`): the
    section letters (A, F, G) are smaller and tighter on Android, and rows are shorter. The list
    has no "Suggested for Checkout" section because this vault has no card. A new row first shows
    a coloured letter tile and later switches to a site icon (Gmail went from a red "G" to a
    favicon-style "G" with a copy button).
16. **Home "Add a password"** in the empty state switches to the Passwords tab instead of opening
    the add menu or editor. iOS does the same (`onAddFirst: { onTab(.passwords) }`), so it matches,
    but it is an extra tap.

## Not captured / blocked

Nothing is blocked; all 64 files exist. The limits are the Premium-only content above (alerts and
breach data) and the empty collections screen.
