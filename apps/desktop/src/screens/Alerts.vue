<script setup lang="ts">
// iOS `BreachedPasswordsView` (Alerts tab): saved passwords found in Pwned Passwords.
// Premium only; checked from this computer, sending just a 5-character hash prefix.
// Free accounts get the locked card and the check never runs.
import { computed, onMounted, ref, watch } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import PageFrame from '@/components/PageFrame.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import { account } from '@/account/store'
import { nav, selectTab } from '@/nav'
import { prefs, useT } from '@/prefs'
import { session } from '@/vault/session'
import { toolsApi, type HealthReport } from '@/vault/tools'
import BreachLockedCard from './BreachLockedCard.vue'
import VaultRow from './vault/VaultRow.vue'

const t = useT()
const report = ref<HealthReport | null>(null)
const checking = ref(false)

const premium = computed(() => account.status?.isPremium ?? false)
const locked = computed(() => !premium.value || report.value?.breachLocked === true)

async function check() {
  if (!premium.value) {
    report.value = null
    return
  }
  if (checking.value) return
  checking.value = true
  try {
    report.value = await toolsApi.health()
  } finally {
    checking.value = false
  }
}

onMounted(check)
watch(() => session.items, check)
watch(premium, check)

const rows = computed(() => {
  const r = report.value
  if (!r) return []
  const byId = new Map(session.items.map((i) => [i.id, i]))
  return r.breachedIds.map((id) => ({ row: byId.get(id), count: r.breachCounts[id] ?? 0 })).filter((x) => !!x.row)
})

const number = (n: number) => new Intl.NumberFormat(prefs.language).format(n)

function openItem(id: string) {
  nav.selectedItemId = id
  selectTab('passwords')
}
</script>

<template>
  <PageFrame :title="t('nav.breachedPasswords')" :gap="20">
    <BreachLockedCard v-if="locked" />

    <template v-else-if="report && (!checking || report.breachedCount > 0)">
      <CardContainer v-if="report.breachCheckFailed" :padding="20">
        <div class="stack wide">
          <span class="line"><QasaIcon name="wifi-off" :size="16" />{{ t('breached.offline') }}</span>
          <button type="button" class="again" :disabled="checking" @click="check"><QasaIcon name="refresh" :size="14" />{{ t('breached.checkAgain') }}</button>
        </div>
      </CardContainer>
      <CardContainer v-else-if="report.breachedCount > 0" :padding="20">
        <div class="stack">
          <h2 class="found">{{ t('breached.found', number(report.breachedCount)) }}</h2>
          <p class="muted">{{ t('breached.changeHint') }}</p>
        </div>
      </CardContainer>
      <CardContainer v-else :padding="20">
        <div class="stack">
          <h2 class="none"><QasaIcon name="protected" :size="20" />{{ t('breached.noneTitle') }}</h2>
          <p class="muted">{{ t('breached.noneBody') }}</p>
          <button type="button" class="again" :disabled="checking" @click="check"><QasaIcon name="refresh" :size="14" />{{ t('breached.checkAgain') }}</button>
        </div>
      </CardContainer>
    </template>

    <div v-else class="checking"><span class="spinner" />{{ t('breached.checking') }}</div>

    <div v-if="!locked && rows.length" class="rows">
      <div v-for="{ row, count } in rows" :key="row!.id" class="breached">
        <VaultRow :row="row!" :selected="false" @select="openItem(row!.id)" @edit="openItem(row!.id)" />
        <span class="seen">{{ t('breached.seenCount', number(count)) }}</span>
      </div>
    </div>

    <CardContainer :padding="20">
      <div class="stack tight">
        <h3 class="privacy-title">{{ t('breached.privacyTitle') }}</h3>
        <p class="muted">{{ t('breached.privacyBody') }}</p>
      </div>
    </CardContainer>
  </PageFrame>
</template>

<style scoped>
.stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stack.wide {
  gap: 14px;
}

.stack.tight {
  gap: 8px;
}

.found,
.none {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.found {
  color: var(--danger);
}

.none {
  color: var(--success);
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.muted {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.again {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

/* iOS `PressableStyle`. */
.again:active:not(:disabled) {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.again:disabled {
  opacity: var(--motion-disabled-opacity);
  cursor: default;
}

.checking {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-block: 8px;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid color-mix(in srgb, var(--accent) 25%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.rows {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.breached {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.seen {
  padding-inline-start: 64px;
  font-size: var(--type-row-value-size);
  font-weight: 500;
  color: var(--danger);
}

.privacy-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
</style>
