<script setup lang="ts">
// "Qasa 1.2 is available": shown once a check finds an update, until Later. Installing
// restarts the app, which locks the vault, so the prompt says so.
import Sheet from '@/components/Sheet.vue'
import { useT } from '@/prefs'
import { installUpdate, updater } from '@/updater'

const t = useT()
const open = () => (updater.stage === 'available' && !updater.dismissed) || updater.stage === 'downloading'
</script>

<template>
  <Sheet :open="open()" :label="t('update.availableTitle', updater.next)" :width="400" placement="bottom" @close="updater.stage !== 'downloading' && (updater.dismissed = true)">
    <div class="prompt">
      <h2>{{ t('update.availableTitle', updater.next) }}</h2>
      <template v-if="updater.stage === 'downloading'">
        <p>{{ t('update.installing') }}</p>
        <progress :value="updater.progress ?? undefined" max="1" />
      </template>
      <template v-else>
        <p>{{ t('update.availableBody') }}</p>
        <div class="actions">
          <button type="button" class="secondary" @click="updater.dismissed = true">{{ t('update.later') }}</button>
          <button type="button" class="primary" @click="installUpdate">{{ t('update.install') }}</button>
        </div>
      </template>
    </div>
  </Sheet>
</template>

<style scoped>
.prompt {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 24px;
  text-align: center;
}

h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

progress {
  width: 100%;
  height: 6px;
  accent-color: var(--accent);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.actions button {
  flex: 1;
  height: var(--button-height);
  padding: 0 16px;
  border-radius: var(--button-radius);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}

.secondary {
  border: 1px solid var(--stroke);
  background: var(--card-background);
  color: var(--text-primary);
}

.primary {
  border: 0;
  background: var(--accent);
  color: var(--on-accent);
}
</style>
