# Releasing the desktop app and the browser extension

How Qasa for macOS, Windows and Linux is built, signed and shipped, and how the browser
extension reaches the stores. The plan behind this is
[`desktop-extension-plan.md` §10](desktop-extension-plan.md).

## How a desktop release flows

1. Bump `version` in `apps/desktop/src-tauri/tauri.conf.json` (the updater compares it)
   and commit.
2. Tag and push: `git tag desktop-v0.2.0 && git push origin desktop-v0.2.0`.
3. **Desktop release** (`.github/workflows/desktop-release.yml`) builds:
   - macOS: one universal `.dmg`;
   - Windows: NSIS `.exe` and `.msi`;
   - Linux: `.AppImage` and `.deb`.

   It signs each one when the certificates are in the secrets, signs every update
   archive with the updater key, and attaches `latest.json`. It also attaches
   version-free copies: `Qasa-macOS.dmg`, `Qasa-Windows-Setup.exe`, `Qasa-Linux.AppImage`
   and `Qasa-Linux.deb`.
4. The release is a **draft**. Download the builds, install them, then **Publish**.
5. Publishing is the release. These addresses redirect to the latest published release
   (`server/deploy/Caddyfile`):
   - `qasa-app.com/desktop/latest.json`, which installed apps poll 10 s after launch and
     from Settings → Check for updates;
   - `qasa-app.com/desktop/download/{macos,windows,linux-appimage,linux-deb}`.

**Keep the desktop the only kind of GitHub release.** "Latest release" is repository-wide.
The extension workflow attaches its zips to the workflow run, not to a release, for that
reason.

**The first time:** deploy the Caddyfile (`server/deploy/deploy.sh`) so the redirects
exist.

## Secrets

Add these under the GitHub repository's Settings → Secrets and variables → Actions.

| Secret | What | Needed for |
| --- | --- | --- |
| `TAURI_SIGNING_PRIVATE_KEY` | Contents of `~/.tauri/qasa-desktop-updater.key` | Every release |
| `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` | Don't add it: the key has no password, GitHub won't store an empty secret, and a missing secret reads as empty | — |
| `APPLE_CERTIFICATE` | Base64 of the Developer ID Application `.p12` | macOS signing |
| `APPLE_CERTIFICATE_PASSWORD` | The `.p12` export password | macOS signing |
| `APPLE_SIGNING_IDENTITY` | `Developer ID Application: <Name> (<TEAMID>)` | macOS signing |
| `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` | Apple ID, an app-specific password, the team id | Notarization |
| `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `AZURE_TENANT_ID` | A service principal with the *Trusted Signing Certificate Profile Signer* role | Windows signing |
| `AZURE_SIGNING_ENDPOINT`, `AZURE_SIGNING_ACCOUNT`, `AZURE_SIGNING_PROFILE` | e.g. `https://weu.codesigning.azure.net`, the account name, the certificate profile | Windows signing |

Without the Apple or Azure secrets the workflow still builds, unsigned. That's useful to
try the pipeline, but not for users:

- macOS refuses to open an unsigned, un-notarized app.
- Windows SmartScreen warns about it.

### The updater key

- `~/.tauri/qasa-desktop-updater.key` was generated on 2026-11-08 with `tauri signer
  generate`, with no password. Its public half is `plugins.updater.pubkey` in
  `tauri.conf.json`.
- **Back the private key up** (a password manager or an offline copy). If it is lost,
  installed apps can never update again: they only accept archives signed by this key.
  Each user would have to download a new build by hand.
- Never commit it.

### macOS: Developer ID

1. In the Apple Developer account, go to Certificates. Create a **Developer ID
   Application** certificate from a CSR made in Keychain Access.
2. Install it, then export it from Keychain Access as `.p12` with a password.
3. Run `base64 -i cert.p12 | pbcopy` and paste the result into `APPLE_CERTIFICATE`.
4. For `APPLE_PASSWORD`, create an app-specific password at appleid.apple.com → Sign-In
   and Security.

The app uses the hardened runtime and no entitlements (`src-tauri/entitlements.plist`).
Touch ID uses CryptoKit Secure Enclave keys, which need no keychain access group, so no
provisioning profile is needed.

### Windows: Azure Trusted Signing

1. In Azure, create a **Trusted Signing account** and pass its identity validation
   (individual or organization).
2. Create a **Public Trust** certificate profile.
3. Create an app registration with a client secret. Give it the *Trusted Signing
   Certificate Profile Signer* role on the account.

The workflow installs `trusted-signing-cli` and signs through it (a `bundle.windows.signCommand`
overlay). Cost is about $10 a month.

### Linux

No code signing. The updater signature covers updates of the AppImage.

## Building locally

```bash
make nm-host                     # the native messaging host, debug
cd apps/desktop
TAURI_SIGNING_PRIVATE_KEY="$(cat ~/.tauri/qasa-desktop-updater.key)" TAURI_SIGNING_PRIVATE_KEY_PASSWORD="" \
  bun run tauri build --bundles app,dmg
```

- `scripts/sidecar.ts` builds `qasa-nm-host` for the target before every `tauri dev` and
  `tauri build`, and Tauri ships it next to the app's executable. On every launch the app
  registers it with the installed browsers (`src-tauri/src/bridge/manifest.rs`).
- To change the icon, edit the iOS app icon, then run
  `python3 scripts/app-icon.py && bunx tauri icon src-tauri/icons/app-icon.png`. Delete
  the `ios/` and `android/` folders that command also creates.

## The browser extension

1. Bump `version` in `apps/extension/package.json`.
2. Tag `extension-v0.2.0` and push. **Extension release** runs the tests and
   `bun run package`, and attaches the store zips to the run.
3. Upload them by hand, following `apps/extension/store/listing.md`:
   - **Chrome Web Store** and **Edge Add-ons:** `qasa-chrome-<ver>.zip`.
   - **Firefox AMO:** `qasa-firefox-<ver>.zip` plus the source zip.

The first time an extension is published, the store assigns it an id. Add that id to
`CHROME_EXTENSION_IDS` in `apps/desktop/src-tauri/src/bridge/manifest.rs` and release the
desktop app. Until then, the desktop only lets the unpacked development build connect.
