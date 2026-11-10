<script setup lang="ts">
// Settings → Backups: back up now, last backup, location, automatic backups, restore.
// One backup file per computer; each new backup replaces it. Free on every plan.
import { ArchiveRestore, DatabaseBackup, Folder } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import NavChevronRow from '@/components/NavChevronRow.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import { backupApi, backupErrorKey, formatBackupDate, FREQUENCIES, type BackupFrequency } from '@/backup/api'
import { backup, refreshBackup, setBackupStatus } from '@/backup/store'
import { useT } from '@/prefs'
import RestoreBackupSheet from './RestoreBackupSheet.vue'

defineEmits<{ back: [] }>()
const t = useT()

const status = computed(() => backup.status)
const working = ref(false)
/** The result of the last Back up now on this screen (a message key). */
const result = ref<{ key: string; ok: boolean } | null>(null)
const showRestore = ref(false)

onMounted(refreshBackup)

const running = computed(() => working.value || !!status.value?.running)

const lastLine = computed(() =>
  status.value?.lastBackupMs ? t('backup.lastBackupFmt', formatBackupDate(status.value.lastBackupMs)) : t('backup.never'),
)

async function backUpNow() {
  if (running.value) return
  working.value = true
  result.value = null
  try {
    const r = await backupApi.backUpNow()
    setBackupStatus(r.status)
    if (!r.cancelled) result.value = { key: 'backup.done', ok: true }
  } catch (e) {
    await refreshBackup()
    result.value = { key: backupErrorKey(e), ok: false }
  } finally {
    working.value = false
  }
}

async function chooseLocation() {
  try {
    setBackupStatus(await backupApi.chooseLocation())
  } catch (e) {
    result.value = { key: backupErrorKey(e), ok: false }
  }
}

const frequencyChoices = computed<Choice<BackupFrequency>[]>(() =>
  FREQUENCIES.map((f) => ({ value: f, label: t(`backup.freq.${f}`) })),
)

const frequency = computed<BackupFrequency>({
  get: () => status.value?.frequency ?? 'off',
  set: async (value) => {
    try {
      setBackupStatus(await backupApi.setFrequency(value))
    } catch (e) {
      console.error('Could not save the backup frequency', e)
    }
  },
})
</script>

<template>
  <PageFrame :title="t('backup.title')" back :gap="16" @back="$emit('back')">
    <p class="muted">{{ t('backup.intro') }}</p>

    <PrimaryButton
      :title="running ? t('backup.backingUp') : t('backup.backUpNow')"
      :icon="DatabaseBackup"
      :disabled="running"
      @click="backUpNow"
    />
    <div class="state">
      <span class="last">{{ lastLine }}</span>
      <span v-if="result" :class="result.ok ? 'ok' : 'error'" :role="result.ok ? 'status' : 'alert'">{{ t(result.key) }}</span>
      <span v-else-if="status?.failed" class="error" role="alert">{{ t('backup.failed') }}</span>
    </div>

    <section class="group">
      <SectionLabel :text="t('backup.locationTitle')" />
      <CardContainer>
        <NavChevronRow
          :icon="Folder"
          :title="status?.locationName ?? t('backup.locationNone')"
          :trailing-text="status?.location ? t('backup.changeLocation') : t('backup.chooseLocation')"
          :disabled="running"
          @click="chooseLocation"
        />
      </CardContainer>
      <p v-if="status?.location" class="path" :title="status.location"><span dir="ltr">{{ status.location }}</span></p>
      <p class="footer">{{ t('backup.locationHelp') }}</p>
    </section>

    <section class="group">
      <SectionLabel :text="t('backup.auto')" />
      <ChoiceList v-model="frequency" class="frequencies" :choices="frequencyChoices" :label="t('backup.auto')" />
      <p class="footer">{{ t('backup.autoHelp') }}</p>
    </section>

    <section class="group">
      <CardContainer>
        <NavChevronRow :icon="ArchiveRestore" :title="t('backup.restore')" aria-haspopup="dialog" @click="showRestore = true" />
      </CardContainer>
      <p class="footer">{{ t('backup.restoreHelp') }}</p>
    </section>

    <template #overlay>
      <RestoreBackupSheet :open="showRestore" @close="showRestore = false" />
    </template>
  </PageFrame>
</template>

<style scoped>
.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.state {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-inline: 4px;
  font-size: 13px;
}

.last {
  color: var(--text-secondary);
}

.ok {
  color: var(--success);
}

.error {
  font-weight: 500;
  color: var(--danger);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Same list rows as the auto-lock presets. */
.frequencies :deep(.choice) {
  padding-block: 12px;
}

.frequencies :deep(.choice-label) {
  font-size: 16px;
  font-weight: 400;
}

.path {
  margin: 0;
  padding-inline: 4px;
  overflow: hidden;
  font-size: 12px;
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: start;
}

.footer {
  margin: 0;
  padding-inline: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
