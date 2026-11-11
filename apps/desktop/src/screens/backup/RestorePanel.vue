<script setup lang="ts">
// Restore from a backup file: pick it (any file type), show whose and when, ask for the
// main password the vault had then, restore. Used during setup (RestoreSetup) and in
// Settings → Backups (RestoreBackupSheet). The file is read and kept by the core.
import { ArchiveRestore, FileUp } from 'lucide-vue-next'
import { onBeforeUnmount, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import { backupApi, formatBackupDate, restoreErrorKey, type BackupFileInfo, type RestoreOutcome } from '@/backup/api'
import { useT } from '@/prefs'

const emit = defineEmits<{ restored: [outcome: RestoreOutcome] }>()
const t = useT()

const info = ref<BackupFileInfo | null>(null)
const password = ref('')
const picking = ref(false)
const busy = ref(false)
const errorKey = ref<string | null>(null)
let restored = false

async function pick() {
  if (picking.value || busy.value) return
  picking.value = true
  errorKey.value = null
  try {
    const picked = await backupApi.pickRestore()
    if (picked) {
      info.value = picked
      password.value = ''
    }
  } catch (e) {
    info.value = null
    errorKey.value = restoreErrorKey(e)
  } finally {
    picking.value = false
  }
}

async function restore() {
  if (!info.value || !password.value || busy.value) return
  busy.value = true
  errorKey.value = null
  try {
    const outcome = await backupApi.restore(password.value)
    restored = true
    password.value = ''
    emit('restored', outcome)
  } catch (e) {
    errorKey.value = restoreErrorKey(e)
  } finally {
    busy.value = false
  }
}

onBeforeUnmount(() => {
  password.value = ''
  if (!restored) backupApi.cancelRestore().catch(() => {})
})
</script>

<template>
  <div class="restore">
    <CardContainer v-if="info">
      <div class="card-body">
        <p class="from">{{ t('backup.restore.fromFmt', info.deviceName, formatBackupDate(info.createdAtMs)) }}</p>
        <p class="muted">{{ t('backup.restore.passwordPrompt') }}</p>
        <FieldGroup>
          <FieldRow
            v-model="password"
            :label="t('auth.unlock.masterPassword')"
            secure
            revealable
            autofocus
            :disabled="busy"
            :error="errorKey === 'backup.error.wrongPassword' ? t(errorKey) : null"
            @submit="restore"
          />
        </FieldGroup>
        <p v-if="busy" class="working" role="status"><span class="spinner" aria-hidden="true" />{{ t('backup.restore.working') }}</p>
        <button v-else type="button" class="qasa-button secondary" :disabled="picking" @click="pick">
          <span>{{ t('backup.restore') }}</span>
        </button>
      </div>
    </CardContainer>

    <p v-if="errorKey && errorKey !== 'backup.error.wrongPassword'" class="error" role="alert">{{ t(errorKey) }}</p>

    <PrimaryButton
      v-if="info"
      :title="busy ? t('backup.restore.working') : t('backup.restore.button')"
      :icon="busy ? undefined : ArchiveRestore"
      :disabled="!password || busy"
      @click="restore"
    />
    <PrimaryButton v-else :title="t('backup.restore')" :icon="FileUp" :disabled="picking" @click="pick" />
  </div>
</template>

<style scoped>
/* iOS `BackupRestoreView`: 16 between blocks; the password card stacks 12 apart. */
.restore {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

p {
  margin: 0;
}

.from {
  font-size: var(--type-field-size);
  font-weight: var(--type-button-weight);
  color: var(--text-primary);
}

.muted,
.working {
  font-size: 14px;
  color: var(--text-secondary);
}

.working {
  display: flex;
  align-items: center;
  gap: var(--space-button-icon-gap);
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.error {
  font-size: var(--type-row-value-size);
  font-weight: 500;
  color: var(--danger);
}
</style>
