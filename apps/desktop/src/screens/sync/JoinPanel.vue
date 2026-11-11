<script setup lang="ts">
// iOS `WiFiJoinPanel`: one card with the 17 semibold title, the live list of devices showing
// a code (`SettingsRow`s with a selection circle), the code field and Sync, 12 apart.
// Find a device showing a code on this Wi‑Fi, type the code, sync. Used by Sync on this
// network (join) and by "I already have Qasa on another device" on Create vault.
import { Circle, CircleCheck, Laptop, Monitor, RefreshCw, Smartphone } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref, type Component } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
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

/** iOS `WiFiJoinPanel.icon(for:)`. */
const ICON = { ios: Smartphone, android: Smartphone, macos: Laptop, windows: Monitor, linux: Monitor } as Record<string, Component>


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
  <CardContainer>
    <div class="join">
      <h3 class="title">{{ t('sync.joinTitle') }}</h3>
      <template v-if="peers.length === 0">
        <p class="searching"><span class="spinner" aria-hidden="true" />{{ t('sync.searching') }}</p>
        <p class="hint">{{ t('sync.noPeers') }}</p>
      </template>
      <div v-else class="peers" role="radiogroup" :aria-label="t('sync.joinTitle')">
        <button
          v-for="p in peers"
          :key="p.address"
          type="button"
          role="radio"
          class="peer"
          :aria-checked="selected?.address === p.address"
          @click="selected = p"
        >
          <SettingsRow :icon="ICON[p.platform] ?? Monitor" :title="p.name">
            <template #trailing>
              <CircleCheck v-if="selected?.address === p.address" class="mark on" fill="currentColor" stroke="var(--card-background)" aria-hidden="true" />
              <Circle v-else class="mark" aria-hidden="true" />
            </template>
          </SettingsRow>
        </button>
      </div>

      <FieldGroup>
        <FieldRow v-model="code" :label="t('pair.codeLabel')" mono :disabled="!selected" :error="error" @submit="sync" />
      </FieldGroup>
      <PrimaryButton
        :title="busy ? t('sync.connecting') : t('sync.syncButton')"
        :icon="busy ? undefined : RefreshCw"
        :disabled="!selected || code.replace(/\D/g, '').length !== 6 || busy"
        @click="sync"
      />
    </div>
  </CardContainer>
</template>

<style scoped>
.join {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

p,
.title {
  margin: 0;
}

.title {
  font-size: var(--type-sheet-title-size);
  font-weight: var(--type-sheet-title-weight);
  color: var(--text-primary);
}

.peers {
  display: flex;
  flex-direction: column;
}

/* iOS `RowButtonStyle` around each `SettingsRow`, `RowDivider`s between them. */
.peer {
  position: relative;
  display: block;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: start;
  cursor: pointer;
}

.peer::after {
  content: '';
  position: absolute;
  inset-block: 0;
  inset-inline: -8px;
  border-radius: var(--radius-row-press);
  background: var(--text-primary);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion-press);
}

.peer:hover::after,
.peer:active::after {
  opacity: var(--motion-row-pressed-opacity);
}

.peer + .peer::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: calc(var(--size-icon-badge) + var(--space-row-icon-gap));
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
}

.peer:focus-visible {
  outline-offset: -2px;
}

.mark {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  color: var(--text-tertiary);
}

.mark.on {
  color: var(--accent);
}

.searching {
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

.hint {
  font-size: var(--type-caption-size);
  color: var(--text-secondary);
}

</style>
