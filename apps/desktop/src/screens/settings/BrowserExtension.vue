<script setup lang="ts">
// Settings → Browser extension (plan §8.1): the browsers allowed to fill logins, each
// with Remove. Pairing itself starts from the extension's popup.
import { Globe, Puzzle } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageFrame from '@/components/PageFrame.vue'
import SectionLabel from '@/components/SectionLabel.vue'
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
  <PageFrame :title="t('bridge.settingsRow')" back :gap="16" @back="$emit('back')">
    <p class="muted">{{ t('bridge.pageBody') }}</p>

    <section class="group">
      <SectionLabel :text="t('bridge.allowedBrowsers')" />
      <CardContainer v-if="clients.length === 0" :padding="20" class="empty">
        <Puzzle :size="28" aria-hidden="true" />
        <p class="muted">{{ t('bridge.noClients') }}</p>
      </CardContainer>
      <CardContainer v-else :padding="0">
        <template v-for="(c, i) in clients" :key="c.id">
          <hr v-if="i > 0" class="divider" />
          <div class="row">
            <span class="tile" aria-hidden="true"><Globe :size="18" /></span>
            <div class="text">
              <span class="name">{{ c.name }}</span>
              <span class="sub">{{ c.lastUsedAt ? t('bridge.lastUsed', formatDate(c.lastUsedAt)) : t('bridge.neverUsed') }}</span>
            </div>
            <button type="button" class="remove" @click="pendingRemove = c">{{ t('bridge.remove') }}</button>
          </div>
        </template>
      </CardContainer>
    </section>

    <ConfirmDialog
      :open="pendingRemove !== null"
      :title="pendingRemove ? t('bridge.removeTitle', pendingRemove.name) : ''"
      :message="t('bridge.removeMessage')"
      :confirm-label="t('bridge.remove')"
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
  gap: 8px;
}

.empty {
  align-items: center;
  gap: 10px;
  text-align: center;
  color: var(--text-secondary);
}

.row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
}

.tile {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--accent);
}

.text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.sub {
  font-size: 13px;
  color: var(--text-secondary);
}

.remove {
  padding: 6px 12px;
  border: 1px solid var(--stroke);
  border-radius: 10px;
  background: transparent;
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.divider {
  margin: 0;
  border: 0;
  border-top: 1px solid var(--stroke);
}
</style>
