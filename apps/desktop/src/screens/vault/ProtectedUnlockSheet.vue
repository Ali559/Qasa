<script setup lang="ts">
// iOS `ProtectedUnlockSheet` (design/README.md rule 6b): the app's own bottom sheet with
// the shared header (`vault.unlockProtectedNote`), the `protected.title` message, the main
// password as a one-row group (a wrong password is the error on its row), then Unlock as
// the bottom button. With Touch ID / Windows Hello on and sealed, the prompt starts by
// itself as the sheet opens and a secondary `auth.unlock.biometrics` button starts it
// again; the password always works. No Cancel: Esc or a click outside closes it (`close`).
import { computed, ref, watch } from 'vue'

import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import type { BiometricStatus } from '@/vault/types'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ unlocked: []; close: [] }>()
const t = useT()

const password = ref('')
const wrong = ref(false)
const checking = ref(false)
const bio = ref<BiometricStatus | null>(null)
const biometricsAvailable = computed(() => !!bio.value && bio.value.kind !== 'none' && bio.value.enabled && bio.value.sealed)

watch(password, () => (wrong.value = false))

watch(
  () => props.open,
  async (open) => {
    password.value = ''
    wrong.value = false
    if (!open) return
    bio.value = await vaultApi.biometricStatus().catch(() => null)
    if (props.open && biometricsAvailable.value) void askBiometrics()
  },
  { immediate: true },
)

async function askBiometrics() {
  try {
    // A dismissed prompt (or an invalidated seal) leaves the password to answer.
    if ((await vaultApi.verifyBiometric(t('vault.unlockProtectedNote'))) && props.open) emit('unlocked')
  } catch {
    // Nothing to show: the sheet stays, the password still works.
  }
}

async function checkPassword() {
  if (!password.value || checking.value) return
  checking.value = true
  try {
    if (await vaultApi.verifyPassword(password.value)) emit('unlocked')
    else wrong.value = true
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <Sheet :open="open" :label="t('vault.unlockProtectedNote')" :title="t('vault.unlockProtectedNote')" :width="420" placement="bottom" @close="emit('close')">
    <div class="unlock">
      <p class="message">{{ t('protected.title') }}</p>
      <FieldGroup>
        <FieldRow
          v-model="password"
          :label="t('auth.unlock.masterPassword')"
          :placeholder="t('auth.unlock.placeholder')"
          secure
          revealable
          autofocus
          :error="wrong ? t('auth.unlock.wrongPassword') : null"
          @submit="checkPassword"
        />
      </FieldGroup>
      <div class="actions">
        <PrimaryButton :title="t('auth.unlock.button')" :disabled="!password || checking" @click="checkPassword" />
        <PrimaryButton v-if="biometricsAvailable" kind="secondary" :title="t('auth.unlock.biometrics')" @click="askBiometrics" />
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
/* iOS: the message, the group and the buttons `space.sheetActionsGap` (20) apart. */
.unlock {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.message {
  margin: 0;
  text-align: center;
  font-size: var(--type-dialog-message-size);
  font-weight: var(--type-dialog-message-weight);
  color: var(--text-secondary);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}
</style>
