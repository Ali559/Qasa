<script setup lang="ts">
// Settings → Browser extension (plan §8.1): the browsers allowed to fill logins, each
// with Remove. Pairing itself starts from the extension's popup.
import { onMounted, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageFrame from '@/components/PageFrame.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import { bridgeApi, type BridgeClient } from '@/bridge/api'
import { prefs, useT } from '@/prefs'

defineEmits<{ back: [] }>()
const t = useT()
const clients = ref<BridgeClient[]>([])
const pendingRemove = ref<BridgeClient | null>(null)

async function refresh() {
  clients.value = await bridgeApi.clients()
}

onMounted(refresh)

function formatDate(ms: number): string {
  return new Date(ms).toLocaleString(prefs.language, { dateStyle: 'medium', timeStyle: 'short' })
}

async function remove() {
  const client = pendingRemove.value
  pendingRemove.value = null
  if (!client) return
  await bridgeApi.revoke(client.id)
  await refresh()
}
</script>

<template>
  <PageFrame :title="t('bridge.settingsRow')" back @back="$emit('back')">
    <p class="muted">{{ t('bridge.pageBody') }}</p>

    <section v-if="clients.length === 0" class="group">
      <span class="label"><SectionLabel :text="t('bridge.allowedBrowsers')" /></span>
      <CardContainer :padding="20" class="empty">
        <QasaIcon name="link" :size="28" />
        <p class="muted">{{ t('bridge.noClients') }}</p>
      </CardContainer>
    </section>
    <!-- One `SettingsRow` per browser, with Remove as the trailing control. -->
    <SettingsSection v-else :title="t('bridge.allowedBrowsers')">
      <SettingsRow
        v-for="c in clients"
        :key="c.id"
        icon="website"
        :title="c.name"
        :subtitle="c.lastUsedAt ? t('bridge.lastUsed', formatDate(c.lastUsedAt)) : t('bridge.neverUsed')"
      >
        <template #trailing>
          <button type="button" class="remove" @click="pendingRemove = c">{{ t('bridge.remove') }}</button>
        </template>
      </SettingsRow>
    </SettingsSection>

    <ConfirmDialog
      :open="pendingRemove !== null"
      :title="pendingRemove ? t('bridge.removeTitle', pendingRemove.name) : ''"
      :message="t('bridge.removeMessage')"
      :confirm-label="t('bridge.remove')"
      :cancel-label="t('settings.cancel')"
      destructive
      @confirm="remove"
      @cancel="pendingRemove = null"
    />
  </PageFrame>
</template>

<style scoped>
.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-section-label-gap);
}

.label {
  padding-inline: 4px;
}

.empty {
  align-items: center;
  gap: 10px;
  text-align: center;
  color: var(--text-secondary);
}

/* A compact secondary control (iOS `PressableStyle` feedback). */
.remove {
  flex-shrink: 0;
  padding: 6px 12px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  background: var(--card-background);
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.remove:active {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}
</style>
