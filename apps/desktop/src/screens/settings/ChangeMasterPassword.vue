<script setup lang="ts">
// iOS `ChangeMasterPasswordView`: current, new and confirm fields behind one shared eye
// toggle, an inline error or success line, and the update button. The Rust core
// re-encrypts and re-indexes every item; on success the sheet closes after 700 ms.
import { Eye, EyeOff, Lock } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

import SectionLabel from '@/components/SectionLabel.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { isCommandError } from '@/vault/types'
import SheetHeader from './SheetHeader.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const t = useT()

const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordVisible = ref(false)
const isSaving = ref(false)
const errorMessage = ref<string | null>(null)
const didSucceed = ref(false)
const form = ref<HTMLFormElement | null>(null)
let closeTimer: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.open,
  (open) => {
    if (!open) return
    clearTimeout(closeTimer)
    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    passwordVisible.value = false
    errorMessage.value = null
    didSucceed.value = false
    // Sheet focuses its first control (the Cancel button); start in the first field instead.
    nextTick(() => requestAnimationFrame(() => form.value?.querySelector('input')?.focus()))
  },
  { immediate: true },
)

/** Swift `String.count`: characters, not UTF-16 units. */
const length = (s: string) => [...s].length

const canSave = computed(
  () =>
    currentPassword.value.length > 0 &&
    length(newPassword.value) >= 8 &&
    newPassword.value === confirmPassword.value &&
    newPassword.value !== currentPassword.value &&
    !isSaving.value &&
    !didSucceed.value,
)

const fields = computed(() => [
  { key: 'current', title: t('settings.currentMasterPassword'), model: currentPassword },
  { key: 'new', title: t('settings.newMasterPassword'), model: newPassword },
  { key: 'confirm', title: t('settings.confirmMasterPassword'), model: confirmPassword },
])

async function save() {
  if (!canSave.value) return
  errorMessage.value = null
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = t('settings.passwordMismatch')
    return
  }
  isSaving.value = true
  try {
    await vaultApi.changePassword(currentPassword.value, newPassword.value)
    didSucceed.value = true
    closeTimer = setTimeout(() => emit('close'), 700)
  } catch (e) {
    if (isCommandError(e) && e.code === 'wrongPassword') errorMessage.value = t('settings.wrongMasterPassword')
    else if (isCommandError(e) && e.code === 'weakPassword') errorMessage.value = t('settings.weakMasterPassword')
    else errorMessage.value = isCommandError(e) ? e.message : String(e)
  } finally {
    isSaving.value = false
  }
}

function close() {
  if (!isSaving.value) emit('close')
}
</script>

<template>
  <Sheet :open="open" :label="t('settings.changeMasterPassword')" :width="520" @close="close">
    <SheetHeader :title="t('settings.changeMasterPassword')" :cancel-label="t('settings.cancel')" @cancel="close" />
    <form ref="form" class="body" @submit.prevent="save">
      <p class="subtitle">{{ t('settings.changeMasterSubtitle') }}</p>

      <div v-for="field in fields" :key="field.key" class="field-group">
        <SectionLabel :text="field.title" />
        <TextField
          v-model="field.model.value"
          :icon="Lock"
          :placeholder="field.title"
          :secure="!passwordVisible"
        >
          <template #trailing>
            <button
              type="button"
              class="reveal"
              :aria-label="passwordVisible ? t('a11y.hidePassword') : t('a11y.showPassword')"
              :aria-pressed="passwordVisible"
              @click.prevent="passwordVisible = !passwordVisible"
            >
              <EyeOff v-if="passwordVisible" :size="18" aria-hidden="true" />
              <Eye v-else :size="18" aria-hidden="true" />
            </button>
          </template>
        </TextField>
      </div>

      <p v-if="errorMessage" class="message error" role="alert">{{ errorMessage }}</p>
      <p v-if="didSucceed" class="message success" role="status">{{ t('settings.masterPasswordChanged') }}</p>

      <button type="submit" class="save" :disabled="!canSave">
        <span v-if="isSaving" class="spinner" aria-hidden="true" />
        <span>{{ t('settings.saveMasterPassword') }}</span>
      </button>
    </form>
  </Sheet>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: var(--screen-padding);
  overflow-y: auto;
  background: var(--background);
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reveal {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.reveal:hover {
  background: var(--card-muted);
}

.message {
  margin: 0;
  font-size: 13px;
}

.error {
  color: var(--danger);
}

.success {
  color: var(--success);
}

.save {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 14px;
  border: 0;
  border-radius: 10px;
  background: var(--accent);
  color: var(--on-accent-chip);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.save:disabled {
  background: color-mix(in srgb, var(--accent) 35%, transparent);
  cursor: default;
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-inline-end-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}
</style>
