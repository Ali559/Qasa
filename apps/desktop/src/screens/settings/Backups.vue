<script setup lang="ts">
// Settings → Backups: back up now, last backup, location, automatic backups, restore.
// One backup file per computer; each new backup replaces it. Free on every plan.
import { ArchiveRestore, DatabaseBackup, Folder, RotateCcw } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import SettingsSection from '@/components/SettingsSection.vue'
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
  FREQUENCIES.map((f) => ({ value: f, label: t(`backup.freq.${f}`), icon: RotateCcw })),
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
  <PageFrame :title="t('backup.title')" back :gap="24" @back="$emit('back')">
    <p class="muted">{{ t('backup.intro') }}</p>

    <!-- iOS `backUpCard`: badge 14 from the last-backup line, then the state 8 below. -->
    <CardContainer>
      <div class="state">
        <div class="last-row">
          <span class="badge" aria-hidden="true"><DatabaseBackup :stroke-width="2.5" /></span>
          <span class="last">{{ lastLine }}</span>
          <span v-if="running" class="spinner" aria-hidden="true" />
        </div>
        <span v-if="result && !running" :class="result.ok ? 'ok' : 'error'" :role="result.ok ? 'status' : 'alert'">{{ t(result.key) }}</span>
        <span v-else-if="status?.failed && !running" class="error" role="alert">{{ t('backup.failed') }}</span>
      </div>
    </CardContainer>

    <section class="group">
      <SettingsSection :title="t('backup.locationTitle')">
        <SettingsRow
          :icon="Folder"
          :title="status?.locationName ?? t('backup.locationNone')"
          :trailing-text="status?.location ? t('backup.changeLocation') : t('backup.chooseLocation')"
          :disabled="running"
          @click="chooseLocation"
        />
      </SettingsSection>
      <p v-if="status?.location" class="path" :title="status.location"><span dir="ltr">{{ status.location }}</span></p>
      <p class="footer">{{ t('backup.locationHelp') }}</p>
    </section>

    <section class="group">
      <span class="label"><SectionLabel :text="t('backup.auto')" /></span>
      <ChoiceList v-model="frequency" :choices="frequencyChoices" :label="t('backup.auto')" />
      <p class="footer">{{ t('backup.autoHelp') }}</p>
    </section>

    <section class="group">
      <SettingsSection>
        <SettingsRow :icon="ArchiveRestore" :title="t('backup.restore')" aria-haspopup="dialog" @click="showRestore = true" />
      </SettingsSection>
      <p class="footer">{{ t('backup.restoreHelp') }}</p>
    </section>

    <template #actions>
      <PrimaryButton
        :title="running ? t('backup.backingUp') : t('backup.backUpNow')"
        :icon="running ? undefined : DatabaseBackup"
        :disabled="running"
        @click="backUpNow"
      />
    </template>

    <template #overlay>
      <RestoreBackupSheet :open="showRestore" @close="showRestore = false" />
    </template>
  </PageFrame>
</template>

<style scoped>
p {
  margin: 0;
}

.muted {
  font-size: 14px;
  color: var(--text-secondary);
}

.state {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.last-row {
  display: flex;
  align-items: center;
  gap: var(--space-row-icon-gap);
}

/* iOS `IconBadge`. */
.badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.badge svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.last {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.spinner {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
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

.ok,
.error {
  font-size: var(--type-row-value-size);
  font-weight: 500;
}

.ok {
  color: var(--accent);
}

.error {
  color: var(--danger);
}

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-section-label-gap);
}

.label {
  padding-inline: 4px;
}

.path {
  padding-inline: 4px;
  overflow: hidden;
  font-size: var(--type-caption-size);
  color: var(--text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: start;
}

/* iOS `helpText`: 12 regular, secondary, 4 in. */
.footer {
  padding-inline: 4px;
  font-size: var(--type-caption-size);
  font-weight: var(--type-caption-weight);
  color: var(--text-secondary);
}
</style>
