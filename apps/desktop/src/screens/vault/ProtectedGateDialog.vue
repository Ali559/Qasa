<script setup lang="ts">
// The main-password prompt for protected notes (see vault/protectedGate.ts).
import { LockOpen, Lock } from 'lucide-vue-next'
import { ref, watch } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { finishProtectedAccess, protectedGate } from '@/vault/protectedGate'

const t = useT()
const password = ref('')
const error = ref(false)
const busy = ref(false)

watch(
  () => protectedGate.open,
  () => {
    password.value = ''
    error.value = false
  },
)

async function confirm() {
  if (!password.value || busy.value) return
  busy.value = true
  try {
    if (await vaultApi.verifyPassword(password.value)) finishProtectedAccess(true)
    else error.value = true
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Sheet :open="protectedGate.open" :label="t('vault.unlockProtectedNote')" :width="420" @close="finishProtectedAccess(false)">
    <div class="gate">
      <span class="icon"><Lock :size="22" /></span>
      <h2>{{ t('vault.unlockProtectedNote') }}</h2>
      <p>{{ t('protected.title') }}</p>
      <TextField
        v-model="password"
        :icon="Lock"
        :placeholder="t('auth.unlock.placeholder')"
        :label="t('auth.unlock.masterPassword')"
        secure
        revealable
        autofocus
        :invalid="error"
        @submit="confirm"
      />
      <p v-if="error" class="error" role="alert">{{ t('auth.unlock.wrongPassword') }}</p>
      <PrimaryButton :title="t('auth.unlock.button')" :icon="LockOpen" :disabled="!password || busy" @click="confirm" />
      <button type="button" class="cancel" @click="finishProtectedAccess(false)">{{ t('common.cancel') }}</button>
    </div>
  </Sheet>
</template>

<style scoped>
.gate {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 12px;
  padding: 24px;
  text-align: center;
}

.icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-inline: auto;
  border-radius: 50%;
  background: var(--card-muted);
  color: var(--accent);
}

h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.error {
  color: var(--danger);
  font-weight: 500;
}

.cancel {
  padding: 8px;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
</style>
