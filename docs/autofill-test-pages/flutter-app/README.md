# Flutter autofill test app (Phase 12)

`main.dart` is a one-file Flutter app shaped like the Dozarawa app: a sign-up tab (full
name, username, email, password, "Continue") and a sign-in tab (username, password). The
fields are **not** in an `AutofillGroup` and have no `autofillHints`, so Android reports
only the focused field to the autofill service, as such apps do.

```bash
flutter create --org test.qasa --project-name flutter_af --platforms android flutter_af
cp main.dart flutter_af/lib/main.dart
cd flutter_af && flutter build apk --debug
adb install -r build/app/outputs/flutter-apk/app-debug.apk
```

`../android-flutter-split-fill.png` (2026-11-18, emulator): the sign-up password offers
"Use a strong password" (R7); on sign-in the first pick fills only the username, and the
password field then offers that login first and fills it in one tap (R8).
