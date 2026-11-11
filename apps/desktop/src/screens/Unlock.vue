<script setup lang="ts">
// iOS `LoginView`: main password, and the fingerprint / Hello row when it is turned on and
// sealed for this vault. With biometrics on, the prompt starts by itself (once each time the
// screen appears or the window comes back), unless the main password is due again: then
// only the password is offered, and the screen says why.
import { Fingerprint, KeyRound, LockOpen, ScanFace, Shield } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import StatusPill from '@/components/StatusPill.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { session } from '@/vault/session'
import { isCommandError, type BiometricStatus } from '@/vault/types'

const emit = defineEmits<{ unlocked: [] }>()
const t = useT()

const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const canUnlock = computed(() => password.value.trim().length > 0 && !busy.value)
const bio = ref<BiometricStatus | null>(null)
const bioReady = computed(() => !!bio.value && bio.value.kind !== 'none' && bio.value.enabled && bio.value.sealed)
/** Biometrics is set up but the main password is due: no button, no prompt. */
const passwordDue = computed(() => bioReady.value && !!bio.value?.masterPasswordDue)
const showBiometric = computed(() => bioReady.value && !passwordDue.value)

/** Set when the window loses focus (not while our own prompt has it), so the next focus
 * prompts once. The system prompt itself blurs the window; that never re-arms it. */
let armed = false
let gone = false

async function refreshBio() {
  bio.value = await vaultApi.biometricStatus().catch(() => null)
}

/** Starts the prompt by itself only when it would be offered as a button. */
function autoPrompt() {
  if (showBiometric.value && !busy.value && !gone) void unlockWithBiometrics()
}

function onBlur() {
  if (!busy.value) armed = true
}

function onFocus() {
  if (!armed) return
  armed = false
  // The status may have changed while away (the interval ran out, or Settings turned it off).
  void refreshBio().then(autoPrompt)
}

function onVisibility() {
  if (document.visibilityState === 'hidden') onBlur()
  else onFocus()
}

onMounted(async () => {
  window.addEventListener('blur', onBlur)
  window.addEventListener('focus', onFocus)
  document.addEventListener('visibilitychange', onVisibility)
  await refreshBio()
  // A window opened in the background prompts when it comes forward instead.
  if (document.hasFocus()) autoPrompt()
  else armed = true
})

onBeforeUnmount(() => {
  gone = true
  window.removeEventListener('blur', onBlur)
  window.removeEventListener('focus', onFocus)
  document.removeEventListener('visibilitychange', onVisibility)
})

async function unlockWithBiometrics() {
  if (busy.value) return
  busy.value = true
  try {
    await vaultApi.unlockBiometric(t('auth.unlock.biometricsHint'))
    error.value = null
    emit('unlocked')
  } catch (e) {
    // Dismissing the prompt is not an error, and a due password is shown by the note
    // (after the refresh below); anything else falls back to the password.
    const quiet = isCommandError(e) && (e.code === 'cancelled' || e.code === 'masterPasswordDue')
    if (!quiet) error.value = t('auth.unlock.biometricsFailed')
    await refreshBio()
  } finally {
    busy.value = false
  }
}

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
        <FieldGroup>
          <FieldRow v-model="password" :label="t('auth.unlock.masterPassword')" secure revealable autofocus :error="error" @submit="unlock" />
        </FieldGroup>
        <p v-if="!error && passwordDue" class="due">
          <KeyRound :stroke-width="2.75" class="due-icon" aria-hidden="true" />
          <span>{{ t('auth.unlock.masterPasswordDueFmt', bio!.masterPasswordIntervalDays) }}</span>
        </p>
      </div>

      <!-- iOS: a `SettingsRow` with a chevron in a stroked card, 14 in, `PressableStyle`. -->
      <div v-if="showBiometric" class="bio">
        <SettingsRow
          :icon="bio?.kind === 'windowsHello' ? ScanFace : Fingerprint"
          :title="t('auth.unlock.biometrics')"
          :subtitle="t('auth.unlock.biometricsHint')"
          :disabled="busy"
          @click="unlockWithBiometrics"
        />
      </div>

      <CardContainer muted class="local-only">
        <div class="lo-title">
          <Shield :size="17" class="accent" fill="currentColor" aria-hidden="true" />
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
  gap: var(--space-form-gap);
}

.intro,
.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.due {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  padding-top: 2px;
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  color: var(--text-secondary);
}

.due-icon {
  flex-shrink: 0;
  width: 13px;
  height: calc(var(--type-row-value-size) * var(--line-height));
  color: var(--accent);
}

.bio {
  margin-top: 4px;
  padding-inline: var(--space-section-inset-horizontal);
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.bio:has(.settings-row:active:not(:disabled)) {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.bio :deep(.settings-row::after) {
  display: none;
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
  flex-shrink: 0;
  color: var(--accent);
}

.lo-body {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}
</style>
