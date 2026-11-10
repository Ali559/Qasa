// Strings for the in-page menu. The content script runs in every frame of every page, so
// it carries only the `ext.page.*` keys (plus a few others: `CONTENT_EXTRA_KEYS`), cut from
// the generated catalogs at build time by the `qasa-content-strings` plugin (scripts/aliases.ts).
import catalogs from 'virtual:qasa-content-strings'

export type ContentLanguage = 'en' | 'ar' | 'ckb'

/** `{0}`, `{1}` … are replaced by `args` in order, as the desktop's `translate`. */
export function ct(language: ContentLanguage, key: string, ...args: Array<string | number>): string {
  const text = catalogs[language][key] ?? catalogs.en[key] ?? key
  return text.replace(/\{(\d+)\}/g, (match, index: string) => {
    const value = args[Number(index)]
    return value === undefined ? match : String(value)
  })
}
