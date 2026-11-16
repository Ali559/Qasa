// Site icons (docs/site-icons.md): a login's tile shows its site's own icon. The Rust core
// asks the site (only that site, over HTTPS), keeps the icons encrypted with the vault key,
// and hands them to the UI as `data:image/png` URLs; the webview never fetches. This file
// holds the host rules (the cache key, same as `site_icons/host.rs`) and the reactive
// host → icon store the tiles read. Without an icon a tile shows the letter (`siteLetter`).
import { reactive } from 'vue'

const PRIVATE_SUFFIXES = ['local', 'localhost', 'lan', 'home', 'internal', 'intranet', 'corp', 'home.arpa', 'test', 'example', 'invalid', 'onion']

/**
 * The host a login's icon is asked of and cached under, or null when it is never asked:
 * not http(s), an IP literal, a host without a dot, or a private suffix (`.local`, `.lan`…).
 * One trailing `.` and a leading `www.` are dropped.
 */
export function iconHost(website: string | null | undefined): string | null {
  const trimmed = (website ?? '').trim()
  if (!trimmed) return null
  let url: URL
  try {
    url = new URL(trimmed.includes('://') ? trimmed : `https://${trimmed}`)
  } catch {
    return null
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return null
  let host = url.hostname.toLowerCase()
  if (host.endsWith('.')) host = host.slice(0, -1)
  if (host.startsWith('www.')) host = host.slice(4)
  if (!host || !host.includes('.') || host.startsWith('[')) return null
  // A numeric last label is an IPv4 address (the URL parser has already normalised it).
  const last = host.slice(host.lastIndexOf('.') + 1)
  if (!last || /^\d+$/.test(last) || last.startsWith('0x')) return null
  if (PRIVATE_SUFFIXES.some((s) => host === s || host.endsWith(`.${s}`))) return null
  return host
}

/** Asks the core for the cached icons of some hosts (null: none yet / none found). */
export type SiteIconSource = (hosts: string[]) => Promise<Record<string, string | null>>

/** host → `data:image/png` URL, or null when asked and there is none. Missing: not asked. */
const icons = reactive(new Map<string, string | null>())
const asked = new Set<string>()
let pending = new Set<string>()
let source: SiteIconSource | null = null

/** Set by the desktop app (`siteIconsApi.ts`); in the extension and the browser mock nothing asks. */
export function setSiteIconSource(next: SiteIconSource | null): void {
  source = next
}

function flush(): void {
  const hosts = [...pending]
  pending = new Set()
  if (!source || hosts.length === 0) return
  source(hosts).then(
    (found) => {
      for (const [host, icon] of Object.entries(found)) {
        if (icon !== null || !icons.has(host)) icons.set(host, icon)
      }
    },
    () => {
      // Locked or not available: ask again next time the tile is drawn.
      for (const host of hosts) asked.delete(host)
    },
  )
}

/**
 * The icon for `host` as a data URL, or null (the letter shows). The first call for a host
 * asks the core, batched with the other tiles drawn at the same time; the tile re-renders
 * when the icon arrives.
 */
export function siteIconFor(host: string | null): string | null {
  if (!host) return null
  const icon = icons.get(host)
  if (icon === undefined && source && !asked.has(host)) {
    asked.add(host)
    if (pending.size === 0) queueMicrotask(flush)
    pending.add(host)
  }
  return icon ?? null
}

/** A request finished (the core's `site-icon` event). */
export function setSiteIcon(host: string, icon: string | null): void {
  asked.add(host)
  icons.set(host, icon)
}

/** Forgets every icon (the vault locked, or the setting went off): tiles show letters. */
export function clearSiteIcons(): void {
  icons.clear()
  asked.clear()
  pending = new Set()
}

/** A cached icon the desktop sent as base64 PNG (the extension's items) as a data URL, or null. */
export function pngDataUrl(base64: string | null | undefined): string | null {
  return base64 && /^[A-Za-z0-9+/]+={0,2}$/.test(base64) ? `data:image/png;base64,${base64}` : null
}
