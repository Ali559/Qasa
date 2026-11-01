# Shared mobile localization

Source of truth for Qasa iOS + Android UI copy (`en` / `ar` / `ckb`).

## Edit workflow

1. Edit [`strings.json`](strings.json) (shared product UI) or [`platform/`](platform/) overlays.
2. Regenerate committed outputs:

```bash
python3 l10n/generate.py
```

3. Do **not** hand-edit generated files:
   - `qasa/qasa_ios/Resources/Localizable.xcstrings`
   - `qasa/QasaCredentialProvider/Localizable.xcstrings`
   - `qasa/QasaPaymentNotification/Localizable.xcstrings`
   - `qasa_android/app/src/main/res/values*/strings.xml`

## Keys

- Shared keys use dotted camelCase: `auth.create.button`, `settings.autoLockMinutesFmt`.
- Android resource names are derived: `auth.create.button` → `auth_create_button`. When the Android code uses a name that cannot be derived, give the row an explicit one: `"home.allgoodBody": { "android": "home_allgood_body", "en": …, "ar": …, "ckb": … }`.
- Platform-only keys live in `platform/ios.json` / `platform/android.json` (same `{ en, ar, ckb }` shape). Android overlay keys may already be snake_case (`app_name`).

## Placeholders

Store **iOS-style** format tokens in JSON (`%@`, `%lld`, `%d`, `%1$@`, …).  
The generator maps them for Android (`%@` → `%s`, `%lld`/`%ld` → `%d`).

## Drift check

```bash
python3 l10n/generate.py --check
```

Exits non-zero if generated outputs would change.

## Re-import from iOS catalog

Only if you need to re-seed from an existing `.xcstrings` (overwrites `strings.json`):

```bash
python3 l10n/extract_ios.py
```
