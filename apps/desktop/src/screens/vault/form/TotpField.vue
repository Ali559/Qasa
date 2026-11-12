<script setup lang="ts">
// iOS `TOTPEditorField` as the "2FA code" row of the login's `FieldGroup` (design/README.md
// rule 4). While empty the value line is "Add 2FA code", which opens an action sheet (rule
// 6a, iOS `qasaActionSheet`: enter a setup key, or — the desktop's stand-in for the camera —
// read the QR code from an image) with the help hint as
// a 12 secondary caption; once set, the live code with the issuer as the caption and a
// trailing Remove button. The secret itself is never shown.
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import ActionSheet from '@/components/ActionSheet.vue'
import FieldIconButton from '@/components/FieldIconButton.vue'
import GroupRow from '@/components/GroupRow.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import { rowId } from '@/components/fieldGroup'
import PromptSheet from './PromptSheet.vue'
import { useT } from '@/prefs'
import { isCommandError } from '@/vault/types'
import { toolsApi, type TotpCode, type TotpSetup } from '@/vault/tools'

/** The editor's field-rule message for the 2FA code, if any. */
const props = defineProps<{ error?: string | null }>()
const model = defineModel<string | undefined>()
const id = rowId('totp')
const t = useT()
const error = ref<string | null>(null)
const showMenu = ref(false)
const showKey = ref(false)
const key = ref('')
const setup = ref<TotpSetup | null>(null)
const code = ref<TotpCode | null>(null)
const rowError = computed(() => error.value ?? props.error)
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
  key.value = ''
  error.value = null
  showKey.value = true
}
</script>

<template>
  <GroupRow :id="id" :label="t('field.totp')" :error="rowError" :control="false">
    <button v-if="!model" type="button" class="add" aria-haspopup="dialog" @click="showMenu = true">
      <QasaIcon name="add" :size="16" />
      <span>{{ t('totp.add') }}</span>
    </button>
    <span v-else class="code" dir="ltr">{{ code?.grouped ?? '' }}</span>
    <template #below>
      <span v-if="!model" class="caption">{{ t('totp.hint') }}</span>
      <span v-else class="caption issuer">{{ setup?.issuer ?? setup?.accountName ?? t('field.totp') }}</span>
    </template>
    <template v-if="model" #trailing>
      <FieldIconButton icon="close" :label="t('totp.remove')" @click="model = undefined" />
    </template>
  </GroupRow>

  <ActionSheet
    v-model:open="showMenu"
    :title="t('totp.add')"
    :items="[
      { label: t('totp.scanImage'), icon: 'image', action: fromImage },
      { label: t('totp.enterKey'), icon: 'keyboard', action: openKey },
    ]"
  />
  <PromptSheet
    v-model="key"
    :open="showKey"
    :title="t('totp.enterKey')"
    :message="t('totp.hint')"
    :placeholder="t('totp.keyPlaceholder')"
    :confirm-label="t('totp.add')"
    mono
    @confirm="applyKey"
    @cancel="showKey = false"
  />
</template>

<style scoped>
/* The value line's action: 16 semibold accent with a plus, no chrome. */
.add {
  display: flex;
  align-items: center;
  gap: var(--space-button-icon-gap);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-weight: var(--type-button-weight);
  cursor: pointer;
}

.add svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

/* The live code, as the editor's value: mono, left to right in every language. */
.code {
  font-family: var(--font-mono);
  text-align: start;
}

.caption {
  font-size: var(--type-caption-size);
  font-weight: var(--type-caption-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
}

.issuer {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
