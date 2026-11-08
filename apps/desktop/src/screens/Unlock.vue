<script setup lang="ts">
// iOS `LoginView`. The biometric row arrives in phase 3.
import { LockOpen, Lock, Shield } from 'lucide-vue-next'
import { computed, ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import StatusPill from '@/components/StatusPill.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { session } from '@/vault/session'
import { isCommandError } from '@/vault/types'

const emit = defineEmits<{ unlocked: [] }>()
const t = useT()

const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const canUnlock = computed(() => password.value.trim().length > 0 && !busy.value)

async function unlock() {
  if (!canUnlock.value) return
  const newer = session.status?.unsupportedKdfVersion
  if (newer) {
    error.value = t('error.vaultNewerKdf', newer)
    return
  }
  busy.value = true
  try {
    await vaultApi.unlock(password.value)
    password.value = ''
    error.value = null
    emit('unlocked')
  } catch (e) {
    if (isCommandError(e) && e.code === 'unsupportedKdf') error.value = t('error.vaultNewerKdf', '?')
    else error.value = t('auth.unlock.wrongPassword')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthFrame>
    <template #header-trailing><StatusPill :text="t('brand.localOffline')" /></template>

    <div class="content">
      <div class="intro">
        <h1 class="title">{{ t('auth.unlock.title') }}</h1>
        <p class="subtitle">{{ t('auth.unlock.subtitle') }}</p>
      </div>

      <div class="group">
        <SectionLabel :text="t('auth.unlock.masterPassword')" />
        <TextField
          v-model="password"
          :icon="Lock"
          :placeholder="t('auth.unlock.placeholder')"
          :label="t('auth.unlock.masterPassword')"
          secure
          revealable
          autofocus
          :invalid="!!error"
          @submit="unlock"
        />
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </div>

      <CardContainer muted class="local-only">
        <div class="lo-title">
          <Shield :size="16" class="accent" fill="currentColor" />
          <span>{{ t('auth.unlock.localOnly') }}</span>
        </div>
        <p class="lo-body">{{ t('auth.unlock.localOnlyBody') }}</p>
      </CardContainer>
    </div>

    <template #footer>
      <PrimaryButton
        :title="busy ? t('auth.securingVault') : t('auth.unlock.button')"
        :icon="LockOpen"
        :disabled="!canUnlock"
        @click="unlock"
      />
    </template>
  </AuthFrame>
</template>

<style scoped>
.content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.intro,
.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.error {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--danger);
}

.local-only {
  gap: 8px;
  margin-top: 8px;
}

.lo-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.accent {
  color: var(--accent);
}

.lo-body {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
