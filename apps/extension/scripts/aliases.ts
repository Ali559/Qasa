// Path aliases and plugins shared by the build and Vitest. `@` is the desktop's src (theme,
// fonts, i18n, components), except `@/prefs`: the desktop's prefs talk to Tauri, so the
// popup swaps in its own (src/popup/prefs.ts) with the same `useT` export.
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import type { Plugin } from 'vite'

const here = (p: string) => fileURLToPath(new URL(p, import.meta.url))

export const ROOT = here('..')
export const DESKTOP_SRC = here('../../desktop/src/')
const CATALOGS = `${DESKTOP_SRC}i18n/`

export const aliases = [
  { find: /^@\/prefs$/, replacement: here('../src/popup/prefs.ts') },
  { find: /^@\//, replacement: DESKTOP_SRC },
  { find: /^@ext\//, replacement: here('../src/') },
]

/** Keys the in-page menu needs besides `ext.page.*`: the main-password-due note and its
 *  "Open Qasa" button (the same strings as the desktop's unlock screen and the popup). */
const CONTENT_EXTRA_KEYS = new Set(['brand.name', 'auth.unlock.masterPasswordDueFmt', 'ext.openQasa'])

/** `virtual:qasa-content-strings`: the `ext.page.*` keys and `CONTENT_EXTRA_KEYS` per language. */
export function contentStrings(): Plugin {
  const id = 'virtual:qasa-content-strings'
  return {
    name: 'qasa-content-strings',
    resolveId: (source) => (source === id ? `\0${id}` : null),
    load(resolved) {
      if (resolved !== `\0${id}`) return null
      const out: Record<string, Record<string, string>> = {}
      for (const lang of ['en', 'ar', 'ckb']) {
        const file = `${CATALOGS}${lang}.json`
        this.addWatchFile(file)
        const all = JSON.parse(readFileSync(file, 'utf8')) as Record<string, string>
        out[lang] = Object.fromEntries(Object.entries(all).filter(([k]) => k.startsWith('ext.page.') || CONTENT_EXTRA_KEYS.has(k)))
      }
      return `export default ${JSON.stringify(out)}`
    },
  }
}
