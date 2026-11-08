// Preferences shared by every screen: language, theme, and whether Welcome was done.
// Saved by the Rust core (src-tauri/src/prefs.rs). In a plain browser (UI work with
// `bun run dev`) they fall back to localStorage so screens still render.
import { invoke } from '@tauri-apps/api/core'
import { computed, reactive, watch } from 'vue'

import { isRtl, translate, type Language } from './i18n'

export type ThemePreference = 'system' | 'light' | 'dark'

export interface Prefs {
  language: Language
  theme: ThemePreference
  hasCompletedWelcome: boolean
}

const DEFAULTS: Prefs = { language: 'en', theme: 'system', hasCompletedWelcome: false }
const BROWSER_KEY = 'qasa.prefs'
const inTauri = '__TAURI_INTERNALS__' in window

export const prefs = reactive<Prefs>({ ...DEFAULTS })

async function load(): Promise<Prefs> {
  if (inTauri) return { ...DEFAULTS, ...(await invoke<Prefs>('get_prefs')) }
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(BROWSER_KEY) ?? '{}') }
  } catch {
    return { ...DEFAULTS }
  }
}

async function save(value: Prefs): Promise<void> {
  if (inTauri) {
    await invoke('set_prefs', { prefs: value })
    return
  }
  try {
    localStorage.setItem(BROWSER_KEY, JSON.stringify(value))
  } catch {
    // Storage can be unavailable (private window); prefs then last for this session only.
  }
}

/** Puts language, direction and theme on <html>, which every token and rule reads. */
function apply(value: Prefs): void {
  const root = document.documentElement
  root.lang = value.language
  root.dir = isRtl(value.language) ? 'rtl' : 'ltr'
  if (value.theme === 'system') root.removeAttribute('data-theme')
  else root.dataset.theme = value.theme
}

/** Loads the saved prefs and applies them before the first screen renders. */
export async function initPrefs(): Promise<void> {
  try {
    Object.assign(prefs, await load())
  } catch (error) {
    console.error('Could not load preferences', error)
  }
  apply(prefs)
  watch(prefs, (value) => {
    apply(value)
    save({ ...value }).catch((error) => console.error('Could not save preferences', error))
  })
}

/** `t('key', …args)` in the current language; re-renders when the language changes. */
export function useT() {
  const language = computed(() => prefs.language)
  return (key: string, ...args: Array<string | number>) => translate(language.value, key, ...args)
}
