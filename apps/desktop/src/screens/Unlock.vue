<script setup lang="ts">
// iOS `LoginView`: main password, and the fingerprint / Hello row when it is turned on and
// sealed for this vault. With biometrics on, the prompt starts by itself (once each time the
// screen appears or the window comes back), unless the main password is due again: then
// only the password is offered, and the screen says why.
import { ChevronRight, Fingerprint, KeyRound, LockOpen, Lock, ScanFace, Shield } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import StatusPill from '@/components/StatusPill.vue'
import TextField from '@/components/TextField.vue'
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
        <p v-else-if="passwordDue" class="due">
          <KeyRound :size="16" class="due-icon" aria-hidden="true" />
          <span>{{ t('auth.unlock.masterPasswordDueFmt', bio!.masterPasswordIntervalDays) }}</span>
        </p>
      </div>

      <button v-if="showBiometric" type="button" class="bio" :disabled="busy" @click="unlockWithBiometrics">
        <span class="bio-icon">
          <ScanFace v-if="bio?.kind === 'windowsHello'" :size="22" />
          <Fingerprint v-else :size="22" />
        </span>
        <span class="bio-text">
          <span class="bio-title">{{ t('auth.unlock.biometrics') }}</span>
          <span class="bio-hint">{{ t('auth.unlock.biometricsHint') }}</span>
        </span>
        <ChevronRight class="mirror-rtl chevron" :size="16" />
      </button>

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

.due {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.due-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--accent);
}

.bio {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 4px;
  padding: 16px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
  text-align: start;
  cursor: pointer;
}

.bio:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--stroke));
}

.bio-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--card-background-light);
  color: var(--accent);
}

.bio-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.bio-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.bio-hint {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.chevron {
  color: var(--text-tertiary);
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
