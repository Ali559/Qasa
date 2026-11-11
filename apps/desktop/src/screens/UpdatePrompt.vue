<script setup lang="ts">
// "Qasa 1.2 is available": shown once a check finds an update, until Later. Installing
// restarts the app, which locks the vault, so the prompt says so. Laid out as iOS
// `ConfirmSheetContent`: content 20 from the top, a 19 semibold centered title and 15
// message 10 apart, then 20 below the Install button with Later under it, 10 apart.
import Sheet from '@/components/Sheet.vue'
import { useT } from '@/prefs'
import { installUpdate, updater } from '@/updater'

const t = useT()
const open = () => (updater.stage === 'available' && !updater.dismissed) || updater.stage === 'downloading'
</script>

<template>
  <Sheet :open="open()" :label="t('update.availableTitle', updater.next)" :width="400" placement="bottom" @close="updater.stage !== 'downloading' && (updater.dismissed = true)">
    <div class="prompt">
      <div class="text">
        <h2>{{ t('update.availableTitle', updater.next) }}</h2>
        <p>{{ updater.stage === 'downloading' ? t('update.installing') : t('update.availableBody') }}</p>
      </div>
      <progress v-if="updater.stage === 'downloading'" :value="updater.progress ?? undefined" max="1" />
      <div v-else class="actions">
        <button type="button" class="qasa-button" @click="installUpdate"><span>{{ t('update.install') }}</span></button>
        <button type="button" class="qasa-button secondary" @click="updater.dismissed = true"><span>{{ t('update.later') }}</span></button>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.prompt {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: var(--space-sheet-content-top) var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.text {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-text-gap);
  text-align: center;
}

h2 {
  margin: 0;
  font-size: var(--type-dialog-title-size);
  font-weight: var(--type-dialog-title-weight);
  color: var(--text-primary);
}

p {
  margin: 0;
  font-size: var(--type-dialog-message-size);
  font-weight: var(--type-dialog-message-weight);
  color: var(--text-secondary);
}

/* iOS `StrengthBar` proportions: a 6pt capsule. */
progress {
  width: 100%;
  height: 6px;
  accent-color: var(--accent);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}
</style>
