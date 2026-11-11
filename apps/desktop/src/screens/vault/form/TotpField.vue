<script setup lang="ts">
// iOS `TOTPEditorField`: "Add 2FA code" (enter a setup key, or — the desktop's stand-in
// for the camera — read the QR code from an image), then the issuer with a live code and
// Remove. The secret itself is never shown.
import { Image, Keyboard, Plus, ShieldCheck } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

import PromptSheet from './PromptSheet.vue'
import { useT } from '@/prefs'
import { isCommandError } from '@/vault/types'
import { toolsApi, type TotpCode, type TotpSetup } from '@/vault/tools'

const model = defineModel<string | undefined>()
const t = useT()
const error = ref<string | null>(null)
const showMenu = ref(false)
const showKey = ref(false)
const key = ref('')
const setup = ref<TotpSetup | null>(null)
const code = ref<TotpCode | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

async function refreshCode() {
  if (!model.value) return
  try {
    code.value = await toolsApi.totpPreview(model.value)
  } catch {
    code.value = null
  }
}

watch(
  model,
  async (uri) => {
    clearInterval(timer)
    code.value = null
    if (!uri) {
      setup.value = null
      return
    }
    try {
      setup.value = await toolsApi.totpParse(uri)
    } catch {
      setup.value = null
    }
    refreshCode()
    timer = setInterval(refreshCode, 1000)
  },
  { immediate: true },
)
onBeforeUnmount(() => clearInterval(timer))

async function applyKey() {
  try {
    model.value = (await toolsApi.totpParse(key.value)).uri
    error.value = null
    showKey.value = false
  } catch {
    error.value = t('totp.invalid')
    showKey.value = false
  }
}

async function fromImage() {
  showMenu.value = false
  try {
    const found = await toolsApi.totpFromImage()
    if (found) {
      model.value = found.uri
      error.value = null
    }
  } catch (e) {
    const message = isCommandError(e) ? e.message : ''
    error.value = message === 'noQr' ? t('totp.noQrInImage') : message === 'NotOtpAuth' ? t('totp.notQr') : t('totp.invalid')
  }
}

function openKey() {
  showMenu.value = false
  key.value = ''
  error.value = null
  showKey.value = true
}
</script>

<template>
  <div class="totp-field">
    <span class="label">{{ t('field.totp') }}</span>
    <template v-if="!model">
      <div class="add-wrap">
        <button type="button" class="qasa-button add" :aria-expanded="showMenu" @click="showMenu = !showMenu">
          <Plus :stroke-width="2.5" />
          <span>{{ t('totp.add') }}</span>
        </button>
        <div v-if="showMenu" class="menu" role="menu">
          <button type="button" role="menuitem" @click="fromImage"><Image :size="16" />{{ t('totp.scanImage') }}</button>
          <button type="button" role="menuitem" @click="openKey"><Keyboard :size="16" />{{ t('totp.enterKey') }}</button>
        </div>
      </div>
      <span class="hint">{{ t('totp.hint') }}</span>
    </template>
    <div v-else class="set">
      <ShieldCheck class="accent shield" :stroke-width="2.25" />
      <span class="text">
        <span class="issuer">{{ setup?.issuer ?? setup?.accountName ?? t('field.totp') }}</span>
        <span v-if="code" class="code">{{ code.grouped }}</span>
      </span>
      <button type="button" class="remove" @click="model = undefined">{{ t('totp.remove') }}</button>
    </div>
    <span v-if="error" class="error" role="alert">{{ error }}</span>

    <PromptSheet
      v-model="key"
      :open="showKey"
      :title="t('totp.enterKey')"
      :message="t('totp.hint')"
      :placeholder="t('totp.keyPlaceholder')"
      :confirm-label="t('totp.add')"
      :cancel-label="t('common.cancel')"
      mono
      @confirm="applyKey"
      @cancel="showKey = false"
    />
  </div>
</template>

<style scoped>
.totp-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

/* iOS `FormFieldLabel` = `SectionLabel`. */
.label {
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  color: var(--text-secondary);
}

.add-wrap {
  position: relative;
}

/* iOS: card fill, accent 40% stroke, accent 16 semibold title with a plus. */
.add {
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
  background: var(--card-background);
  color: var(--accent);
}

.menu {
  position: absolute;
  top: calc(100% + 4px);
  inset-inline: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  padding: 6px;
  border: var(--size-stroke) solid var(--stroke-chrome);
  border-radius: var(--radius-control);
  background: var(--chrome);
  box-shadow: 0 12px 32px var(--shadow-color);
}

.menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 0;
  border-radius: calc(var(--radius-control) - 4px);
  background: transparent;
  color: var(--text-primary);
  font-size: var(--type-body-size);
  text-align: start;
  cursor: pointer;
}

.menu button svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.menu button:hover {
  background: var(--card-muted);
}

.hint,
.error {
  font-size: var(--type-caption-size);
  font-weight: var(--type-caption-weight);
  color: var(--text-secondary);
}

.error {
  color: var(--danger);
}

/* iOS `setRow`: 14 padding, card radius 20, 1px stroke, 12 gap. */
.set {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.accent {
  color: var(--accent);
}

.shield {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
}

.text {
  display: flex;
  flex-direction: column;
  gap: var(--space-row-text-gap);
  flex: 1;
  min-width: 0;
}

.issuer {
  font-size: var(--type-row-title-size);
  font-weight: var(--type-row-title-weight);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.code {
  font-family: var(--font-mono);
  font-size: 15px;
  color: var(--text-secondary);
  direction: ltr;
  text-align: start;
}

.remove {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
</style>
