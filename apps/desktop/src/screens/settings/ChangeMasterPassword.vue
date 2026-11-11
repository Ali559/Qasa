<script setup lang="ts">
// iOS `ChangeMasterPasswordView`: current, new and confirm fields behind one shared eye
// toggle, an inline 13pt error or success line, and the update button in the sheet's
// `BottomActionBar`. The Rust core
// re-encrypts and re-indexes every item; on success the sheet closes after 700 ms.
import { Eye, EyeOff, Lock } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'

import BottomActionBar from '@/components/BottomActionBar.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { isCommandError } from '@/vault/types'

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
    // Start in the first field.
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
  <Sheet :open="open" :label="t('settings.changeMasterPassword')" :title="t('settings.changeMasterPassword')" :width="520" placement="bottom" @close="close">
    <form ref="form" class="form" @submit.prevent="save">
      <div class="body">
        <p class="subtitle">{{ t('settings.changeMasterSubtitle') }}</p>

        <div v-for="field in fields" :key="field.key" class="field-group">
          <SectionLabel :text="field.title" />
          <TextField v-model="field.model.value" :icon="Lock" :placeholder="field.title" :secure="!passwordVisible">
            <template #trailing>
              <button
                type="button"
                class="reveal"
                :aria-label="passwordVisible ? t('a11y.hidePassword') : t('a11y.showPassword')"
                :aria-pressed="passwordVisible"
                @click.prevent="passwordVisible = !passwordVisible"
              >
                <EyeOff v-if="passwordVisible" :stroke-width="2.5" aria-hidden="true" />
                <Eye v-else :stroke-width="2.5" aria-hidden="true" />
              </button>
            </template>
          </TextField>
        </div>

        <p v-if="errorMessage" class="message error" role="alert">{{ errorMessage }}</p>
        <p v-if="didSucceed" class="message success" role="status">{{ t('settings.masterPasswordChanged') }}</p>
      </div>

      <BottomActionBar>
        <button type="submit" class="qasa-button" :disabled="!canSave">
          <span v-if="isSaving" class="spinner" aria-hidden="true" />
          <span>{{ t('settings.saveMasterPassword') }}</span>
        </button>
      </BottomActionBar>
    </form>
  </Sheet>
</template>

<style scoped>
.form {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
  overflow-y: auto;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

/* iOS `FieldIconButton`: 32 square, 16 semibold glyph, secondary. */
.reveal {
  display: grid;
  place-items: center;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.reveal svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.message {
  margin: 0;
  font-size: var(--type-row-value-size);
}

.error {
  color: var(--danger);
}

.success {
  color: var(--success);
}

.spinner {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border: 2px solid currentColor;
  border-inline-end-color: transparent;
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
