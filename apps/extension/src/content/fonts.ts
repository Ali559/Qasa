// The in-page menu's fonts: Rubik, and Rudaw for Arabic and Kurdish, as in the popup and
// the apps. The page can't load them from the extension (that would need
// web_accessible_resources, which lets any site detect Qasa by probing URLs), so the
// service worker reads the bundled files and hands over the bytes. Loaded once per
// document, the first time a menu opens. Until they arrive the menu uses system fonts.

/** Per-document family names, so a page's own "Rubik" can't stand in for ours. */
const SUFFIX = Math.random().toString(36).slice(2, 10)
export const RUBIK = `qasa-rubik-${SUFFIX}`
export const RUDAW = `qasa-rudaw-${SUFFIX}`

export interface FontBytes {
  /** Base64, because extension messages carry JSON. */
  rubik: string
  rudaw: string
}

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

/** Adds both faces to `doc`. Failures leave the system fonts in place. */
export function loadFonts(doc: Document, fetchBytes: () => Promise<FontBytes>): Promise<void> {
  const existing = loaded.get(doc)
  if (existing) return existing
  const done = (async () => {
    try {
      bytes ??= fetchBytes()
      const { rubik, rudaw } = await bytes
      // A same-origin child frame needs its own window's FontFace for its font set.
      const Face = doc === document ? FontFace : ((doc.defaultView as (Window & typeof globalThis) | null)?.FontFace ?? FontFace)
      const faces = [new Face(RUBIK, decode(rubik), { weight: '300 900' }), new Face(RUDAW, decode(rudaw), { weight: '400' })]
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
