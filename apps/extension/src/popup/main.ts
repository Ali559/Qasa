import { createApp } from 'vue'

import '@/theme/tokens.css'
import '@/theme/base.css'
import './popup.css'
import App from './App.vue'
import { initPrefs } from './prefs'

// Language first, so the first frame already has the right direction and font.
initPrefs().finally(() => createApp(App).mount('#app'))
