<script setup lang="ts">
// App state machine, as iOS `qasaApp.RootView` (docs/design.md §12.1):
// launching → welcome → create vault | unlock → shell, plus the store-error screen.
import { onMounted, ref } from 'vue'

import { accountApi } from '@/account/api'
import { bootstrapAccount, forgetAccount, startInboxPolling, stopInboxPolling } from '@/account/store'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { resetNav } from '@/nav'
import { prefs, useT } from '@/prefs'
import CreateVault from '@/screens/CreateVault.vue'
import Shell from '@/screens/Shell.vue'
import Unlock from '@/screens/Unlock.vue'
import WelcomePreferences from '@/screens/WelcomePreferences.vue'
import { protectedReason } from '@/vault/protectedGate'
import { attachAutoLock, loadVault, lockVault, refreshStatus, session } from '@/vault/session'
import { watchEffect } from 'vue'

type Stage = 'launching' | 'welcome' | 'create' | 'unlock' | 'shell'
const stage = ref<Stage>('launching')
const t = useT()
/** Logging this computer out also wipes its vault, so it asks like Clear local data. */
const confirmLogout = ref(false)
// The Touch ID / Hello prompt for protected notes speaks the UI language.
watchEffect(() => (protectedReason.text = t('vault.unlockProtectedNote')))

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
  // Account features load behind the vault and never block it (works offline).
  bootstrapAccount().then(startInboxPolling)
}

async function lock() {
  stopInboxPolling()
  await lockVault()
  resetNav()
  await route()
}

/** Linked devices → log out this computer: unlink it on the server, forget the account
 * and wipe the vault (desktop's equivalent of the phone's logout). */
async function logoutDevice() {
  confirmLogout.value = false
  stopInboxPolling()
  try {
    await accountApi.clearLocal()
  } finally {
    forgetAccount()
    resetNav()
    await route()
  }
}

onMounted(() => {
  attachAutoLock(() => {
    stopInboxPolling()
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
  <Shell v-else @lock="lock" @logout="confirmLogout = true" />
  <ConfirmDialog
    :open="confirmLogout"
    :title="t('settings.clearAlertTitle')"
    :message="t('settings.clearAlertMessage')"
    :confirm-label="t('settings.clear')"
    destructive
    @confirm="logoutDevice"
    @cancel="confirmLogout = false"
  />
</template>

<style scoped>
.launching {
  height: 100%;
  background: var(--background);
}
</style>
