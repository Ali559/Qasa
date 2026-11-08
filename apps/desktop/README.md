# Qasa desktop

Qasa for macOS, Windows and Linux: a Tauri 2 app with a Rust core (`src-tauri/`) and a
Vue 3 interface (`src/`). The plan, phases and design rules are in
[`docs/desktop-extension-plan.md`](../../docs/desktop-extension-plan.md).

```bash
bun install
bun run tauri dev     # the app, with hot reload (or `make desktop` from the repo root)
bun run dev           # the interface alone in a browser, prefs in localStorage
bun run tauri build   # a release bundle for this OS
```

## Rules

- **Keys never reach the webview.** Vault crypto runs in Rust (`core/qasa-vault`); the
  interface asks for decrypted items one screen at a time.
- **The iOS app is the design reference.** Colors come from
  `apps/ios/qasa_ios/Theme/Theme.swift` through `bun run tokens`; never hand-edit
  `src/theme/tokens.css`. `bun run tokens:check` fails when it is stale.
- **Strings come from `l10n/strings.json`.** Run `python3 l10n/generate.py`; never edit
  `src/i18n/*.json`. Desktop-only strings go in `l10n/platform/desktop.json`.
- **New Tauri commands** must also be added to the allow-list in `isolation/index.js`.
