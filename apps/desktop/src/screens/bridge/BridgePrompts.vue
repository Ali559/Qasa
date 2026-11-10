<script setup lang="ts">
// The browser extension's two prompts, shown over whatever screen is open (also the
// Unlock screen, for pairing): "Allow Chrome to use Qasa?" with the matching code, and
// "Save this login?" / "Update the saved password?" after a sign-in form was sent.
// Saves queue up and are shown one at a time; nothing is written until Save is clicked.
// Third: "Confirm to fill <card>", the master password for a card fill when Touch ID /
// Hello isn't set up (docs/autofill-plan.md §3.2).
import { Globe, KeyRound, Lock, User } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref, watchEffect } from 'vue'

import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { bridgeApi, type ConfirmPrompt, type PairPrompt, type SavePrompt } from '@/bridge/api'
import { useT } from '@/prefs'
import { showToast } from '@/toast'
import { loadVault, session } from '@/vault/session'

const t = useT()
const pair = ref<PairPrompt | null>(null)
const saves = ref<SavePrompt[]>([])
const busy = ref(false)
const confirm = ref<ConfirmPrompt | null>(null)
const confirmPassword = ref('')
const confirmError = ref('')
const unlisten: Array<() => void> = []

// The Touch ID / Hello prompts the browser starts speak the UI language.
watchEffect(() => void bridgeApi.setAuthReasons(t('bridge.unlockReason'), t('bridge.confirmReason')).catch(() => {}))

onMounted(async () => {
  unlisten.push(await bridgeApi.onPair((p) => (pair.value = p)))
  unlisten.push(await bridgeApi.onSave((p) => saves.value.push(p)))
  unlisten.push(
    await bridgeApi.onConfirm((p) => {
      confirm.value = p
      confirmPassword.value = ''
      confirmError.value = ''
    }),
  )
})

async function decideConfirm(accept: boolean) {
  const current = confirm.value
  if (!current || busy.value) return
  busy.value = true
  try {
    const answered = await bridgeApi.decideConfirm(current.id, accept ? confirmPassword.value : null)
    if (answered) confirm.value = null
    else confirmError.value = t('auth.unlock.wrongPassword')
  } catch (e) {
    console.error('Could not confirm the fill', e)
    confirm.value = null
  } finally {
    confirmPassword.value = ''
    busy.value = false
  }
}
onBeforeUnmount(() => unlisten.forEach((u) => u()))

async function decidePair(allow: boolean) {
  pair.value = null
  await bridgeApi.decidePair(allow)
}

async function decideSave(accept: boolean) {
  const current = saves.value[0]
  if (!current || busy.value) return
  busy.value = true
  try {
    const saved = await bridgeApi.decideSave(current.id, accept)
    if (saved) {
      showToast(t(current.kind === 'save' ? 'bridge.saved' : 'bridge.updated'))
      if (session.status?.unlocked) await loadVault()
    }
  } catch (e) {
    console.error('Could not save the login from the browser', e)
  } finally {
    saves.value.shift()
    busy.value = false
  }
}
</script>

<template>
  <Sheet :open="pair !== null" :label="pair ? t('bridge.pairTitle', pair.client) : ''" :width="400" @close="decidePair(false)">
    <div v-if="pair" class="prompt">
      <h2>{{ t('bridge.pairTitle', pair.client) }}</h2>
      <output class="code" dir="ltr">{{ pair.code.replace(/(\d{3})(\d{3})/, '$1 $2') }}</output>
      <p>{{ t('bridge.pairBody') }}</p>
      <div class="actions">
        <button type="button" class="secondary" @click="decidePair(false)">{{ t('bridge.deny') }}</button>
        <button type="button" class="primary" @click="decidePair(true)">{{ t('bridge.allow') }}</button>
      </div>
    </div>
  </Sheet>

  <Sheet
    :open="pair === null && saves.length > 0"
    :label="saves[0] ? t(saves[0].kind === 'save' ? 'bridge.saveTitle' : 'bridge.updateTitle') : ''"
    :width="420"
    @close="decideSave(false)"
  >
    <div v-if="saves[0]" class="prompt">
      <h2>{{ t(saves[0].kind === 'save' ? 'bridge.saveTitle' : 'bridge.updateTitle') }}</h2>
      <p>{{ t('bridge.saveBody', saves[0].client) }}</p>
      <dl class="fields">
        <div v-if="saves[0].itemTitle" class="field">
          <KeyRound :size="18" aria-hidden="true" />
          <dt>{{ t('vault.type.login') }}</dt>
          <dd>{{ saves[0].itemTitle }}</dd>
        </div>
        <div class="field">
          <Globe :size="18" aria-hidden="true" />
          <dt>{{ t('bridge.website') }}</dt>
          <dd dir="ltr">{{ saves[0].website }}</dd>
        </div>
        <div v-if="saves[0].username" class="field">
          <User :size="18" aria-hidden="true" />
          <dt>{{ t('bridge.username') }}</dt>
          <dd dir="ltr">{{ saves[0].username }}</dd>
        </div>
      </dl>
      <div class="actions">
        <button type="button" class="secondary" :disabled="busy" @click="decideSave(false)">{{ t('bridge.notNow') }}</button>
        <button type="button" class="primary" :disabled="busy" @click="decideSave(true)">
          {{ t(saves[0].kind === 'save' ? 'bridge.save' : 'bridge.update') }}
        </button>
      </div>
    </div>
  </Sheet>

  <Sheet
    :open="pair === null && confirm !== null"
    :label="confirm ? t('bridge.confirmTitle', confirm.itemTitle) : ''"
    :width="400"
    @close="decideConfirm(false)"
  >
    <div v-if="confirm" class="prompt">
      <h2>{{ t('bridge.confirmTitle', confirm.itemTitle) }}</h2>
      <p>{{ t('bridge.confirmBody', confirm.client) }}</p>
      <TextField
        v-model="confirmPassword"
        :icon="Lock"
        :placeholder="t('auth.unlock.placeholder')"
        :label="t('auth.unlock.masterPassword')"
        secure
        revealable
        autofocus
        :invalid="!!confirmError"
        @submit="decideConfirm(true)"
      />
      <p v-if="confirmError" class="error" role="alert">{{ confirmError }}</p>
      <div class="actions">
        <button type="button" class="secondary" :disabled="busy" @click="decideConfirm(false)">{{ t('common.cancel') }}</button>
        <button type="button" class="primary" :disabled="busy || !confirmPassword" @click="decideConfirm(true)">
          {{ t('bridge.confirm') }}
        </button>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.error {
  color: var(--danger);
}

.prompt {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 24px;
  text-align: center;
}

h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.code {
  margin: 6px 0;
  font-family: var(--font-mono);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--accent);
}

.fields {
  display: flex;
  flex-direction: column;
  margin: 8px 0 0;
  border: 1px solid var(--stroke);
  border-radius: 14px;
  background: var(--card-background);
  text-align: start;
}

.field {
  display: grid;
  grid-template-columns: 18px auto 1fr;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  color: var(--text-secondary);
}

.field + .field {
  border-top: 1px solid var(--stroke);
}

dt {
  font-size: 14px;
}

dd {
  overflow: hidden;
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  text-align: end;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.actions button {
  flex: 1;
  padding: 12px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.actions button:disabled {
  opacity: 0.6;
  cursor: default;
}

.secondary {
  border: 1px solid var(--stroke);
  background: var(--card-background);
  color: var(--text-primary);
}

.primary {
  border: 0;
  background: var(--accent);
  color: var(--on-accent);
}
</style>
