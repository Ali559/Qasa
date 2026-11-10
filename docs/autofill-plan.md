# Autofill plan

Autofill for every item that can go into a form, on every platform: logins and 2FA codes,
payment cards, addresses, names, email, phone and ID documents. The model is Dashlane:
focus a field, see suggestions for that site or app, pick one, confirm with biometrics or
the master password, and the form fills itself. Decided 2026-11-10 (product owner).

This replaces decision 3 of [`desktop-extension-plan.md`](desktop-extension-plan.md) ("no
card autofill in the extension"). Its reason was that iOS never returns cards as credentials.
Since iOS 18 a provider can insert any text, so cards are now possible everywhere, though
not in the same way on each platform (§3.3).

## Progress log

**2026-11-10 — iOS done (§3.4).**
- **Card notification:**
  - The values moved out of `userInfo` into `EphemeralSecretStore`: shared keychain, `WhenUnlockedThisDeviceOnly`, deleted on expiry. The notification extension got the keychain group.
  - Copies from the notification and the drawer are `localOnly` and expire after 30 s.
- **Credential provider:** declares `ProvidesPasswords`, `ProvidesOneTimeCodes` and `ProvidesTextToInsert`.
  - The request-based entry points are added.
  - `prepareOneTimeCodeCredentialList` returns the current code of a login with 2FA.
  - `prepareInterfaceForUserChoosingTextToInsert` → `TextInsertionView`: unlock (Face ID on its own, else the master password), then an item, then one value (`AutofillTextChoices`). Card numbers and codes are masked in the list.
- **Index:** gains Name, Email, Phone, Address and Company (text insertion only, never in the password list), and `hasTOTP`.
- **Identities:** `AutofillIdentitySync` registers a one-time-code identity per login with 2FA, and clears the store on local reset and account deletion.
- **Tests:** 7 new Swift Testing cases (`AutofillTests`), passing on the iPhone 17 simulator; the app and both extensions build.
  - Full suite: 109 of 110. `VaultSyncStoreTests/snapshotApplyRoundTrip` failed once in the parallel run and passes on its own (twice). It doesn't touch autofill, so it looks like a flaky test that was already there.
- **Not tried yet:**
  - on a device or in the simulator UI: QuickType codes, "AutoFill → Qasa" text insertion, the notification panel reading from the keychain;
  - Face ID in the extensions.
- **Follow-up:** iOS 26.2 lets a provider save passwords (`prepareInterfaceForSavePasswordRequest`), which would give iOS the save prompt Android and the extension have.

**2026-11-10 — Browser extension and desktop bridge done (§3.2).**
- **Detection:** `content/identity.ts` classifies card, name, contact and address inputs and selects (the same rules as Android). Card and profile groups live next to the login groups in `detect.ts`.
- **Menu:**
  - A real click into any fillable field opens the menu; typing closes it. A script's click or focus opens nothing.
  - Card fields list cards; profile fields list addresses, names, email, phone and company.
- **Locked vault:** the menu's Unlock sends `unlock {prompt: true}`.
  - The desktop starts Touch ID / Hello on its own thread (`Host::start_biometric`), else brings its window forward.
  - The menu waits, then lists the items for the same field.
- **Bridge ops:**
  - `identities`.
  - `fillIdentity`: values by `autocomplete` token. A profile pick fills what it lacks from the first item of each other kind.
  - `confirm`.
  - Cards answer `confirm`, then `pending` until Touch ID / Hello or the new "Fill <card>?" master password sheet (`BridgePrompts.vue`) answers. An approval is single-use, lasts 60 s, and belongs to one paired browser.
- **Filling:** the clicked field must pass the visibility check. The rest of the form must be rendered inside the page, so fields below the fold fill and fields pushed off the page don't. Selects match by value, text or number.
- **Desktop app:**
  - The Touch ID / Hello texts come from the UI language (`bridge_set_auth_reasons`).
  - A vault unlocked from the browser enters the app (`vault-unlocked`).
- **Not done on the desktop:** "Confirm every fill" (no setting there yet; logins and profiles fill once unlocked).
- **Tests:**
  - 8 Vitest tests (`identity.test.ts`).
  - 6 Playwright tests (`identity.spec.ts`): card with confirmation, dismissed confirmation, profile below the fold, click-to-open, script click ignored, locked → Unlock → list.
  - A real-bridge test (card and profile against the Rust demo server).
  - 7 Rust tests in `bridge/tests.rs`.
  - Results: 41 Chromium, 2 real-bridge and 71 Rust tests pass.
  - Firefox not run (no Firefox or geckodriver on this Mac).
- **By hand still:** Touch ID from the extension in the real app, the confirm sheet on screen, and real checkout pages (Stripe iframes).

**2026-11-10 — Android done (§3.3), except passkeys.**
- **Fill path:** `QasaAutofillService` rewritten.
  - Fields are classified by `AutofillFieldClassifier`: hints, HTML attributes, ids and labels in en/ar/ckb.
  - Logins are matched by `AutofillMatcher`, with the PSL from OkHttp. Only the browsers in `trustedBrowsers` may report a web domain; any other app is matched by package.
  - Every dataset is authenticated through `AutofillAuthActivity`: unlock or confirm, then fill in the same tap. Suggestions show while locked.
  - "Search Qasa" is always last in the list.
  - Inline (keyboard) suggestions use `androidx.autofill` 1.1.0.
  - `SaveInfo`: a submitted login opens the new-login form, prefilled.
  - Compatibility packages are declared for the common browsers.
- **Index format 3:** card holder and expiry, Name, PersonalEmail, Phone, Address and Company. The phone shows only its last 2 digits while locked.
- **Settings → "Confirm every fill"** (default off). Cards and bank accounts always ask.
- **Credential Manager:** entries are matched to the caller, and `CredentialPickActivity` returns the `PasswordCredential`.
- **Auto-lock:**
  - A vault unlocked from autofill now starts the background clock (`AutoLockMonitor.lockIfExpiredInBackground`).
  - Opening the app afterwards runs the post-unlock bootstrap (`RootViewModel.ensureStarted`).
- `AutofillTotpActivity` was removed; its 2FA copy moved into `AutofillDatasets`.
- **Tests:**
  - 26 new JVM tests: `AutofillFieldClassifierTest`, `AutofillMatcherTest`, `AutofillValuesTest`.
  - Emulator (API 37, Chrome 157 with "Autofill using another service" on):
    - Login suggestion on focus, with no secret in it.
    - Unlocked → fills at once.
    - Locked → "Unlock Qasa · Fill into localhost" → password → filled with no second pick.
    - Checkout page classified fully.
    - Card asks even when unlocked, then fills the number, name and CVC.
    - Submitting a new login → "Save username and password to Qasa" → unlock → New Login prefilled.
- **Not tried on a device yet:**
  - biometric unlock in the fill path (the emulator had no fingerprint enrolled);
  - inline suggestions;
  - the address/profile fill (no address item in the test vault; covered by unit tests);
  - a native app login;
  - Credential Manager.
- **Found, not fixed:** auto-lock counts only taps on `MainActivity`. Typing in an editor sheet (its own dialog window) for longer than the timeout locks the vault and loses the draft.

**2026-11-10 — Plan written.** Current state mapped on the three platforms (§2).

---

## 1. Rules (every platform)

1. **The user picks first, then the vault opens.** A field shows suggestions (title and a
   masked hint, never a secret). Secrets are decrypted only after the user picks one.
2. **Locked vault: unlock in place, then finish.** When the user picks a suggestion and the
   vault is locked, biometrics start on their own (when enabled). The master password is
   always the fallback. After the unlock, the fill finishes with no second pick.
3. **When to ask again** (re-auth on an unlocked vault):

   | Kind | Default | Setting |
   |------|---------|---------|
   | Login, 2FA code | No (the unlock is enough) | "Confirm every fill": ask every time |
   | Address, name, email, phone, company | No | Same setting |
   | Payment card, bank account | **Always** | None |
   | ID card, passport, driving licence | **Always** | None |

   iOS asks on every fill anyway (the extension has no session), so it already does more
   than this table.
4. **Matching.** Logins are offered only for their own site or app: the ranking of
   `AutofillContextMatcher.swift` (exact host +100, subdomain +80, …, offer at ≥ 60).
   Identity kinds (address, name, card, …) are not tied to a site and are offered on any
   form that asks for them.
5. **Never silently.** No fill without a user action on that field. The rest of the
   extension's clickjacking rules stay (closed shadow root, visibility checks).
6. **Fill only what the form asks for.** A card pick fills the number, expiry, name and CVV
   fields the form has, and nothing else. An address pick fills the address fields, plus the
   name, email and phone fields of the same form, from the first item of each kind.

## 2. Where it stands (2026-11-10)

| | Browser extension + desktop | Android | iOS |
|---|---|---|---|
| Kinds filled | Logins, 2FA | Logins (first 5, any app) | Logins (2FA code copied) |
| Opens on | Click on the Qasa icon | Field focus (system) | QuickType bar (system) |
| Locked vault | "Unlock" brings the window forward; the user picks again | **Nothing shown** | Unlock sheet (Face ID, password) |
| Re-auth when unlocked | Never | **Never** | Every fill |
| Site matching | Yes (≥ 60) | **None: every app gets every login** | Yes |
| Cards | No (decision 3) | No (indexed, never offered) | Copy drawer and notification |
| Addresses, names | No | No | No |
| Save new logins | Yes | No (no-op) | No |

**Security problems found while mapping:**
- **Android** offers the first five logins of the vault to any app or site that asks, with
  their passwords already in the dataset. A hostile app can read them without the user
  picking anything (§4.1).
- **Android** `QasaCredentialProviderService` returns entries that never return a credential:
  every entry opens `MainActivity`.
- **iOS** copying a card puts the full number, CVV and expiry in plain text in the local
  notification's `userInfo` (`EphemeralPaymentNotificationSupport.swift`), and the
  pasteboard copy is neither local-only nor expiring (`PaymentClipboard.swift`).

## 3. Design

### 3.1 Field classification (shared vocabulary)

Every platform maps a field to one of these, from the platform's hints first
(`autocomplete` / `autofillHints` / `textContentType`), then from names, ids, labels and
placeholders in en/ar/ckb:

| Group | Field types |
|-------|-------------|
| Login | `username`, `email-as-username`, `password`, `new-password`, `one-time-code` |
| Card | `cc-number`, `cc-name`, `cc-exp` (one field), `cc-exp-month`, `cc-exp-year`, `cc-csc` |
| Name | `name` (full), `given-name`, `additional-name`, `family-name`, `honorific-prefix` |
| Contact | `email`, `tel` (full), `tel-country-code`, `tel-national` |
| Address | `street-address`, `address-line1`, `address-line2`, `address-level2` (city), `postal-code`, `country` |
| Company | `organization` |

A form is a **login form** when it has a password field (or a username step), otherwise an
**identity form** when it has at least one card, address or name field. A lone email or tel
field outside a login form counts as identity. The values come from these items:

| Field type | Item and field |
|------------|----------------|
| `cc-*` | `Card`: `number`, `accountHolder`, `expiryDate` (`MM/YY`, split for month and year), `securityCode` |
| `name`, `given-name`, … | `Name`: `firstName`, `middleName`, `lastName`, `honorific` |
| `email` | `PersonalEmail.email` |
| `tel*` | `Phone`: `countryCode` + `number` |
| address fields | `Address`: `address`, `building`/`apartment` (line 2), `city`, `zipCode`, `countryCode` (ISO; a `<select>` is matched by code or name) |
| `organization` | `Company` name |

### 3.2 Browser extension + desktop

- **Focus opens the menu.** A trusted focus or click on a detected field opens the Qasa menu
  under it (Dashlane). Escape or typing closes it. The icon stays as the visible affordance.
  Focus that a script causes (`!isTrusted`) never opens it.
- **Detection** (`content/detect.ts`) gains the identity groups of §3.1. Card fields inside
  cross-origin payment iframes (Stripe, Adyen) are their own frame: that frame's content
  script handles them, and identity kinds have no site check, so they fill there too.
- **Menu sections:** logins for the site (login forms); cards (card fields); addresses and
  names (identity fields). Each row: title plus a masked hint (`•••• 4242`, `Erbil`).
- **Bridge protocol v1 additions** (`shared/protocol.ts`, `src-tauri/src/bridge/`):

  | Op | Args | Returns |
  |----|------|---------|
  | `identities` | — | `[{id, kind, title, hint}]` for card, name, email, phone, address, company |
  | `fillIdentity` | `itemId` | The item's fields by §3.1 type. Card and ID kinds run the re-auth first |
  | `unlock` | `prompt: true` | Desktop starts Touch ID / Windows Hello itself when enabled (`vault_unlock_biometric`), else brings the window forward on the Unlock screen. Replies when unlocked, cancelled, or after 120 s |
  | `fill` | `itemId, url` | Unchanged; runs the re-auth when "Confirm every fill" is on |

  The re-auth on the desktop is `vault_verify_biometric`, or a small "Confirm with your master
  password" window when biometrics are off. It answers `{error: 'cancelled'}` when the user
  closes it.
- **Resume after unlock:** the content script keeps the pick (field group and item id) while
  `unlock` runs, and fills once it answers unlocked, after the same visibility checks.

### 3.3 Android

- **Parse properly** (`QasaAutofillService`): read `autofillHints`, `htmlInfo.attributes`
  (`type`, `name`, `id`, `autocomplete`), `hint`, `idEntry` and `inputType`; classify by §3.1.
  Record `webDomain` (browsers) and `idPackage` / the calling package.
- **Match:** logins only for the calling app (stored `androidApp` package or Digital Asset
  Links-free host match from `websiteHost` and the web domain). Browsers: the compatibility
  packages for Chrome, Firefox, Edge, Brave, Samsung Internet, Opera, Vivaldi, DuckDuckGo in
  `autofill_service.xml`, and `webDomain` must match the login host.
- **Every dataset is authenticated.** The dataset carries only the presentation (title and
  masked hint) and `null` values; `setAuthentication` points to a new `AutofillAuthActivity`
  (replaces `AutofillTotpActivity`). It unlocks when locked (biometric via the sealed master
  password, else the master password field), re-auths by the §1 table when unlocked, builds
  the real dataset, copies the 2FA code when the login has one, and returns it. The fill then
  completes in the same tap.
- **Locked vault:** suggestions still show, from the plaintext part of the index (title,
  subtitle, host, last 4). That is what the index already holds; it never held secrets in
  plaintext.
- **Identity kinds:** index Name, PersonalEmail, Phone, Address, Company (metadata plain,
  values sealed like the others); index version 3.
- **Inline suggestions** on Android 11+ (keyboard strip) when the request has an
  `InlineSuggestionsRequest`.
- **Save:** `SaveInfo` for login forms; `onSaveRequest` opens the app's new-login form
  prefilled (confirmed by the user, never saved silently).
- **Credential Manager** (`QasaCredentialProviderService`): entries go to a handler activity
  that runs the same unlock and returns the `PasswordCredential` through
  `PendingIntentHandler`. Passkeys stay out of scope (§6).

### 3.4 iOS

- **Text insertion (iOS 18+):** `ProvidesTextToInsert` and
  `prepareInterfaceForUserChoosingTextToInsert()`. In any text field the user picks
  AutoFill → Qasa, then an item and a field (card number, expiry, address line, name, phone,
  email, …), confirms with Face ID / the password, and Qasa inserts that text. This is the
  only way iOS lets a third-party provider fill cards and addresses; Apple doesn't let
  providers fill whole card or address forms. The copy drawer stays for iOS 17.
- **One-time codes (iOS 18+):** `ProvidesOneTimeCodes`, `ASOneTimeCodeCredentialIdentity`
  per login with 2FA, `prepareOneTimeCodeCredentialList` and the one-time-code variant of
  `provideCredentialWithoutUserInteraction` (always `userInteractionRequired`).
- **Index** gains Name, PersonalEmail, Phone, Address, Company (`AutofillItemKind`).
- **Card notification fix:** the notification carries only the item id and the field
  labels; the content extension decrypts from the sealed index, after Face ID. The
  pasteboard copy is `localOnly` with a 30 s expiration.
- **`LocalVaultReset`** also clears the `ASCredentialIdentityStore`.

## 4. Order

1. **Android** (the security problems first): authenticated datasets, matching, unlock
   activity; then identity kinds, browsers, inline suggestions, save, Credential Manager.
2. **Extension + desktop:** identity detection and fill, `identities`/`fillIdentity`,
   biometric unlock from the bridge, resume after unlock, focus opens the menu, re-auth.
3. **iOS:** card notification fix, text insertion, one-time codes, identity index.

## 5. Testing

| Platform | Automated | By hand |
|----------|-----------|---------|
| Extension | Vitest for detection on new fixtures; Playwright fixtures: card checkout, split expiry month/year, address form with country `<select>`, name+email+phone signup, Stripe-like card iframe, locked vault that unlocks mid-fill, re-auth cancelled, script-focused field (must not open). Chrome and Firefox (`bun run e2e`, `e2e:firefox`) | Real Chrome and Edge against the desktop: Google login, a real checkout page |
| Desktop bridge | `cargo test`: `identities`, `fillIdentity` for each kind, re-auth required for cards, locked → `locked` error, cancelled re-auth | Touch ID prompt from the bridge |
| Android | JVM tests: field classification on recorded `AssistStructure`-like nodes, matching (package, web domain, subdomain, lookalike), dataset never carries values, index v3 round trip | Emulator: Chrome login, a native app login, a checkout form, locked → biometric → filled |
| iOS | XCTest: matcher, identity sync builds the right identities, text-insertion field list per kind, notification carries no secret | Simulator: QuickType login, AutoFill → Qasa text insertion of a card number |

## 6. Out of scope

- **Passkeys** on every platform: their own plan.
- **Safari extension** (desktop plan decision 6).
- **Filling ID documents into forms by field type:** no standard `autocomplete` tokens exist;
  they are offered for copy and, on iOS, text insertion.
