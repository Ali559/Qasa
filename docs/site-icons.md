# Site icons (TODO Phase 7, 2026-11-16)

A login shows its site's own icon. The owner picked, in `design/previews/site-icons.html`:
the fallback is **C, the calm letter**, and icons come **only from the site itself**. This
file is the shared spec: iOS, Android, the desktop and the extension follow it exactly, and
`site-icons-vectors.json` holds the cases every platform's unit tests run.

The bundled brand marks (`assets/site-icons`, `SiteIcon*`, `siteIconData.ts`) and the
brand-colour letter table in `SiteLogo` are removed.

## What a tile shows

| Item | Tile (48 circle) | Detail hero (88 circle) |
| --- | --- | --- |
| Login with a cached icon | the icon, `size.siteIconGlyph` (28), centred on `siteIconTile`, with a 1pt `stroke` ring | the icon at `size.heroSiteIconGlyph` (52) on `siteIconTile`, 1pt `stroke` ring |
| Login without one (none yet, none found, setting off) | **calm letter**: the name's first letter or digit, 18 bold, `accent` on `cardMuted` | the same letter at 36 bold on `cardMuted` |
| Note, card, other kinds | their type icon, unchanged | unchanged |

The letter is the first letter or digit of the item name, upper-cased (`?` when none). There
is no colour per site any more. While an icon is being fetched the letter shows; when it
arrives the tile swaps without animation (reduce motion or not).

## Where icons come from

Only from the login's own site, over HTTPS. Never a third party, never a Qasa server.

- **Android app logins** (`website` = `androidapp://<package>`): the installed app's icon
  from `PackageManager.getApplicationIcon`, no network. Not installed: the letter.
- **iOS and desktop app logins** don't exist (iOS stores the associated domain as a website).

### Which host

Read the login's `website` the way autofill does: trim; if it has `://` the scheme must be
`http` or `https` (anything else: no icon); without a scheme take it as `https://<website>`;
lowercase the host, drop one trailing `.` and a leading `www.`. The **host** is the cache
key and the address asked. `http://` websites are still asked over `https`.

### Never asked (the letter shows)

- IP literals (IPv4, IPv6, bracketed or not).
- `localhost` and any host without a dot.
- Hosts ending in `.local`, `.localhost`, `.lan`, `.home`, `.internal`, `.intranet`,
  `.corp`, `.home.arpa`, `.test`, `.example`, `.invalid`, `.onion`.
- A redirect to any of the above, or to anything not `https`.

So a router or NAS login never makes the phone touch the local network (no iOS Local
Network prompt), and nothing leaves the device for sites that aren't on the internet.

### The request

Every request: HTTPS only; no cookies, credentials or shared cache (an ephemeral session);
`User-Agent: Mozilla/5.0 (compatible)` and no `Accept-Language` (iOS can't drop the header, so it sends it
 empty), so the site learns
nothing about the user or that they use Qasa; at most 3 redirects, each checked against the
rules above; 8 s timeout per request.

1. `GET https://<host>/` with `Accept: text/html`. Read at most 512 KB.
2. Collect every `<link>` whose `rel` (case-insensitive, space-separated) contains `icon`
   or is `apple-touch-icon` / `apple-touch-icon-precomposed`. Resolve `href` against the
   final URL. Skip it if it isn't `https:` (a `data:image/png;base64,` or
   `data:image/x-icon;base64,` href is fine) or if its `type` is `image/svg+xml` or its path
   ends in `.svg` (SVG isn't decoded on every platform).
3. Order: `apple-touch-icon*` first; then by the largest size in `sizes` (`any` counts as
   0, missing as 0), larger first; ties keep document order. Try them in that order.
4. If none is usable, try `https://<host>/favicon.ico`.
5. Download at most 256 KB. Decode PNG, ICO (largest image), JPEG, GIF (first frame) and
   WebP where the platform can. Reject anything under 16 × 16.
6. Normalise: fit into 64 × 64 px (aspect kept, centred, transparent padding), encode PNG.

`site-icons-vectors.json` → `pickIcons` holds HTML snippets and the expected order of URLs.

## When

- When a login is **saved** with a website (editor, iOS autofill saves imported on unlock,
  Android autofill save, extension save through the desktop), if its host has no fresh
  cache entry.
- **Lazily** when a login tile with no cache entry is drawn (existing vaults): queued, at
  most 4 at a time, de-duplicated by host.
- Only while the vault is unlocked and the **Website icons** setting is on. Never from the
  iOS Credential Provider or the Android autofill service UI (they show the letter).

## Cache

Per device and per vault, keyed by host: `{ png | none, checkedAt }`.

- Stored **encrypted with the vault key** (the same AEAD as items) in app-private storage,
  never in a shared or backed-up-in-plaintext location: the list of hosts is as private as
  the vault. Deleted with the vault (sign out, reset, delete account) and when the setting
  is turned off.
- An icon is refreshed after 30 days (in the background, next time the tile is drawn).
- `none` (the site answered but had no usable icon) is retried after 7 days; a network
  failure (offline, timeout, TLS error) after 1 day.
- It is not synced: each device asks each site once. Not in backups or exports.

## Setting

Settings → Security: **Website icons** (`settings.siteIcons`), hint
`settings.siteIconsHint`, a switch, **on** by default. Off: no request is ever made, the
cache is deleted, every login shows its letter.

## Extension

The extension never fetches. The desktop sends each login's cached icon (PNG, base64) with
the items it gives the extension. The popup draws it as an `<img src="data:image/png…">`.
The in-page menu decodes it with `createImageBitmap` onto a `<canvas>` (a page's CSP
`img-src` can block `data:` images in the page), and shows the letter if that fails.
