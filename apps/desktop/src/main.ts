import { createApp } from 'vue'

import App from './App.vue'
import { initPrefs } from './prefs'
import { attachSiteIcons } from './vault/siteIconsApi'
import './theme/tokens.css'
import './theme/base.css'

// Prefs first, so the first frame already has the right language, direction and theme.
initPrefs().finally(() => {
  attachSiteIcons()
  createApp(App).mount('#app')
})
