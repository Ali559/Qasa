<script setup lang="ts">
// iOS `TOTPEditorField`: "Add 2FA code" (enter a setup key, or — the desktop's stand-in
// for the camera — read the QR code from an image), then the issuer with a live code and
// Remove. The secret itself is never shown.
import { Image, Keyboard, Plus, ShieldCheck } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
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
        <button type="button" class="add" :aria-expanded="showMenu" @click="showMenu = !showMenu">
          <Plus :size="18" />
          {{ t('totp.add') }}
        </button>
        <div v-if="showMenu" class="menu" role="menu">
          <button type="button" role="menuitem" @click="fromImage"><Image :size="16" />{{ t('totp.scanImage') }}</button>
          <button type="button" role="menuitem" @click="openKey"><Keyboard :size="16" />{{ t('totp.enterKey') }}</button>
        </div>
      </div>
      <span class="hint">{{ t('totp.hint') }}</span>
    </template>
    <div v-else class="set">
      <ShieldCheck :size="18" class="accent" />
      <span class="text">
        <span class="issuer">{{ setup?.issuer ?? setup?.accountName ?? t('field.totp') }}</span>
        <span v-if="code" class="code">{{ code.grouped }}</span>
      </span>
      <button type="button" class="remove" @click="model = undefined">{{ t('totp.remove') }}</button>
    </div>
    <span v-if="error" class="error" role="alert">{{ error }}</span>

    <Sheet :open="showKey" :label="t('totp.enterKey')" :width="420" placement="bottom" @close="showKey = false">
      <div class="key-sheet">
        <h2>{{ t('totp.enterKey') }}</h2>
        <p>{{ t('totp.hint') }}</p>
        <TextField v-model="key" :placeholder="t('totp.keyPlaceholder')" mono autofocus @submit="applyKey" />
        <div class="actions">
          <button type="button" @click="showKey = false">{{ t('common.cancel') }}</button>
          <button type="button" class="primary" :disabled="!key.trim()" @click="applyKey">{{ t('totp.add') }}</button>
        </div>
      </div>
    </Sheet>
  </div>
</template>

<style scoped>
.totp-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.add-wrap {
  position: relative;
}

.add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  height: var(--button-height);
  padding: 0 16px;
  border: 1px solid color-mix(in srgb, var(--accent) 40%, transparent);
  border-radius: var(--button-radius);
  background: var(--card-background);
  color: var(--accent);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.menu {
  position: absolute;
  top: calc(100% + 4px);
  inset-inline: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  padding: 6px;
  border: 1px solid var(--stroke-chrome);
  border-radius: 14px;
  background: var(--chrome);
  box-shadow: 0 12px 32px var(--shadow-color);
}

.menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-primary);
  font-size: 14px;
  text-align: start;
  cursor: pointer;
}

.menu button:hover {
  background: var(--card-muted);
}

.hint {
  font-size: 12px;
  color: var(--text-secondary);
}

.set {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
}

.accent {
  color: var(--accent);
}

.text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.issuer {
  font-size: 15px;
  font-weight: 500;
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
  border: 0;
  background: transparent;
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.error {
  font-size: 12px;
  color: var(--danger);
}

.key-sheet {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 24px;
}

.key-sheet h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.key-sheet p {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  text-align: center;
}

.actions {
  display: flex;
  gap: 10px;
}

.actions button {
  flex: 1;
  height: var(--button-height);
  padding: 0 16px;
  border: 1px solid var(--stroke);
  border-radius: var(--button-radius);
  background: var(--card-background);
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.actions .primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--on-accent);
}

.actions .primary:disabled {
  opacity: 0.45;
}
</style>
