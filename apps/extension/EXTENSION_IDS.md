# Extension ids

The ids the desktop's native messaging host manifests must allow.

| Browser | Id | Host manifest field |
| --- | --- | --- |
| Chrome / Chromium / Edge (unpacked dev build) | `fiamcbmecfeeofaadngagmdldkgncmmd` | `"allowed_origins": ["chrome-extension://fiamcbmecfeeofaadngagmdldkgncmmd/"]` |
| Firefox | `extension@qasa-app.com` | `"allowed_extensions": ["extension@qasa-app.com"]` |

Native host name: `com.qasa_app.desktop` (host names allow only lowercase letters, digits,
`_` and `.`, so the app id's `-` becomes `_`).

- The Chrome id is pinned by the `key` field in the Chrome manifest
  (`scripts/manifest.ts`, `DEV_PUBLIC_KEY`), the public half of `.dev-key.pem`. The private
  key is not committed; it is only needed to pack a `.crx` with the same id.
- The Chrome Web Store and Edge Add-ons assign their own ids when the extension is first
  uploaded (the store build, `bun run package`, has no `key`). After publishing, add both
  ids to `CHROME_EXTENSION_IDS` in `apps/desktop/src-tauri/src/bridge/manifest.rs`, next to
  the dev id, so the desktop writes them into `allowed_origins`, and fill in the table
  below. The desktop release must ship before (or with) the store listing going live, or
  the store build can't reach the app.
- Firefox keeps `extension@qasa-app.com` in every build (`browser_specific_settings.gecko.id`),
  so AMO needs no change on the desktop side.

| Store | Id |
| --- | --- |
| Chrome Web Store | (after the first upload) |
| Edge Add-ons | (after the first upload) |
- The e2e tests check the id: the service worker URL must be
  `chrome-extension://fiamcbmecfeeofaadngagmdldkgncmmd/background.js`.
