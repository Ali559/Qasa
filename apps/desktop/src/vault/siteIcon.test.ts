// The shared cases in docs/site-icons-vectors.json (every platform runs them): the calm
// letter, and the host a login's icon is asked of (the Rust core's `site_icons::host_of`
// is held to the same cases).
import { readFileSync } from 'node:fs'

import { describe, expect, it } from 'vitest'

import { siteLetter } from './present'
import { iconHost, needsRing, pngDataUrl } from './siteIcon'

interface Vectors {
  host: Array<{ website: string; host: string | null }>
  letter: Array<{ name: string; letter: string }>
}

const vectors: Vectors = JSON.parse(readFileSync(new URL('../../../../docs/site-icons-vectors.json', import.meta.url), 'utf8'))

describe('site icon vectors', () => {
  it('letter: every case', () => {
    expect(vectors.letter.length).toBeGreaterThan(0)
    for (const c of vectors.letter) expect(siteLetter(c.name), JSON.stringify(c.name)).toBe(c.letter)
  })

  it('host: every case', () => {
    expect(vectors.host.length).toBeGreaterThan(0)
    for (const c of vectors.host) expect(iconHost(c.website), JSON.stringify(c.website)).toBe(c.host)
  })

  it('only base64 becomes a data URL', () => {
    expect(pngDataUrl('iVBORw0KGgo=')).toBe('data:image/png;base64,iVBORw0KGgo=')
    expect(pngDataUrl('"><script>')).toBeNull()
    expect(pngDataUrl(null)).toBeNull()
  })
})

describe('the ring', () => {
  // Only a glyph gets the 1px stroke ring: pixel (11, 11) of the shaped icon is see-through.
  const icon = (alphaAt11: number) => {
    const rgba = new Uint8ClampedArray(64 * 64 * 4).fill(255)
    rgba[(11 * 64 + 11) * 4 + 3] = alphaAt11
    return rgba
  }
  it('is drawn for a see-through probe pixel only', () => {
    expect(needsRing(icon(0), 64)).toBe(true)
    expect(needsRing(icon(127), 64)).toBe(true)
    expect(needsRing(icon(128), 64)).toBe(false)
    expect(needsRing(icon(255), 64)).toBe(false)
  })
})
