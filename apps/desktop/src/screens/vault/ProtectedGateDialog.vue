<script setup lang="ts">
// The main-password prompt for protected notes (see vault/protectedGate.ts). iOS asks the
// device owner through the system prompt; here it is a `confirmSheet`-style prompt sheet: an
// `IconBadge` (×1.2, 4 below), 19 semibold title and 15 message 10 apart, the field, then
// Unlock over Cancel 10 apart, 20 between blocks, 20 from the top.
import { ref, watch } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import QasaIcon from '@/components/QasaIcon.vue'
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
  <Sheet :open="protectedGate.open" :label="t('vault.unlockProtectedNote')" :width="420" placement="bottom" @close="finishProtectedAccess(false)">
    <div class="gate">
      <div class="text">
        <span class="badge" aria-hidden="true"><QasaIcon name="lock" :size="16" /></span>
        <h2>{{ t('vault.unlockProtectedNote') }}</h2>
        <p>{{ t('protected.title') }}</p>
      </div>
      <div class="field">
        <TextField
          v-model="password"
          :placeholder="t('auth.unlock.placeholder')"
          :label="t('auth.unlock.masterPassword')"
          secure
          revealable
          autofocus
          :invalid="error"
          @submit="confirm"
        />
        <p v-if="error" class="error" role="alert">{{ t('auth.unlock.wrongPassword') }}</p>
      </div>
      <div class="actions">
        <PrimaryButton :title="t('auth.unlock.button')" icon="unlock" :disabled="!password || busy" @click="confirm" />
        <PrimaryButton kind="secondary" :title="t('common.cancel')" @click="finishProtectedAccess(false)" />
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.gate {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: var(--space-sheet-content-top) var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sheet-text-gap);
  text-align: center;
}

/* iOS `IconBadge` scaled 1.2 with 4 below. */
.badge {
  display: grid;
  place-items: center;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  margin: 4px 0 8px;
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  transform: scale(1.2);
}

.badge svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

h2 {
  margin: 0;
  font-size: var(--type-dialog-title-size);
  font-weight: var(--type-dialog-title-weight);
}

p {
  margin: 0;
  font-size: var(--type-dialog-message-size);
  font-weight: var(--type-dialog-message-weight);
  color: var(--text-secondary);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

.error {
  color: var(--danger);
  font-size: var(--type-field-error-size);
  font-weight: var(--type-field-error-weight);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}
</style>
