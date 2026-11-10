<script setup lang="ts">
// Restore from a backup file: pick it (any file type), show whose and when, ask for the
// main password the vault had then, restore. Used during setup (RestoreSetup) and in
// Settings → Backups (RestoreBackupSheet). The file is read and kept by the core.
import { ArchiveRestore, FolderOpen, Laptop, Lock, Monitor, Smartphone } from 'lucide-vue-next'
import { onBeforeUnmount, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import TextField from '@/components/TextField.vue'
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

const ICON = { ios: Smartphone, android: Smartphone, macos: Laptop, windows: Monitor, linux: Monitor } as Record<string, unknown>

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
    <template v-if="!info">
      <PrimaryButton :title="t('backup.restore')" :icon="FolderOpen" :disabled="picking" @click="pick" />
    </template>

    <template v-else>
      <CardContainer>
        <button type="button" class="file" :disabled="busy" @click="pick">
          <span class="tile"><component :is="ICON[info.platform] ?? ArchiveRestore" :size="18" /></span>
          <span class="file-text">{{ t('backup.restore.fromFmt', info.deviceName, formatBackupDate(info.createdAtMs)) }}</span>
          <FolderOpen class="change" :size="16" aria-hidden="true" />
        </button>
      </CardContainer>

      <p class="muted">{{ t('backup.restore.passwordPrompt') }}</p>
      <div class="group">
        <SectionLabel :text="t('auth.unlock.masterPassword')" />
        <TextField
          v-model="password"
          :icon="Lock"
          :placeholder="t('auth.unlock.placeholder')"
          :label="t('auth.unlock.masterPassword')"
          secure
          revealable
          autofocus
          :disabled="busy"
          :invalid="errorKey === 'backup.error.wrongPassword'"
          @submit="restore"
        />
      </div>
    </template>

    <p v-if="errorKey" class="error" role="alert">{{ t(errorKey) }}</p>

    <PrimaryButton
      v-if="info"
      :title="busy ? t('backup.restore.working') : t('backup.restore.button')"
      :icon="ArchiveRestore"
      :disabled="!password || busy"
      @click="restore"
    />
  </div>
</template>

<style scoped>
.restore {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.file {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  text-align: start;
  cursor: pointer;
}

.tile {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--card-muted);
  color: var(--accent);
}

.file-text {
  flex: 1;
  min-width: 0;
}

.change {
  flex-shrink: 0;
  color: var(--text-secondary);
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.error {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--danger);
}
</style>
