// Monochrome icons for common sites, bundled (`assets/site-icons`, generated into
// `siteIconData.ts`) so nothing is fetched and no third party learns which sites the user
// has. A login whose website isn't in the table keeps its letter tile (`siteLogo`). Same
// table and matching as Android `SiteIcons.kt` and iOS `SiteIcon.swift`.
import { SITE_ICON_DOMAINS, SITE_ICON_PATHS } from './siteIconData'

/**
 * The lowercased host of an `http(s)` URL or bare host, without `www.` (the bridge's
 * `matcher.rs` `host_of`), or null.
 */
export function hostOf(raw: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null
  let url: URL
  try {
    url = new URL(trimmed.includes('://') ? trimmed : `https://${trimmed}`)
  } catch {
    return null
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
  const host = url.hostname.toLowerCase().replace(/\.+$/, '').replace(/^www\./, '')
  return host || null
}

/**
 * The icon id for a login's website, or null. The host and each parent domain are looked
 * up, so `accounts.google.com` and `mail.google.co.uk` find their site. The table lists
 * only registrable domains (`matcher.rs` tests check each against the Public Suffix
 * List), so the first hit is the host's registrable domain.
 */
export function siteIconId(website: string | null | undefined): string | null {
  let host = website ? hostOf(website) : null
  while (host && host.includes('.')) {
    const id = SITE_ICON_DOMAINS[host]
    if (id) return id
    host = host.slice(host.indexOf('.') + 1)
  }
  return null
}

/** The icon's SVG path (24×24 viewBox) for a login's website, or null. */
export function siteIconPath(website: string | null | undefined): string | null {
  const id = siteIconId(website)
  return id ? (SITE_ICON_PATHS[id] ?? null) : null
}
