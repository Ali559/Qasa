// The extension's UI language. There are no desktop prefs here: the first choice is the
// browser's UI language, mapped to en / ar / ckb; the popup's picker overrides it and is
// kept in storage.local (a UI preference, not vault data).
import type { Language } from '@/i18n'

export const LANGUAGE_KEY = 'language'

export function languageFromLocale(locale: string): Language {
  const tag = locale.toLowerCase()
  if (tag.startsWith('ckb') || tag.startsWith('ku')) return 'ckb'
  if (tag.startsWith('ar')) return 'ar'
  return 'en'
}

export async function loadLanguage(): Promise<Language> {
  try {
    const { [LANGUAGE_KEY]: saved } = await chrome.storage.local.get(LANGUAGE_KEY)
    if (saved === 'en' || saved === 'ar' || saved === 'ckb') return saved
  } catch {
    // Storage unavailable: fall through to the browser language.
  }
  return languageFromLocale(chrome.i18n.getUILanguage())
}
