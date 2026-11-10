<script setup lang="ts">
// iOS `AppShellView` on desktop: the four bottom tabs become a sidebar with the same
// entries, icons and order (on the right in RTL). Pages open in the content area with a
// back button, and the sidebar stays.
import { CircleUser, House, KeyRound, Lock, Shield, ShieldAlert } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted } from 'vue'

import ToastHost from '@/components/ToastHost.vue'
import { back, nav, openPage, selectTab, type ShellTab } from '@/nav'
import { useT } from '@/prefs'
import Home from './Home.vue'
import Alerts from './Alerts.vue'
import You from './You.vue'
import VaultScreen from './vault/VaultScreen.vue'
import Collections from './tools/Collections.vue'
import Generator from './tools/Generator.vue'
import PasswordHealth from './tools/PasswordHealth.vue'
import Settings from './settings/Settings.vue'
import LinkedDevices from './settings/LinkedDevices.vue'
import Notifications from './tools/Notifications.vue'
import PremiumUpgrade from './premium/PremiumUpgrade.vue'
import ManageSubscription from './premium/ManageSubscription.vue'
import WifiSync from './sync/WifiSync.vue'
import BrowserExtension from './settings/BrowserExtension.vue'
import Backups from './settings/Backups.vue'

const emit = defineEmits<{ lock: []; logout: [] }>()
const t = useT()

const tabs = computed(() => [
  { id: 'home' as ShellTab, label: t('tab.home'), icon: House },
  { id: 'passwords' as ShellTab, label: t('tab.passwords'), icon: KeyRound },
  { id: 'alerts' as ShellTab, label: t('tab.alerts'), icon: ShieldAlert },
  { id: 'you' as ShellTab, label: t('tab.you'), icon: CircleUser },
])

// Desktop shortcuts (plan §5.2): ⌘/Ctrl+L lock, ⌘/Ctrl+G generator, ⌘/Ctrl+1…4 tabs.
function onKey(e: KeyboardEvent) {
  if (!(e.metaKey || e.ctrlKey) || e.altKey || e.shiftKey) return
  const key = e.key.toLowerCase()
  if (key === 'l') {
    e.preventDefault()
    emit('lock')
  } else if (key === 'g') {
    e.preventDefault()
    openPage('generator')
  } else if (['1', '2', '3', '4'].includes(key)) {
    e.preventDefault()
    selectTab(tabs.value[Number(key) - 1].id)
  }
}

onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="shell">
    <nav class="sidebar" :aria-label="t('nav.openMenu')">
      <div class="brand">
        <Shield :size="16" fill="currentColor" aria-hidden="true" />
        <span>{{ t('brand.name') }}</span>
      </div>
      <button
        v-for="tab in tabs"
        :key="tab.id"
        type="button"
        class="tab"
        :class="{ active: nav.tab === tab.id && !nav.page }"
        :aria-current="nav.tab === tab.id ? 'page' : undefined"
        @click="selectTab(tab.id)"
      >
        <component :is="tab.icon" :size="20" aria-hidden="true" />
        <span>{{ tab.label }}</span>
      </button>
      <span class="grow" />
      <button type="button" class="tab lock" @click="emit('lock')">
        <Lock :size="18" aria-hidden="true" />
        <span>{{ t('shell.lock') }}</span>
      </button>
    </nav>

    <main class="content">
      <Transition name="page">
        <Generator v-if="nav.page === 'generator'" key="generator" @back="back" />
        <PasswordHealth v-else-if="nav.page === 'passwordHealth'" key="health" @back="back" />
        <Collections v-else-if="nav.page === 'collections'" key="collections" @back="back" />
        <Settings v-else-if="nav.page === 'settings'" key="settings" @back="back" @lock="emit('lock')" />
        <Notifications v-else-if="nav.page === 'notifications'" key="notifications" @back="back" />
        <PremiumUpgrade v-else-if="nav.page === 'premiumUpgrade'" key="premium" @back="back" />
        <ManageSubscription v-else-if="nav.page === 'manageSubscription'" key="subscription" @back="back" />
        <LinkedDevices v-else-if="nav.page === 'linkedDevices'" key="devices" @back="back" @logout="emit('logout')" />
        <WifiSync v-else-if="nav.page === 'wifiSync'" key="wifi" @back="back" />
        <BrowserExtension v-else-if="nav.page === 'browserExtension'" key="extension" @back="back" />
        <Backups v-else-if="nav.page === 'backups'" key="backups" @back="back" />
        <Home v-else-if="nav.tab === 'home'" key="home" />
        <VaultScreen v-else-if="nav.tab === 'passwords'" key="passwords" />
        <Alerts v-else-if="nav.tab === 'alerts'" key="alerts" />
        <You v-else key="you" />
      </Transition>
    </main>
    <ToastHost />
  </div>
</template>

<style scoped>
.shell {
  display: flex;
  height: 100%;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 220px;
  flex-shrink: 0;
  padding: 0 12px 16px;
  border-inline-end: 1px solid var(--stroke);
  background: var(--chrome);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 64px;
  padding-inline: 8px;
  color: var(--accent);
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  font-size: 20px;
  font-weight: 800;
}

.tab {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 15px;
  font-weight: 500;
  text-align: start;
  cursor: pointer;
  transition:
    background-color var(--motion-fast),
    color var(--motion-fast);
}

.tab:hover {
  background: var(--card-muted);
  color: var(--text-primary);
}

.tab.active {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  font-weight: 600;
}

.grow {
  flex: 1;
}

.lock {
  font-size: 14px;
}

.content {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.content {
  position: relative;
}

/* Enter-only: the old screen goes at once (an "out-in" swap can stall if a click lands
   mid-animation). Pages slide in from the trailing edge over 220 ms, as on iOS. */
.page-enter-active {
  transition:
    opacity var(--motion-page) ease-out,
    transform var(--motion-page) ease-out;
}

.page-leave-active {
  display: none;
}

.page-enter-from {
  opacity: 0;
  transform: translateX(12px);
}

:root[dir='rtl'] .page-enter-from {
  transform: translateX(-12px);
}

</style>
