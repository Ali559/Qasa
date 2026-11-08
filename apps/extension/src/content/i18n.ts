// Strings for the in-page menu. The content script runs in every frame of every page, so
// it carries only the `ext.page.*` keys (plus the brand name), cut from the generated
// catalogs at build time by the `qasa-content-strings` plugin (scripts/aliases.ts).
import catalogs from 'virtual:qasa-content-strings'

export type ContentLanguage = 'en' | 'ar' | 'ckb'

export function ct(language: ContentLanguage, key: string): string {
  return catalogs[language][key] ?? catalogs.en[key] ?? key
}
