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
    <template v-if="!premium">
      <p class="muted">{{ t('lan.requiresPremium') }}</p>
      <PrimaryButton :title="t('nav.upgrade')" :icon="Crown" @click="openPage('premiumUpgrade')" />
    </template>

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

      <CardContainer v-if="result" :padding="20">
        <h3 class="done-title">{{ t('sync.doneTitle', result.peerName) }}</h3>
        <p class="muted">{{ t('sync.doneBody', result.received, result.sent) }}</p>
      </CardContainer>

      <template v-if="mode === 'host'">
        <CardContainer v-if="hosting" :padding="24" class="code-card">
          <span class="label">{{ t('sync.hostTitle') }}</span>
          <output class="code" dir="ltr">{{ event?.stage === 'waiting' ? event.code.replace(/(\d{3})(\d{3})/, '$1 $2') : '··· ···' }}</output>
          <span class="muted small">{{ event?.stage === 'connecting' ? t('sync.connecting') : t('sync.waiting') }}</span>
          <span class="muted small">{{ t('sync.codeExpires', secondsLeft) }}</span>
          <p class="muted small center">{{ t('sync.hostBody') }}</p>
        </CardContainer>
        <p v-if="failure" class="error" role="alert">{{ failure }}</p>
        <PrimaryButton v-if="!hosting" :title="t('lan.startHost')" :icon="Wifi" @click="startHost" />
        <button v-else type="button" class="stop" @click="stop">{{ t('lan.stop') }}</button>
      </template>

      <template v-else>
        <p class="muted">{{ t('sync.joinBody') }}</p>
        <JoinPanel @done="finished" />
      </template>
    </template>
  </PageFrame>
</template>

<style scoped>
.title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.small {
  font-size: 13px;
}

.center {
  text-align: center;
}

.segmented {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 3px;
  border-radius: 10px;
  background: var(--card-muted);
}

.segmented button {
  padding: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.segmented button.on {
  background: var(--card-background);
  color: var(--text-primary);
  font-weight: 600;
  box-shadow: 0 1px 3px var(--shadow-color);
}

.code-card {
  align-items: center;
  gap: 8px;
  text-align: center;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.code {
  font-family: var(--font-mono);
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--accent);
  user-select: all;
}

.done-title {
  margin: 0 0 6px;
  font-size: 18px;
  font-weight: 600;
  color: var(--success);
}

.error {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--danger);
}

.stop {
  padding: 10px;
  border: 0;
  background: transparent;
  color: var(--danger);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
</style>
