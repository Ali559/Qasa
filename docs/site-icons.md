# Site icons (TODO Phase 7, 2026-11-16)

A login shows its site's own icon. The owner picked, in `design/previews/site-icons.html`:
the fallback is **C, the calm letter**, and icons come **only from the site itself**. This
file is the shared spec: iOS, Android, the desktop and the extension follow it exactly, and
`site-icons-vectors.json` holds the cases every platform's unit tests run.

The bundled brand marks (`assets/site-icons`, `SiteIcon*`, `siteIconData.ts`) and the
brand-colour letter table in `SiteLogo` are removed.

## What a tile shows

| Item | Vault list tile (44 circle) | Detail hero (88 circle) |
| --- | --- | --- |
| Login with a cached icon | the cached PNG filling the circle, on `siteIconTile`, cut to the circle; a 1pt `stroke` ring only for a glyph (see below) | the same at 88 |
| Login without one (none yet, none found, setting off) | **calm letter**: the name's first letter or digit, 18 bold, `accent` on `cardMuted` | the same letter at 36 bold on `cardMuted` |
| Note, card, other kinds | their type icon, unchanged | unchanged |

The cached PNG is already shaped for the circle (step 7), so every app draws it the same
way at any size: the whole image, scaled to the tile, clipped to a circle. (Phase 11,
2026-11-17: before this the icon was drawn at 28 in the middle of the tile, and square or
wide favicons looked boxed in.)

The ring: a bleed or edge icon is its own solid circle and has none; a glyph sits on the
light `siteIconTile` and needs one. The tile tells them apart from the PNG alone: pixel
`(11, 11)` of the 64 × 64 PNG (on the circle's edge, inside the glyph box's margin) has
alpha < 128 for a glyph, so the ring is drawn exactly then.

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
6. Fit into 64 × 64 px (aspect kept, centred, transparent padding).
7. **Shape** it for the circle (Phase 11, chosen 2026-11-17 in `design/previews/vault-list.html`),
   then encode PNG. Pixel centres are at `(x + 0.5, y + 0.5)`; "opaque" means alpha ≥ 128.
   - **Bleed** (solid inside its circle: a square, rounded square or circle): at least 95% of
     the pixels within 30 px of the centre `(32, 32)` are opaque. Keep the image as it is;
     it fills the circle and the tile cuts its corners.
   - **Edge** (solid but not square, or small on its canvas): take the bounding box of the
     opaque pixels. It is at least 16 × 16, at least 95% of it is opaque, and its **ring**
     (the pixels 3 or 4 px inside the box's edge, so a resampler's rim doesn't count) is at
     least 90% opaque with at least 90% of those within 24 (every channel) of the ring's
     colour. The ring's colour is, per channel, the value at index `n / 2` of the sorted
     values. Output: a 64 × 64 square filled with that colour, and the box's content fitted
     into a centred 48 × 48 box on top.
   - **Glyph** (anything else: a shape on a see-through background, a border of many colours):
     the bounding box's content fitted into a centred 38 × 38 box on a transparent canvas,
     with its corners cut to a radius of 8 so a hard square edge never shows. No opaque
     pixel at all: the canvas stays empty (the tile then shows `siteIconTile` only).

`docs/site-icon-shapes/` holds the test images and `reference.py`, the reference for steps
6–7; `site-icons-vectors.json` → `shapes` lists the kind each one gets (and the colour for
edge, within 6 per channel). Resamplers differ between platforms, so the tests check the
kind and the colour, not every pixel.

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
- An entry written before step 7 existed holds an unshaped PNG. Each entry records that it is
  shaped (`shape: 1`); one without it is shaped from its PNG when read and written back.
  No site is asked again for this.

## Setting

Settings → Security: **Website icons** (`settings.siteIcons`), hint
`settings.siteIconsHint`, a switch, **on** by default. Off: no request is ever made, the
cache is deleted, every login shows its letter.

## Extension

The extension never fetches. The desktop sends each login's cached icon (PNG, base64) with
the items it gives the extension. The popup draws it as an `<img src="data:image/png…">` filling its circle, like the apps.
The in-page menu decodes it with `createImageBitmap` onto a `<canvas>` (a page's CSP
`img-src` can block `data:` images in the page), and shows the letter if that fails.
