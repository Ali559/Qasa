<script setup lang="ts">
// Create vault → "Restore from a backup": this computer takes the backup's vault and
// account (as a device joining over Wi‑Fi sync does) and the core unlocks it with the
// password just typed. Continue then enters the app like any unlock, which registers this
// computer under the restored account.
import { Check, ChevronLeft } from 'lucide-vue-next'
import { ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import type { RestoreOutcome } from '@/backup/api'
import { useT } from '@/prefs'
import RestorePanel from './RestorePanel.vue'

const emit = defineEmits<{ back: []; restored: [] }>()
const t = useT()
const done = ref<RestoreOutcome | null>(null)
</script>

<template>
  <AuthFrame>
    <div class="content">
      <button v-if="!done" type="button" class="back" @click="emit('back')">
        <ChevronLeft class="mirror-rtl" :size="17" :stroke-width="2.75" />{{ t('common.back') }}
      </button>

      <h1 class="title">{{ t('backup.restore.title') }}</h1>
      <p class="muted">{{ t('backup.restoreSubtitle') }}</p>
      <RestorePanel v-if="!done" @restored="(o) => (done = o)" />
      <p v-else class="done" role="status">{{ t('backup.restore.doneFmt', done.itemsInBackup) }}</p>
    </div>

    <template #footer>
      <PrimaryButton v-if="done" :title="t('pair.done')" :icon="Check" @click="emit('restored')" />
    </template>
  </AuthFrame>
</template>

<style scoped>
.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
}

.back {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: var(--type-body-size);
  font-weight: var(--type-button-weight);
  cursor: pointer;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

/* iOS `doneBody`: 18 bold, accent. */
.done {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
}
</style>
