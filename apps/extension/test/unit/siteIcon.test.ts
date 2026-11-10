import { existsSync, readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

import { hostOf, siteIconId, siteIconPath } from '@/vault/siteIcon'
import { SITE_ICON_DOMAINS, SITE_ICON_PATHS } from '@/vault/siteIconData'

// Same cases as Android `SiteIconsTest` and iOS `SiteIconTests`.
describe('bundled site icons', () => {
  it('match by registrable domain', () => {
    expect(siteIconId('https://accounts.google.com/signin')).toBe('google')
    expect(siteIconId('gmail.com')).toBe('google')
    expect(siteIconId('mail.google.co.uk')).toBe('google')
    expect(siteIconId('https://www.YouTube.com/')).toBe('youtube')
    expect(siteIconId('twitter.com')).toBe('x')
    expect(siteIconId('https://www.icloud.com')).toBe('apple')
    expect(siteIconId('github.com')).toBe('github')
    expect(siteIconPath('github.com')).toBe(SITE_ICON_PATHS.github)
  })

  it('leave unknown and look-alike sites to the letter tile', () => {
    for (const site of ['example.com', '', 'google.com.evil.io', 'notgoogle.com', 'alice.github.io', 'androidapp://com.instagram.android', 'http://10.0.0.1', 'javascript:alert(1)']) {
      expect(siteIconId(site), site).toBeNull()
    }
    expect(siteIconPath(undefined)).toBeNull()
  })

  it('read hosts like the bridge', () => {
    expect(hostOf('https://www.GitHub.com/login?x=1')).toBe('github.com')
    expect(hostOf('github.com')).toBe('github.com')
    expect(hostOf('ftp://example.com')).toBeNull()
    expect(hostOf('  ')).toBeNull()
  })

  // The store source zip has no assets/ folder; the monorepo checks it.
  const sitesJson = new URL('../../../../assets/site-icons/sites.json', import.meta.url)
  it.skipIf(!existsSync(sitesJson))('are generated from assets/site-icons/sites.json', () => {
    const source = JSON.parse(readFileSync(sitesJson, 'utf8')) as {
      sites: Array<{ id: string; domains: string[]; path: string }>
    }
    expect(Object.keys(SITE_ICON_PATHS)).toEqual(source.sites.map((s) => s.id))
    for (const site of source.sites) {
      expect(SITE_ICON_PATHS[site.id]).toBe(site.path)
      for (const d of site.domains) expect(SITE_ICON_DOMAINS[d]).toBe(site.id)
    }
    expect(Object.values(SITE_ICON_DOMAINS).every((id) => id in SITE_ICON_PATHS)).toBe(true)
  })
})
