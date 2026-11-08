<script setup lang="ts">
// App state machine, as iOS `qasaApp.RootView` (docs/design.md §12.1):
// launching → welcome → create vault | unlock → shell, plus the store-error screen.
import { onMounted, ref } from 'vue'

import { resetNav } from '@/nav'
import { prefs } from '@/prefs'
import CreateVault from '@/screens/CreateVault.vue'
import Shell from '@/screens/Shell.vue'
import Unlock from '@/screens/Unlock.vue'
import WelcomePreferences from '@/screens/WelcomePreferences.vue'
import { attachAutoLock, loadVault, lockVault, refreshStatus, session } from '@/vault/session'

type Stage = 'launching' | 'welcome' | 'create' | 'unlock' | 'shell'
const stage = ref<Stage>('launching')

async function route() {
  try {
    const status = await refreshStatus()
    if (status.unlocked) {
      await loadVault()
      stage.value = 'shell'
    } else if (status.exists) {
      stage.value = 'unlock'
    } else {
      stage.value = prefs.hasCompletedWelcome ? 'create' : 'welcome'
    }
  } catch (e) {
    console.error('Could not read the vault status', e)
    stage.value = 'unlock'
  }
}

async function enterShell() {
  resetNav()
  await refreshStatus()
  await loadVault()
  stage.value = 'shell'
}

async function lock() {
  await lockVault()
  resetNav()
  await route()
}

onMounted(() => {
  attachAutoLock(() => {
    resetNav()
    route()
  })
  route()
})

defineExpose({ session })
</script>

<template>
  <div v-if="stage === 'launching'" class="launching" />
  <WelcomePreferences v-else-if="stage === 'welcome'" @continue="stage = 'create'" />
  <CreateVault v-else-if="stage === 'create'" @created="enterShell" />
  <Unlock v-else-if="stage === 'unlock'" @unlocked="enterShell" />
  <Shell v-else @lock="lock" />
</template>

<style scoped>
.launching {
  height: 100%;
  background: var(--background);
}
</style>
