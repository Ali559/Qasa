<script setup lang="ts">
// iOS `LANSyncView` ("Sync on Wi‑Fi"), now real: Premium only; "This device has the vault"
// shows a one-time code and waits; "Receive vault" finds the other device and takes the
// code. Both directions merge (newest change wins), so either side can host.
import { Crown, Wifi } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { account } from '@/account/store'
import { openPage } from '@/nav'
import { useT } from '@/prefs'
import { failureKey, syncApi, type SyncEvent } from '@/sync/api'
import { loadVault } from '@/vault/session'
import JoinPanel from './JoinPanel.vue'

defineEmits<{ back: [] }>()
const t = useT()

const mode = ref<'host' | 'join'>('host')
const event = ref<SyncEvent | null>(null)
const result = ref<Extract<SyncEvent, { stage: 'done' }> | null>(null)
const secondsLeft = ref(0)
const premium = computed(() => account.status?.isPremium ?? false)
let unlisten: (() => void) | undefined
let timer: ReturnType<typeof setInterval> | undefined

onMounted(async () => {
  unlisten = await syncApi.onEvent(async (e) => {
    event.value = e
    if (e.stage === 'waiting') {
      secondsLeft.value = e.expiresIn
      clearInterval(timer)
      timer = setInterval(() => (secondsLeft.value = Math.max(0, secondsLeft.value - 1)), 1000)
    }
    if (e.stage === 'done') await finished(e)
    if (e.stage === 'done' || e.stage === 'stopped' || (e.stage === 'failed' && e.attemptsLeft === 0)) clearInterval(timer)
  })
})

onBeforeUnmount(() => {
  unlisten?.()
  clearInterval(timer)
  syncApi.stop()
})

async function startHost() {
  result.value = null
  event.value = null
  await syncApi.hostStart()
}

async function stop() {
  await syncApi.stop()
  event.value = null
  clearInterval(timer)
}

async function finished(e: Extract<SyncEvent, { stage: 'done' }>) {
  result.value = e
  await loadVault()
}

function switchMode(m: 'host' | 'join') {
  stop()
  result.value = null
  mode.value = m
}

const hosting = computed(() => event.value?.stage === 'waiting' || event.value?.stage === 'connecting')
const failure = computed(() => {
  const e = event.value
  if (e?.stage !== 'failed') return null
  const f = failureKey(e)
  return f.arg === undefined ? t(f.key) : t(f.key, f.arg)
})
</script>

<template>
  <PageFrame :title="t('lan.title')" back :gap="16" @back="$emit('back')">
    <p v-if="!premium" class="intro">{{ t('lan.requiresPremium') }}</p>

    <template v-else>
      <h2 class="title">{{ t('lan.secureTitle') }}</h2>
      <p class="muted">{{ t('lan.secureBody') }}</p>

      <div class="segmented" role="tablist" :aria-label="t('common.mode')">
        <button type="button" role="tab" :aria-selected="mode === 'host'" :class="{ on: mode === 'host' }" @click="switchMode('host')">
          {{ t('lan.hostMode') }}
        </button>
        <button type="button" role="tab" :aria-selected="mode === 'join'" :class="{ on: mode === 'join' }" @click="switchMode('join')">
          {{ t('lan.joinMode') }}
        </button>
      </div>

      <!-- iOS `WiFiHostPanel`: one card, 12 apart. -->
      <CardContainer v-if="mode === 'host'">
        <div class="host">
          <h3 class="panel-title">{{ t('sync.hostTitle') }}</h3>
          <p class="muted">{{ t('sync.hostBody') }}</p>
          <template v-if="hosting">
            <output class="code" dir="ltr">{{ event?.stage === 'waiting' ? event.code.replace(/(\d{3})(\d{3})/, '$1 $2') : '··· ···' }}</output>
            <span class="expires">{{ t('sync.codeExpires', secondsLeft) }}</span>
            <span class="status"><span class="spinner" aria-hidden="true" />{{ event?.stage === 'connecting' ? t('sync.connecting') : t('sync.waiting') }}</span>
            <button type="button" class="qasa-button secondary" @click="stop"><span>{{ t('lan.stop') }}</span></button>
          </template>
          <PrimaryButton v-else :title="t('lan.startHost')" :icon="Wifi" @click="startHost" />
        </div>
      </CardContainer>
      <JoinPanel v-else @done="finished" />

      <!-- iOS `SyncOutcomeView`. -->
      <div v-if="result" class="outcome" role="status">
        <h3 class="done-title">{{ t('sync.doneTitle', result.peerName) }}</h3>
        <p class="muted">{{ t('sync.doneBody', result.received, result.sent) }}</p>
      </div>
      <p v-else-if="failure && mode === 'host'" class="error" role="alert">{{ failure }}</p>
    </template>

    <template v-if="!premium" #actions>
      <PrimaryButton :title="t('nav.upgrade')" :icon="Crown" @click="openPage('premiumUpgrade')" />
    </template>
  </PageFrame>
</template>

<style scoped>
p,
h2,
h3 {
  margin: 0;
}

.intro {
  font-size: var(--type-body-size);
  color: var(--text-secondary);
}

.title {
  font-size: 22px;
  font-weight: 700;
}

.muted {
  font-size: 14px;
  color: var(--text-secondary);
}

/* The iOS segmented `Picker`: 32 tall, a raised card-filled thumb on a muted track. */
.segmented {
  display: grid;
  grid-template-columns: 1fr 1fr;
  height: 32px;
  padding: 2px;
  border-radius: 9px;
  background: var(--card-muted);
}

.segmented button {
  padding: 0 8px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--text-primary);
  font-size: var(--type-row-value-size);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--motion-fast);
}

.segmented button.on {
  background: var(--card-background);
  font-weight: 600;
  box-shadow: 0 3px 8px rgb(0 0 0 / 12%), 0 3px 1px rgb(0 0 0 / 4%);
}

.host {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-title {
  font-size: var(--type-sheet-title-size);
  font-weight: var(--type-sheet-title-weight);
  color: var(--text-primary);
}

/* iOS `kasoMono(44, .bold)`, accent, 8 above and below. */
.code {
  padding-block: 8px;
  font-family: var(--font-mono);
  font-size: 44px;
  font-weight: 700;
  color: var(--accent);
  text-align: center;
  user-select: all;
}

/* `Font.qasaLabel(12)`: 13 medium. */
.expires {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  color: var(--text-secondary);
}

.status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.spinner {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  border: 2px solid var(--text-tertiary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.outcome {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.done-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}

.error {
  font-size: var(--type-row-value-size);
  font-weight: 500;
  color: var(--danger);
}
</style>
