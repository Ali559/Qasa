import { createApp } from 'vue'

import App from './App.vue'
import { initPrefs } from './prefs'
import './theme/tokens.css'
import './theme/base.css'

// Prefs first, so the first frame already has the right language, direction and theme.
initPrefs().finally(() => createApp(App).mount('#app'))
