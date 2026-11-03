# App screenshots for the landing page

The hero shows the real Passwords screen from the iOS app: one image per language and theme in
`public/screens/passwords-{en,ckb,ar}-{light,dark}.webp` (600 px wide, from the 1206 px
simulator capture).

## Test vault

A DEBUG build launched with `-screenshotVault` fills an empty vault with fake local entries
instead of the developer samples (`VaultSeedData.seedScreenshotVault` in
`apps/ios/qasa_ios/Models/VaultModels.swift`). Gmail's password is in Pwned Passwords, so the
breach check finds one.

- Test main password for this vault: `qasa-screens-2026!` (simulator only, never a real vault)

## Refreshing them

1. Build the `qasa` scheme for an iPhone simulator and install it.
2. Set a clean status bar: `xcrun simctl status_bar booted override --time 9:41 --batteryState charged --batteryLevel 100 --cellularBars 4 --wifiBars 3`
3. First run only: launch with `-wipeLocalVault -screenshotVault -qasa.language en -qasa.hasCompletedWelcome YES`, create the vault with the test password and turn biometrics off.
4. For each language (`en`, `ckb`, `ar`) and theme (`light`, `dark`): launch with
   `-screenshotVault -qasa.language <lang> -qasa.theme <theme> -qasa.hasCompletedWelcome YES`,
   unlock, open the Passwords tab, then `xcrun simctl io booted screenshot --type=png passwords-<lang>-<theme>.png`.
   With the Arabic keyboard active, typed text comes out in Arabic letters: paste the password
   instead (`printf '…' | xcrun simctl pbcopy booted`, then long-press the field and choose Paste).
5. Resize to 600 px wide, save as WebP (quality about 82) into `public/screens/`.

Keep the full-size PNGs out of the repo.
