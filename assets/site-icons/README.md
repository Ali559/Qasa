# Site icons

Monochrome icons for about 50 common sites, shown on a login's row (and detail header)
instead of its letter tile when the login's website is one of them. They are bundled in
every app, never fetched: fetching favicons would tell a third party which sites the user
has. Logins for any other site keep the letter tile.

## Files

- [`sites.json`](sites.json): the source of truth. Each site has an `id`, the Simple Icons
  `slug` it comes from, its `title` and brand `hex` (kept for reference; the apps tint the
  icon with their theme colours), the registrable `domains` it matches, and its single SVG
  `path` in a 24×24 viewBox.
- [`generate.py`](generate.py): writes each platform's bundled form (committed; don't
  hand-edit):
  - Android: `apps/android/app/src/main/java/alibarznji/qasa/theme/SiteIconData.kt`
  - iOS: `apps/ios/qasa_ios/Assets.xcassets/SiteIcons/` (template SVG imagesets) and
    `apps/ios/qasa_ios/Components/SiteIconData.swift`
  - Desktop and browser extension: `apps/desktop/src/vault/siteIconData.ts`
- [`import_simple_icons.py`](import_simple_icons.py): refreshes `title`, `hex` and `path`
  from a Simple Icons package.

## Edit workflow

1. Add or change a site in `sites.json` (`id`, `slug`, `domains`). List only registrable
   domains (`google.co.uk`, not `mail.google.co.uk`), and never a public suffix that
   others register under (`github.io`, `myshopify.com`): the desktop test
   `site_icon_domains_are_registrable_domains` checks this against the Public Suffix List.
2. For a new slug, refresh from Simple Icons:

   ```bash
   cd "$(mktemp -d)" && npm pack simple-icons@16.37.0 && tar xzf simple-icons-*.tgz
   python3 /path/to/qasa/assets/site-icons/import_simple_icons.py package/
   ```

3. Regenerate, and check nothing is stale:

   ```bash
   python3 assets/site-icons/generate.py
   python3 assets/site-icons/generate.py --check
   ```

## Matching

The same on every platform (Android `SiteIcons.kt`, iOS `SiteIcon.swift`, desktop and
extension `siteIcon.ts`): read the login website's host the way that app's autofill does
(lowercased, without `www.`), then look up the host and each parent domain in the table.
Because the table holds only registrable domains, the first hit is the host's registrable
domain: `accounts.google.com` and `mail.google.co.uk` find Google, `google.com.evil.io`
finds nothing.

## Source and licence

The paths come from [Simple Icons](https://simpleicons.org) 16.37.0, released under
CC0 1.0 (public domain); see `LICENSE.md`. Some common brands are not in that release (for example
Microsoft/Outlook, LinkedIn, Amazon, Slack, Adobe, Canva, OpenAI, Yahoo, Xbox, Skype);
those keep the letter tile rather than a redrawn mark.

The marks themselves are trademarks of their owners. Qasa uses them only to help the user
recognise their own saved logins, implies no endorsement, and does not recolour or alter
their shapes beyond tinting them with the app's theme colour.
