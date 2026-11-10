<script setup lang="ts">
// Settings → Backups → Restore: merges a backup into this vault (newest change wins, as
// Wi‑Fi sync), then reloads the list the same way Wi‑Fi sync does.
import { ref, watch } from 'vue'

import Sheet from '@/components/Sheet.vue'
import type { RestoreOutcome } from '@/backup/api'
import { useT } from '@/prefs'
import { loadVault } from '@/vault/session'
import RestorePanel from '../backup/RestorePanel.vue'
import SheetHeader from './SheetHeader.vue'

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
  <Sheet :open="open" :label="t('backup.restore.title')" :width="520" @close="emit('close')">
    <SheetHeader
      :title="t('backup.restore.title')"
      :cancel-label="result ? undefined : t('common.cancel')"
      :done-label="result ? t('settings.done') : undefined"
      @cancel="emit('close')"
      @done="emit('close')"
    />
    <div class="body">
      <p v-if="result" class="message" role="status">
        {{ result.written === 0 ? t('backup.restore.upToDate') : t('backup.restore.mergedFmt', result.written) }}
      </p>
      <template v-else>
        <p class="muted">{{ t('backup.restoreHelp') }}</p>
        <RestorePanel @restored="restored" />
      </template>
    </div>
  </Sheet>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: var(--screen-padding);
  overflow-y: auto;
  background: var(--background);
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.message {
  margin: 0;
  font-size: 15px;
  color: var(--success);
}
</style>
