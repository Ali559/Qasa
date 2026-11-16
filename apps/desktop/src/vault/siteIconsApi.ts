// Connects the site icon store (`siteIcon.ts`) to the Rust core (`site_icons/queue.rs`):
// `site_icons_get` for the icons a screen draws, the `site-icon` event as each request
// finishes (the store is cleared at lock by `session.ts`). With "Website icons" off nothing is asked and every login shows its letter.
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { watch } from 'vue'

import { prefs } from '@/prefs'

import { clearSiteIcons, setSiteIcon, setSiteIconSource } from './siteIcon'

let attached = false

export function attachSiteIcons(): void {
  if (attached || !('__TAURI_INTERNALS__' in window)) return
  attached = true
  setSiteIconSource((hosts) =>
    prefs.siteIcons ? invoke<Record<string, string | null>>('site_icons_get', { hosts }) : Promise.resolve({}),
  )
  listen<{ host: string; icon: string | null }>('site-icon', (e) => {
    if (prefs.siteIcons) setSiteIcon(e.payload.host, e.payload.icon)
  }).catch((e) => console.error('Could not listen for site icons', e))
  watch(
    () => prefs.siteIcons,
    () => clearSiteIcons(),
  )
}
