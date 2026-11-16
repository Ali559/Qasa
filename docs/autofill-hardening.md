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
6. One password field, not marked: new only when the same form also asks for a given or
   family name, a phone number, or a date of birth, or its submit/heading text says sign
   up / register / create / join (Arabic تسجيل / إنشاء حساب; Kurdish تۆمار / دروستکردن /
   خۆتۆمارکردن). Otherwise it is a login password.

A new field never gets saved logins; it gets "Use a strong password" only.

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

### R5. A fill that can't happen says so

The extension's menu stays open with "Couldn't fill. Click the field and try again." It
never closes as if it worked. Android and iOS hand values to the system, which reports
nothing back; they follow R1–R3.

### R6. Fill like a person typed it

The extension sets the value with the native setter and fires `keydown`, `beforeinput`,
`input` (`InputEvent`, `insertReplacementText`), `keyup`, `change` and `blur` in that order.
It re-finds the field at pick time (same form, same name/id/position) in case the page
replaced it.

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

### Platforms

| Platform | Versions we support | Browsers / apps | How it's tested |
| --- | --- | --- | --- |
| Android | 8.0 (API 26) – 17 (API 37) | Chrome (native third-party mode), Samsung Internet, Firefox, Edge, Brave; native apps | JVM tests per form shape; emulator Chrome for A and B |
| iOS | 17 – 27 | Safari, apps | Swift tests per rule; 26.2 generate/save and 18 text insertion need a device |
| Extension | Chrome / Edge 116+, Firefox 128+ | — | Vitest (detection, rules) + Playwright per form A–H |
| Desktop | macOS 13+, Windows 10+ | via the extension | Rust tests for `generate` with constraints |

### Results (2026-11-16)

| Form | Android | iOS | Extension |
| --- | --- | --- | --- |
| A | ✅ (emulator Chrome) | ✅ unit | ✅ e2e |
| B | ❌ → ✅ (emulator Chrome, `android-form-b-fixed.png`) | ✅ unit | ✅ e2e |
| C, D, E, F, G | ❌ → ✅ unit | ✅ unit | ❌ → ✅ e2e |
| H | ✅ unit | — | ✅ e2e |

iOS 26.2 generate/save, iOS 18 text insertion (same password), the save-on-unlock prompts
and Face ID inside the iOS extension still need a device.

The per-form results live in the tests: `AutofillSignupFormsTest` (Android),
`SignupFormsTests` (iOS), `signup-forms.spec.ts` (extension).
