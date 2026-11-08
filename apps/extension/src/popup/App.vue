<script setup lang="ts">
// The toolbar popup. One screen per bridge state (plan §8.3): app not running, not paired
// (connect + matching code), locked, no vault, and unlocked: logins for the current site,
// search, copy actions, Fill, and the generator.
import { AppWindow, Download, Link2, Lock, MonitorX, RefreshCw, Search, Vault } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import BrandHeader from '@/components/BrandHeader.vue'
import FilterChip from '@/components/FilterChip.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import TextField from '@/components/TextField.vue'
import ToastHost from '@/components/ToastHost.vue'
import { LANGUAGE_NAMES, LANGUAGES } from '@/i18n'
import { showToast } from '@/toast'
import type { BridgeStatus, CopyField, LoginMatch, SearchHit } from '@ext/shared/messages'
import { ask, fillInTab, isDetached, NoPageError, tabMatches, targetTab } from './api'
import GeneratorPanel from './components/GeneratorPanel.vue'
import LoginRow from './components/LoginRow.vue'
import StateCard from './components/StateCard.vue'
import { prefs, useT } from './prefs'

const DOWNLOAD_URL = 'https://qasa-app.com/download'

const t = useT()
const status = ref<BridgeStatus | null>(null)
const connecting = ref(false)
const tab = ref<chrome.tabs.Tab>()
const matches = ref<LoginMatch[]>([])
const query = ref('')
const results = ref<SearchHit[]>([])
const view = ref<'logins' | 'generator'>('logins')

/** Whether the tab has a page we can fill (its content script answered). */
const onWebPage = ref(true)
const state = computed(() => status.value?.state)

async function refresh(): Promise<void> {
  try {
    status.value = await ask<BridgeStatus>({ kind: 'status' })
  } catch {
    status.value = { state: 'appNotRunning' }
  }
}

async function loadMatches(): Promise<void> {
  const id = tab.value?.id
  matches.value = []
  if (id === undefined) {
    onWebPage.value = false
    return
  }
  try {
    matches.value = await tabMatches(id)
    onWebPage.value = true
  } catch (error) {
    onWebPage.value = !(error instanceof NoPageError) && error instanceof Error && error.message !== 'badUrl'
  }
}

// Locked, pairing and not-running states change on the desktop: poll while they show.
let poll: ReturnType<typeof setInterval> | undefined
watch(state, (s, previous) => {
  clearInterval(poll)
  if (s && s !== 'unlocked' && s !== 'notPaired') poll = setInterval(refresh, s === 'pairing' ? 1000 : 2000)
  if (s === 'unlocked' && previous !== 'unlocked') void loadMatches()
})

async function connect(): Promise<void> {
  connecting.value = true
  try {
    const code = await ask<string>({ kind: 'pair' })
    status.value = { state: 'pairing', code }
  } catch (error) {
    const code = (error as Error).message
    status.value =
      code === 'appNotRunning' || code === 'timeout'
        ? { state: 'appNotRunning' }
        : { state: 'notPaired', reason: code === 'pairDenied' ? 'denied' : 'expired' }
  } finally {
    connecting.value = false
  }
}

async function openQasa(): Promise<void> {
  try {
    await ask({ kind: 'unlock' })
  } catch {
    showToast(t('ext.error'))
  }
}

// Search: debounced, through the desktop (`search` op, logins only).
let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(query, (q) => {
  clearTimeout(searchTimer)
  if (!q.trim()) {
    results.value = []
    return
  }
  searchTimer = setTimeout(async () => {
    try {
      results.value = await ask<SearchHit[]>({ kind: 'search', query: q.trim() })
    } catch {
      results.value = []
    }
  }, 250)
})

async function copy(item: LoginMatch, field: CopyField): Promise<void> {
  try {
    await ask({ kind: 'copy', itemId: item.id, field })
    showToast(field === 'totp' ? t('totp.copied') : t('ext.copiedClears'))
  } catch {
    showToast(t('ext.error'))
  }
}

async function fill(item: LoginMatch): Promise<void> {
  const id = tab.value?.id
  if (id === undefined) return
  let filled = false
  try {
    filled = await fillInTab(id, item.id)
  } catch {
    filled = false
  }
  if (!filled) showToast(t('ext.fillFailed'))
  else if (!isDetached()) window.close()
}

const matchIds = computed(() => new Set(matches.value.map((m) => m.id)))

onMounted(async () => {
  tab.value = await targetTab()
  await refresh()
})
onBeforeUnmount(() => clearInterval(poll))
</script>

<template>
  <div class="popup">
    <BrandHeader>
      <template #trailing>
        <button v-if="state === 'unlocked'" type="button" class="header-button" :title="t('ext.openQasa')" :aria-label="t('ext.openQasa')" @click="openQasa">
          <AppWindow :size="18" />
        </button>
      </template>
    </BrandHeader>

    <main class="content" :data-state="state ?? 'loading'">
      <template v-if="!status" />

      <StateCard v-else-if="state === 'appNotRunning'" :icon="MonitorX" :title="t('ext.appNotRunningTitle')" :body="t('ext.appNotRunningBody')" tone="gold">
        <PrimaryButton :title="t('ext.tryAgain')" :icon="RefreshCw" @click="refresh" />
        <p class="hint">{{ t('ext.notInstalled') }}</p>
        <a class="link-button" :href="DOWNLOAD_URL" target="_blank" rel="noopener">
          <Download :size="16" aria-hidden="true" />{{ t('ext.download') }}
        </a>
      </StateCard>

      <StateCard v-else-if="state === 'notPaired'" :icon="Link2" :title="t('ext.connectTitle')" :body="t('ext.connectBody')">
        <p v-if="status.state === 'notPaired' && status.reason" class="notice" role="alert">
          {{ status.reason === 'denied' ? t('ext.pairDenied') : t('ext.pairExpired') }}
        </p>
        <PrimaryButton :title="t('ext.connect')" :disabled="connecting" @click="connect" />
      </StateCard>

      <StateCard v-else-if="status.state === 'pairing'" :icon="Link2" :title="t('ext.connectTitle')" :body="t('ext.pairCodeHint')">
        <output class="code" dir="ltr" data-testid="pair-code">{{ status.code.slice(0, 3) }} {{ status.code.slice(3) }}</output>
        <p class="hint">{{ t('ext.pairWaiting') }}</p>
      </StateCard>

      <StateCard v-else-if="state === 'locked'" :icon="Lock" :title="t('ext.lockedTitle')" :body="t('ext.lockedBody')">
        <PrimaryButton :title="t('autofill.unlockTitle')" @click="openQasa" />
      </StateCard>

      <StateCard v-else-if="state === 'noVault'" :icon="Vault" :title="t('ext.noVaultTitle')" :body="t('ext.noVaultBody')">
        <PrimaryButton :title="t('ext.openQasa')" @click="openQasa" />
      </StateCard>

      <template v-else>
        <div class="chips" role="tablist">
          <FilterChip :title="t('ext.tabLogins')" :selected="view === 'logins'" @click="view = 'logins'" />
          <FilterChip :title="t('ext.tabGenerator')" :selected="view === 'generator'" @click="view = 'generator'" />
        </div>

        <template v-if="view === 'logins'">
          <TextField v-model="query" :icon="Search" :placeholder="t('vault.searchPlaceholder')" />

          <template v-if="query.trim()">
            <p v-if="!results.length" class="empty">{{ t('autofill.noMatches') }}</p>
            <LoginRow v-for="item in results" :key="item.id" :item="item" :can-fill="matchIds.has(item.id)" @copy="copy(item, $event)" @fill="fill(item)" />
          </template>

          <template v-else>
            <SectionLabel :text="t('ext.thisSite')" />
            <p v-if="!onWebPage" class="empty">{{ t('ext.notWebPage') }}</p>
            <p v-else-if="!matches.length" class="empty">{{ t('ext.noMatchesHint') }}</p>
            <LoginRow v-for="item in matches" :key="item.id" :item="item" can-fill @copy="copy(item, $event)" @fill="fill(item)" />
          </template>
        </template>

        <GeneratorPanel v-else />
      </template>
    </main>

    <footer class="footer">
      <label class="language">
        <span class="visually-hidden">{{ t('settings.language') }}</span>
        <select v-model="prefs.language" :aria-label="t('settings.language')">
          <option v-for="lang in LANGUAGES" :key="lang" :value="lang">{{ LANGUAGE_NAMES[lang] }}</option>
        </select>
      </label>
    </footer>
    <ToastHost />
  </div>
</template>

<style scoped>
.popup {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 16px 16px;
}

.header-button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: var(--card-muted);
  color: var(--accent);
  cursor: pointer;
}

.chips {
  display: flex;
  gap: 8px;
}

.code {
  display: block;
  margin: 4px 0;
  font-size: 40px;
  font-weight: 700;
  letter-spacing: 4px;
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}

.hint,
.empty {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.empty {
  padding: 8px 4px;
}

.notice {
  margin: 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--danger-background);
  color: var(--danger-text);
  font-size: 14px;
}

.link-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background-light);
  color: var(--accent-dark);
  font-weight: 600;
  text-decoration: none;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  padding: 8px 16px;
  border-top: 1px solid var(--stroke);
  background: var(--chrome);
  color: var(--text-tertiary);
}

.language select {
  padding: 4px 8px;
  border: 1px solid var(--stroke);
  border-radius: 8px;
  background: var(--card-background);
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}
</style>
