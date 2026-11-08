<script setup lang="ts">
// Find a device showing a code on this Wi‑Fi, type the code, sync. Used by Sync on this
// network (join) and by "I already have Qasa on another device" on Create vault.
import { Laptop, Monitor, Smartphone, Wifi } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { failureKey, syncApi, type Peer, type SyncEvent } from '@/sync/api'

const emit = defineEmits<{ done: [event: Extract<SyncEvent, { stage: 'done' }>] }>()
const t = useT()

const peers = ref<Peer[]>([])
const selected = ref<Peer | null>(null)
const code = ref('')
const busy = ref(false)
const error = ref<string | null>(null)
let unlisten: (() => void) | undefined

onMounted(async () => {
  unlisten = await syncApi.onPeers((list) => {
    peers.value = list
    if (selected.value && !list.some((p) => p.address === selected.value!.address)) selected.value = null
    if (!selected.value && list.length === 1) selected.value = list[0]
  })
  await syncApi.browseStart()
})

onBeforeUnmount(() => {
  unlisten?.()
  syncApi.stop()
})

const ICON = { ios: Smartphone, android: Smartphone, macos: Laptop, windows: Monitor, linux: Monitor } as Record<string, unknown>

async function sync() {
  if (!selected.value || code.value.replace(/\D/g, '').length !== 6 || busy.value) return
  busy.value = true
  error.value = null
  try {
    const result = await syncApi.join(selected.value.address, code.value.replace(/\D/g, ''))
    if (result.stage === 'done') emit('done', result)
    else if (result.stage === 'failed') {
      const f = failureKey(result)
      error.value = f.arg === undefined ? t(f.key) : t(f.key, f.arg)
    }
  } catch {
    error.value = t('sync.failed')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="join">
    <div class="peers" role="radiogroup" :aria-label="t('sync.joinTitle')">
      <p v-if="peers.length === 0" class="searching"><span class="spinner" />{{ t('sync.searching') }}</p>
      <button
        v-for="p in peers"
        :key="p.address"
        type="button"
        role="radio"
        class="peer"
        :class="{ selected: selected?.address === p.address }"
        :aria-checked="selected?.address === p.address"
        @click="selected = p"
      >
        <component :is="ICON[p.platform] ?? Wifi" :size="20" class="peer-icon" />
        <span class="peer-name">{{ p.name }}</span>
      </button>
    </div>
    <p v-if="peers.length === 0" class="hint">{{ t('sync.noPeers') }}</p>

    <TextField
      v-model="code"
      :placeholder="t('pair.codePlaceholder')"
      :label="t('pair.codeLabel')"
      mono
      :disabled="!selected"
      :invalid="!!error"
      @submit="sync"
    />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <PrimaryButton
      :title="busy ? t('sync.connecting') : t('sync.syncButton')"
      :icon="Wifi"
      :disabled="!selected || code.replace(/\D/g, '').length !== 6 || busy"
      @click="sync"
    />
  </div>
</template>

<style scoped>
.join {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.peers {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
}

.peer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  text-align: start;
  cursor: pointer;
}

.peer + .peer {
  border-top: 1px solid var(--stroke);
}

.peer.selected {
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.peer-icon {
  color: var(--accent);
}

.searching {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 16px;
  font-size: 14px;
  color: var(--text-secondary);
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid color-mix(in srgb, var(--accent) 25%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.error {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--danger);
}
</style>
