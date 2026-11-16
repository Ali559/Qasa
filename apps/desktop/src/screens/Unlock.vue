<script setup lang="ts">
// design/README.md rule 6d, "Unlocking the app" (option B, prompt first): a returning user
// never sees a login page. The window is the splash (the app icon centred, the wordmark
// under it, nothing of the vault). With Touch ID / Windows Hello on, sealed for this vault
// and the main password not due, the prompt starts by itself once the window has settled
// (~450 ms) and success opens the app. Cancelled or failed, or biometrics off or due, the
// unlock card rises over the splash and the icon moves up above it: rule 6b's sheet as a
// centred card (header, `protected.title`, the password as a one-row group with its error
// on the row, Unlock, and the secondary biometric button while it is offered). Nothing is
// behind it, so it can't be dismissed (Esc and clicks outside do nothing). The prompt
// starts again by itself when the window comes back (once each time).
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import SheetHeader from '@/components/SheetHeader.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { session } from '@/vault/session'
import { isCommandError, type BiometricStatus } from '@/vault/types'

const emit = defineEmits<{ unlocked: [] }>()
const t = useT()

/** How long the splash settles before the system prompt starts by itself. */
const SETTLE_MS = 450

const password = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const canUnlock = computed(() => password.value.trim().length > 0 && !busy.value)
const bio = ref<BiometricStatus | null>(null)
const bioReady = computed(() => !!bio.value && bio.value.kind !== 'none' && bio.value.enabled && bio.value.sealed)
/** Biometrics is set up but the main password is due: no button, no prompt, the note. */
const passwordDue = computed(() => bioReady.value && !!bio.value?.masterPasswordDue)
const showBiometric = computed(() => bioReady.value && !passwordDue.value)
/** The unlock card is up (it stays up once shown, until the vault opens). */
const sheetOpen = ref(false)
const card = ref<HTMLElement | null>(null)

/** Set when the window loses focus (not while our own prompt has it), so the next focus
 * prompts once. The system prompt itself blurs the window; that never re-arms it. */
let armed = false
let gone = false
let settle: ReturnType<typeof setTimeout> | undefined

async function refreshBio() {
  bio.value = await vaultApi.biometricStatus().catch(() => null)
}

async function openSheet() {
  if (sheetOpen.value || gone) return
  sheetOpen.value = true
  await nextTick()
  card.value?.querySelector<HTMLInputElement>('input')?.focus()
}

/** Starts the prompt by itself only when it would be offered as a button; otherwise the
 * card is the way in. */
function autoPrompt(delay = 0) {
  clearTimeout(settle)
  if (!showBiometric.value) {
    void openSheet()
    return
  }
  settle = setTimeout(() => {
    if (!busy.value && !gone) void unlockWithBiometrics()
  }, delay)
}

function onBlur() {
  if (!busy.value) armed = true
}

function onFocus() {
  if (!armed) return
  armed = false
  // The status may have changed while away (the interval ran out, or Settings turned it off).
  void refreshBio().then(() => autoPrompt(sheetOpen.value ? 0 : SETTLE_MS))
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
  if (gone) return
  // A window opened in the background prompts when it comes forward instead (the splash
  // waits; without biometrics the card is up straight away).
  if (document.hasFocus() || !showBiometric.value) autoPrompt(SETTLE_MS)
  else armed = true
})

onBeforeUnmount(() => {
  gone = true
  clearTimeout(settle)
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
    void openSheet()
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
  <div class="splash" :class="{ open: sheetOpen }">
    <Transition name="scrim"><div v-if="sheetOpen" class="scrim" aria-hidden="true" /></Transition>

    <TransitionGroup tag="div" name="rise" class="stack">
      <div key="mark" class="mark" role="img" :aria-label="t('brand.name')">
        <AppIcon :size="96" />
        <span class="wordmark" aria-hidden="true">{{ t('brand.name') }}</span>
      </div>

      <!-- Rule 6b's unlock sheet as a centred card; not dismissable. -->
      <section v-if="sheetOpen" key="card" ref="card" class="card" role="dialog" aria-modal="true" aria-labelledby="unlock-sheet-title">
        <SheetHeader id="unlock-sheet-title" :title="t('auth.unlock.sheetTitle')" />
        <div class="body">
          <p class="message">{{ t('protected.title') }}</p>
          <div class="group">
            <FieldGroup>
              <FieldRow
                v-model="password"
                :label="t('auth.unlock.masterPassword')"
                :placeholder="t('auth.unlock.placeholder')"
                secure
                revealable
                autofocus
                :error="error"
                @submit="unlock"
              />
            </FieldGroup>
            <p v-if="!error && passwordDue" class="due">
              <QasaIcon name="login" :size="13" class="due-icon" />
              <span>{{ t('auth.unlock.masterPasswordDueFmt', bio!.masterPasswordIntervalDays) }}</span>
            </p>
          </div>
          <div class="actions">
            <PrimaryButton
              :title="busy ? t('auth.securingVault') : t('auth.unlock.button')"
              icon="unlock"
              :disabled="!canUnlock"
              @click="unlock"
            />
            <PrimaryButton
              v-if="showBiometric"
              kind="secondary"
              :icon="bio?.kind === 'windowsHello' ? 'face' : 'fingerprint'"
              :title="t('auth.unlock.biometrics')"
              :disabled="busy"
              @click="unlockWithBiometrics"
            />
          </div>
        </div>
      </section>
    </TransitionGroup>
  </div>
</template>

<style scoped>
/* The splash: the page background, the app icon (96) and the wordmark centred. */
.splash {
  position: relative;
  height: 100%;
  overflow-y: auto;
  background: var(--background);
}

.scrim {
  position: fixed;
  inset: 0;
  z-index: 1;
  background: var(--scrim);
}

.stack {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24px;
  min-height: 100%;
  padding: var(--space-screen-padding);
}

.mark {
  position: relative;
  z-index: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.wordmark {
  font-size: 26px;
  font-weight: 700;
  line-height: var(--line-height);
  color: var(--accent);
}

/* Rule 6b's sheet, as the desktop's centred card: the page background, `--radius-sheet`.
   Nothing but the splash is behind it, so (unlike a sheet over a page) a 1px stroke and
   the elevation shadow keep its edge visible in both themes. */
.card {
  border: var(--size-stroke) solid var(--stroke);
  box-shadow: 0 10px 40px var(--shadow-color);
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  width: 100%;
  max-width: 420px;
  flex-shrink: 0;
  border-radius: var(--radius-sheet);
  background: var(--background);
  color: var(--text-primary);
  overflow: hidden;
}

/* As the protected-item sheet: the message, the group and the buttons 20 apart. */
.body {
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

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
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

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}

/* The icon moves up and the card rises in on `motion.standard`; none with reduce motion. */
.rise-move {
  transition: transform var(--motion-standard);
}

.rise-enter-active {
  transition:
    opacity var(--motion-standard),
    transform var(--motion-standard);
}

.rise-enter-from {
  opacity: 0;
  transform: translateY(24px);
}

.scrim-enter-active {
  transition: opacity var(--motion-standard);
}

.scrim-enter-from {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .rise-move,
  .rise-enter-active,
  .scrim-enter-active {
    transition: none;
  }
}
</style>
