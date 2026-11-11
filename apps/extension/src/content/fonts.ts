// The in-page menu's font: IBM Plex Sans Arabic (every language), as in the popup and the
// apps. The page can't load them from the extension (that would need
// web_accessible_resources, which lets any site detect Qasa by probing URLs), so the
// service worker reads the bundled files and hands over the bytes. Loaded once per
// document, the first time a menu opens; the menu stays hidden until they arrive (at most
// layout.ts `FONT_WAIT_MS`), so it never paints in a system face.

import { FONT_WEIGHTS, type FontWeight } from '@ext/shared/fonts'

/** A per-document family name, so a page's own "IBM Plex Sans Arabic" can't stand in for ours. */
const SUFFIX = Math.random().toString(36).slice(2, 10)
export const PLEX = `qasa-plex-${SUFFIX}`

/** Base64 per weight, because extension messages carry JSON. */
export type FontBytes = Record<FontWeight, string>

/** One fetch of the bytes per content script; one load per document it serves (a
 *  same-origin child frame is a separate document with its own font set). */
let bytes: Promise<FontBytes> | null = null
const loaded = new WeakMap<Document, Promise<void>>()

function decode(b64: string): ArrayBuffer {
  const bin = atob(b64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return bytes.buffer
}

/** Adds every weight to `doc`. Failures leave the system fonts in place. */
export function loadFonts(doc: Document, fetchBytes: () => Promise<FontBytes>): Promise<void> {
  const existing = loaded.get(doc)
  if (existing) return existing
  const done = (async () => {
    try {
      bytes ??= fetchBytes()
      const all = await bytes
      // A same-origin child frame needs its own window's FontFace for its font set.
      const Face = doc === document ? FontFace : ((doc.defaultView as (Window & typeof globalThis) | null)?.FontFace ?? FontFace)
      const faces = FONT_WEIGHTS.map((weight) => new Face(PLEX, decode(all[weight]), { weight: String(weight) }))
      for (const face of faces) {
        await face.load()
        doc.fonts.add(face)
      }
    } catch {
      // Try again on the next menu.
      bytes = null
      loaded.delete(doc)
    }
  })()
  loaded.set(doc, done)
  return done
}
