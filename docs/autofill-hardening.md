# Autofill hardening (TODO Phase 8, 2026-11-16)

Reported: "the suggested strong password didn't fill". No platform was named. This file is
what we found, the rules every platform now follows, and the test matrix.

## What we found

Android (reproduced in Chrome 157 on the emulator, `docs/autofill-test-pages/signup.html`):
form A (both fields `autocomplete="new-password"`) fills new and confirm. Form B (confirm
named `password_repeat`, no hint) shows "Use a strong password" but the confirm field is
classified as a current password, so the pick fills only the first field and the site says
the passwords don't match; the confirm field even offers saved logins.

The same class of bug exists on every platform:

| Platform | Why a picked strong password "doesn't fill" |
| --- | --- |
| Android | Confirm fields named `repeat`, `again`, `verify`, `pw2`, Kurdish/Arabic "repeat" are read as current passwords: only one field fills. A sign-up form with one plain password is read as a login: no suggestion. `maxlength` is ignored. |
| Extension | A failed fill closes the menu silently. The field captured when the menu opened can be replaced by a re-render (React/Vue) before the pick, so the fill aborts. A change form's confirm without `new-password` is never filled. One plain password on a sign-up page is read as a login. `maxlength`/`passwordrules` are ignored. |
| iOS 18–26.1 | "AutoFill → Qasa → Use a strong password" makes a **new** password on every use, so the confirm field gets a different one. The password is never saved anywhere. |
| iOS 26.2+ | `max-consecutive`, empty `passwordrules` and `maxlength` below the class count aren't honoured; a filled-but-unsubmitted password is dropped. |

## Rules (all platforms)

### R1. Which password fields are new

In one form (or one screen on Android), with the password-type fields in document order:

1. A field marked `current-password` (hint, or words: current, old, existing; Arabic
   الحالية / القديمة; Kurdish ئێستا / کۆن) is the current password.
2. A field marked `new-password` (hint, or words: new, create, choose, set, confirm,
   repeat, again, retype, re-enter, verify, `2` suffix like `pw2` / `password2`; Arabic
   جديدة / تأكيد / إعادة / أعد; Kurdish نوێ / دووبارە / دڵنیاکردنەوە / پشتڕاستکردنەوە) is new.
3. The password field right **after** a new one, if not current, is its confirm: new.
4. Two password fields, neither marked current: both new (sign-up: new + confirm).
5. Three password fields, none marked: current, new, new.
6. One password field, not marked: new when the same form (one screen on Android) also
   - asks for a given, family or **full** name, a phone number, or a date of birth; or
   - has **both** a username field and an email field (Phase 12: a login asks for one of
     them, a sign-up often for both); or
   - has a submit button or heading whose text says sign up / register / create account /
     join (Arabic إنشاء حساب / حساب جديد / التسجيل; Kurdish تۆمارکردن / خۆتۆمارکردن /
     دروستکردنی هەژمار). The bare Arabic تسجيل does **not** count: تسجيل الدخول is
     "log in". "Continue", "Next" and "Submit" count for nothing either way.
     A login button on the same screen (log in / sign in / تسجيل الدخول / دخول /
     چوونەژوورەوە) wins over sign-up buttons: a login screen's "Don't have an account?
     Sign up" link must not take its saved logins away. Chrome on Android reports no
     `<button>` nodes, so there a page's button text counts for nothing (R7 still offers
     the strong password).
   Otherwise it is a login password. Where the button text comes from: the extension reads
   the page; Android reads the text of native `Button` nodes in the structure (a Flutter or
   React Native app reports none, see below); iOS gets no form at all.

A new field never gets saved logins; it gets "Use a strong password" only.

**Detecting a password field** never depends on the input type alone. A Flutter app reports
a field's `autofillHints` and placeholder (`hintText`) but no input type, id or label, and
outside an `AutofillGroup` it reports **only the focused field**
(`flutter/engine … TextInputPlugin.java`, `AutofillGroup` docs). The placeholder words
(password, کلمة المرور, وشەی نهێنی …) are enough to make a field a password.

### R2. The generated password fits the field

- Length: default 16, clamped to the smallest `maxlength` of the new fields when that is
  8 or more (below 8: use `maxlength` anyway if ≥ 4 and digits only if the field is
  numeric); raised to `minlength` when that is larger.
- `passwordrules` (when the platform reports it): `required`, `allowed`, `minlength`,
  `maxlength`, `max-consecutive`. Empty rules are no rules.
- With `max-consecutive: n`, no character repeats more than `n` times in a row.
- Symbols only from the generator's set, and only when allowed.

### R3. One pick fills every new field with the same value

New and confirm get the same value in one action. When a platform can only fill the
focused field (iOS text insertion), it keeps the last generated password for **5 minutes**
and offers "Use the same strong password" first, so the confirm field gets the same one.

### R4. A generated password is never lost

Whatever fills a generated password records it (site, time, the value; encrypted, local,
deleted after 24 h or once saved). If the form's own save prompt doesn't happen, the app
offers it on the next unlock: "Save the password you created for <site>?".

**Two-screen sign-ups and logins (Android 12+, apps only, not browsers).** A screen with a
username or email but no password sets its save info with `FLAG_DELAY_SAVE` and keeps that
field's id in the response's client state. The next screen's request, from the same app,
reads it back and saves the username and the password as one login. (As Proton Pass does.)

### R5. A fill that can't happen says so

The extension's menu stays open with "Couldn't fill. Click the field and try again." It
never closes as if it worked. Android and iOS hand values to the system, which reports
nothing back; they follow R1–R3.

### R6. Fill like a person typed it

The extension sets the value with the native setter and fires `keydown`, `beforeinput`,
`input` (`InputEvent`, `insertReplacementText`), `keyup`, `change` and `blur` in that order.
It re-finds the field at pick time (same form, same name/id/position) in case the page
replaced it.

### R7. A password field never shows nothing (Phase 12, 2026-11-18)

When R1 can't call a password field new (it may still be a sign-up nobody could tell):

| Saved logins match this app / site | What is offered, in order |
| --- | --- |
| None | "Use a strong password", then "Search Qasa" |
| Some | the matching logins, then "Use a strong password", then "Search Qasa" |

A field R1 calls **new** keeps "Use a strong password" only (plus "Search Qasa" where the
platform always shows it). On iOS the system builds the list: before 26.2 the text-insertion
sheet already shows "Use a strong password" first; from 26.2 iOS itself asks Qasa for a
password on the fields it thinks are new.

### R8. Finish a fill the app split in two (Android, Phase 12)

An app that reports one field at a time (a Flutter or React Native app without an autofill
group) gets only the username filled by the first pick; its password field then asks again.
After a pick, Qasa remembers **which login** it was (its id, never its values) for the app's
package or the browser's site, **in memory, for 2 minutes**, cleared when the vault locks.
A request from the same app or site within that time for a field that pick didn't fill puts
that login **first**, labelled with its name, and fills it **without asking to unlock again**
(if the vault is still unlocked; with "Confirm every fill" on in Settings, it still asks).
Android can't fill a field the app didn't report, so this is one tap, never zero. No other
manager we looked at does this (Bitwarden, KeePassDX, Keyguard, Proton Pass, 2026-11-18).

## Test matrix

Status: ✅ passed, ❌ failed before the fix, — not applicable, ◻ not run (needs a device).

### Forms (`docs/autofill-test-pages/signup.html`)

| Form | Shape |
| --- | --- |
| A | `new-password` on both fields |
| B | `new-password`, confirm without a hint (`password_repeat`) |
| C | two plain password fields (`pw1`, `pw2`) |
| D | one plain password with first/last name (sign-up) |
| E | change: current, new, confirm without a hint |
| F | `maxlength=12` |
| G | Kurdish labels only |
| H | controlled inputs (React-style) |
| I | full name, username, email, one password, button "Continue" (the Dozarawa shape) |
| J | email, one password, button "Create account" |
| K | Arabic login: email, password, button "تسجيل الدخول" (must stay a login) |
| L | one password field reported alone, placeholder "Password", no input type (Flutter) |

### Platforms

| Platform | Versions we support | Browsers / apps | How it's tested |
| --- | --- | --- | --- |
| Android | 8.0 (API 26) – 17 (API 37) | Chrome (native third-party mode), Samsung Internet, Firefox, Edge, Brave; native apps | JVM tests per form shape; emulator Chrome for A and B |
| iOS | 17 – 27 | Safari, apps | Swift tests per rule; 26.2 generate/save and 18 text insertion need a device |
| Extension | Chrome / Edge 116+, Firefox 128+ | — | Vitest (detection, rules) + Playwright per form A–K |
| Desktop | macOS 13+, Windows 10+ | via the extension | Rust tests for `generate` with constraints |

### Results (2026-11-16, Phase 12 rows 2026-11-18)

| Form | Android | iOS | Extension |
| --- | --- | --- | --- |
| A | ✅ (emulator Chrome) | ✅ unit | ✅ e2e |
| B | ❌ → ✅ (emulator Chrome, `android-form-b-fixed.png`) | ✅ unit | ✅ e2e |
| C, D, E, F, G | ❌ → ✅ unit | ✅ unit | ❌ → ✅ e2e |
| H | ✅ unit | — | ✅ e2e |
| I | ❌ → ✅ unit; Flutter-shaped app on the emulator (R7, `android-flutter-split-fill.png`) | — | ❌ → ✅ e2e |
| J | ❌ → ✅ unit (native app); in Chrome a login + R7 (no button nodes) | — | ❌ → ✅ e2e |
| K | ✅ unit (stays a login) | — | ✅ e2e |
| L | ❌ → ✅ unit; Flutter app on the emulator | — | — |
| R8 split sign-in | ❌ → ✅ Flutter app on the emulator | — | — |

iOS 26.2 generate/save, iOS 18 text insertion (same password), the save-on-unlock prompts
and Face ID inside the iOS extension still need a device. The Android two-screen save (R4)
and native-app button text are unit tested only; `docs/autofill-test-pages/flutter-app`
holds the Flutter test app.

The per-form results live in the tests: `AutofillSignupFormsTest` (Android),
`SignupFormsTests` (iOS), `signup-forms.spec.ts` (extension).
