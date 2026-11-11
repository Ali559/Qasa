<script setup lang="ts">
// Settings → Backups → Restore: merges a backup into this vault (newest change wins, as
// Wi‑Fi sync), then reloads the list the same way Wi‑Fi sync does.
import { ref, watch } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import type { RestoreOutcome } from '@/backup/api'
import { useT } from '@/prefs'
import { loadVault } from '@/vault/session'
import RestorePanel from '../backup/RestorePanel.vue'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const t = useT()
const result = ref<RestoreOutcome | null>(null)

watch(
  () => props.open,
  (open) => {
    if (open) result.value = null
  },
)

async function restored(outcome: RestoreOutcome) {
  result.value = outcome
  await loadVault().catch((e) => console.error('Could not reload the vault', e))
}
</script>

<template>
  <Sheet :open="open" :label="t('backup.restore.title')" :title="t('backup.restore.title')" :width="520" placement="bottom" @close="emit('close')">
    <div class="body">
      <p class="muted">{{ t('backup.restoreHelp') }}</p>
      <template v-if="result">
        <p class="message" role="status">
          {{ result.written === 0 ? t('backup.restore.upToDate') : t('backup.restore.mergedFmt', result.written) }}
        </p>
        <PrimaryButton :title="t('pair.done')" icon="check" @click="emit('close')" />
      </template>
      <RestorePanel v-else @restored="restored" />
    </div>
  </Sheet>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
  overflow-y: auto;
}

p {
  margin: 0;
}

.muted {
  font-size: 14px;
  color: var(--text-secondary);
}

/* iOS `doneBody`: 18 bold, accent. */
.message {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}
</style>
