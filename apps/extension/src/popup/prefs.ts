// The popup's stand-in for the desktop's `@/prefs` (aliased in scripts/aliases.ts), so the
// desktop components that call `useT()` work unchanged. Only the language is a preference
// here; the theme follows the system.
import { computed, reactive, watch } from 'vue'

import { isRtl, translate, type Language } from '@/i18n'
import { LANGUAGE_KEY, loadLanguage } from '@ext/shared/language'

export const prefs = reactive<{ language: Language }>({ language: 'en' })

function apply(language: Language): void {
  const root = document.documentElement
  root.lang = language
  root.dir = isRtl(language) ? 'rtl' : 'ltr'
}

export async function initPrefs(): Promise<void> {
  prefs.language = await loadLanguage()
  apply(prefs.language)
  watch(
    () => prefs.language,
    (language) => {
      apply(language)
      chrome.storage.local.set({ [LANGUAGE_KEY]: language }).catch(() => undefined)
    },
  )
}

/** `t('key', …args)` in the current language; re-renders when the language changes. */
export function useT() {
  const language = computed(() => prefs.language)
  return (key: string, ...args: Array<string | number>) => translate(language.value, key, ...args)
}
