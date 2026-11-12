# Suggest a strong password at sign-up

TODO Phase 5 (2026-11-12). The goal: signing up on a site or in an app offers a strong
password in one or two taps and ends with the login saved. The rule is
`design/README.md` 6c. One PR per platform.

## Audit: what the user sees today

### Browser extension (and so the desktop)

- **Detection works.** `content/detect.ts` tells sign-up (`kind: 'signup'`, a
  `new-password` field or two password fields) and change-password forms (`kind: 'change'`,
  a current and a new password) from logins. It keeps one `newPassword` field per group; a
  second "confirm" field is not tracked.
- **Nothing is offered on a sign-up form.** `iconFields` skips `signup` groups, so the
  password field gets no Qasa icon and no in-page menu. On a change-password form the icon
  sits on the current-password field and offers saved logins only. The popup's Fill button
  skips sign-up forms too.
- **Saving works.** Submitting a sign-up form sends `save` with the username and the new
  password (`content/save.ts`); the desktop asks the user and saves a new login. A value
  Qasa filled itself is never offered for saving again (`wasFilledWith`), which would also
  swallow a password Qasa generated.
- **The generator exists, popup only.** The desktop's `generate` op (the shared Rust
  generator, `qasa_vault::generator`) answers even while the vault is locked, but the
  service worker accepts `generate` only from the popup (`POPUP_ONLY`).

### Android

- **Detection works.** `credentials/AutofillFields.kt` maps `newPassword` /
  `new-password` hints and names like `confirmpassword`, `createpassword` to
  `AutofillFieldType.NewPassword`, in the login group.
- **What a sign-up form offers is wrong.** `QasaAutofillService.onFillRequest` treats a
  new-password field as a login field: it offers up to five saved logins (filling an old
  password into a new-password field) and "Search Qasa". There is no generated password.
- **Saving works.** `SaveInfo` is set on the password (or new-password) field, and
  `onSaveRequest` opens the app's new-login form pre-filled for review.

### iOS

- **What iOS offers.** On a sign-up field Apple's own Passwords app suggests a strong
  password; a third-party provider gets nothing there before iOS 26.2. Qasa's provider has
  password AutoFill (`ProvidesPasswords`), one-time codes and, since iOS 18, text insertion
  (`ProvidesTextToInsert`: "AutoFill → Qasa" in any field, `TextInsertionView`), which
  lists saved items only, no generator.
- **iOS 26.2 adds what we need** (AuthenticationServices, iOS 26.2 SDK headers):
  - `SupportsGeneratePasswordCredentials` (and `…WithUI`, on by default) in
    `ASCredentialProviderExtensionCapabilities`; the system then asks the provider for
    passwords on a new-password field: `performWithoutUserInteraction(generatePasswordsRequest:)`
    (no UI) or `prepareInterface(for: ASGeneratePasswordsRequest)` (UI, only when the user
    starts it), answered with `completeGeneratePasswordRequest(results:)` and
    `ASGeneratedPassword(kind: .strong, value:)`. The request carries the page's
    `passwordrules` and the confirm field's.
  - `SupportsSavePasswordCredentials`: `performWithoutUserInteractionIfPossible(savePasswordRequest:)`
    / `prepareInterface(for: ASSavePasswordRequest)`, answered with
    `completeSavePasswordRequest()`. Events: `userInitiated`, `formDidDisappear`,
    `generatedPasswordFilled` (a generated password went into a form not yet submitted).
- **Limits Apple imposes.** iOS 17–26.1: no third-party password generation or save
  prompt at all; the best Qasa can do there is a "Generate password" choice in text
  insertion (iOS 18+), which the user has to open from the field's AutoFill menu. The
  deployment target stays iOS 17, so the 26.2 path is `@available(iOS 26.2, *)`.

### Desktop

- Covered through the extension. A login saved from the extension goes through the
  desktop's save confirmation (`bridge::ops::plan_save`) into the vault, and accepting it
  reloads the open vault list at once (`BridgePrompts.vue` → `loadVault()`); a locked vault
  loads it on unlock.

## Plan, one PR per platform

1. **Extension** (done, branch `phase-5-extension`): an in-page menu on the new-password field of a sign-up or
   change-password form with "Use a strong password" (a preview of the generated password);
   picking it fills the new and confirm fields; submitting saves the login even though
   Qasa filled the password. Check that the desktop list shows the new login at once.
2. **Android** (done, branch `phase-5-android`): a new-password field gets a "Use a strong password" dataset (from
   `PasswordGenerator`) instead of saved logins; the generated value fills new and confirm
   fields; `SaveInfo` makes Android offer to save it. The decision is `credentials/AutofillOffers.kt`
   (unit tested). The dataset needs no unlock. Android skips its save prompt when every
   watched field still holds a value Qasa filled, so the save shows once the user has typed
   the username (sign-up) or the current password (change form); a form whose only watched
   fields Qasa filled itself gets no save prompt.
3. **iOS**: 26.2 generate and save requests; text insertion gets a "Strong password"
   choice for iOS 18–26.1.
