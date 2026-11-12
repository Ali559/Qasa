<script setup lang="ts">
// The toolbar popup. One screen per bridge state (plan §8.3): app not running, not paired
// (connect + matching code), locked, no vault, and unlocked: logins for the current site,
// search, copy actions, Fill, and the generator.
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import BrandHeader from '@/components/BrandHeader.vue'
import FilterChip from '@/components/FilterChip.vue'
import PickerSheet, { type PickerOption } from '@/components/PickerSheet.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import TextField from '@/components/TextField.vue'
import ToastHost from '@/components/ToastHost.vue'
import { isRtl, LANGUAGE_NAMES, LANGUAGES, type Language } from '@/i18n'
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
/** The language picker: the desktop's `PickerSheet`, rising from the popup's bottom edge. */
const showLanguage = ref(false)
const languageOptions: PickerOption<Language>[] = LANGUAGES.map((language) => ({
  value: language,
  label: LANGUAGE_NAMES[language],
  dir: isRtl(language) ? 'rtl' : 'ltr',
  lang: language,
}))

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
    showToast(t('ext.error'), { tone: 'error' })
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
    showToast(t('ext.error'), { tone: 'error' })
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
  if (!filled) showToast(t('ext.fillFailed'), { tone: 'error' })
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
          <QasaIcon name="open-app" :size="16" />
        </button>
      </template>
    </BrandHeader>

    <main class="content" :data-state="state ?? 'loading'">
      <template v-if="!status" />

      <StateCard v-else-if="state === 'appNotRunning'" icon="desktop" :title="t('ext.appNotRunningTitle')" :body="t('ext.appNotRunningBody')" tone="gold">
        <PrimaryButton :title="t('ext.tryAgain')" icon="refresh" @click="refresh" />
        <p class="hint">{{ t('ext.notInstalled') }}</p>
        <a class="qasa-button secondary" :href="DOWNLOAD_URL" target="_blank" rel="noopener">
          <QasaIcon name="import" :size="15" /><span>{{ t('ext.download') }}</span>
        </a>
      </StateCard>

      <StateCard v-else-if="state === 'notPaired'" icon="link" :title="t('ext.connectTitle')" :body="t('ext.connectBody')">
        <p v-if="status.state === 'notPaired' && status.reason" class="notice" role="alert">
          {{ status.reason === 'denied' ? t('ext.pairDenied') : t('ext.pairExpired') }}
        </p>
        <PrimaryButton :title="t('ext.connect')" :disabled="connecting" @click="connect" />
      </StateCard>

      <StateCard v-else-if="status.state === 'pairing'" icon="link" :title="t('ext.connectTitle')" :body="t('ext.pairCodeHint')">
        <output class="code" dir="ltr" data-testid="pair-code">{{ status.code.slice(0, 3) }} {{ status.code.slice(3) }}</output>
        <p class="hint">{{ t('ext.pairWaiting') }}</p>
      </StateCard>

      <!-- The main password is due: only the desktop app can take it, so say so and open it. -->
      <StateCard
        v-else-if="status.state === 'locked' && status.passwordDueDays"
        icon="lock"
        :title="t('ext.lockedTitle')"
        :body="t('auth.unlock.masterPasswordDueFmt', status.passwordDueDays)"
      >
        <PrimaryButton :title="t('ext.openQasa')" @click="openQasa" />
      </StateCard>

      <StateCard v-else-if="state === 'locked'" icon="lock" :title="t('ext.lockedTitle')" :body="t('ext.lockedBody')">
        <PrimaryButton :title="t('autofill.unlockTitle')" @click="openQasa" />
      </StateCard>

      <StateCard v-else-if="state === 'noVault'" icon="vault" :title="t('ext.noVaultTitle')" :body="t('ext.noVaultBody')">
        <PrimaryButton :title="t('ext.openQasa')" @click="openQasa" />
      </StateCard>

      <template v-else>
        <div class="chips" role="tablist">
          <FilterChip :title="t('ext.tabLogins')" :selected="view === 'logins'" @click="view = 'logins'" />
          <FilterChip :title="t('ext.tabGenerator')" :selected="view === 'generator'" @click="view = 'generator'" />
        </div>

        <template v-if="view === 'logins'">
          <TextField v-model="query" icon="search" :placeholder="t('vault.searchPlaceholder')" />

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
      <button
        type="button"
        class="language"
        :aria-label="`${t('settings.language')}, ${LANGUAGE_NAMES[prefs.language]}`"
        aria-haspopup="dialog"
        @click="showLanguage = true"
      >
        <QasaIcon name="language" :size="15" />
        <span :lang="prefs.language">{{ LANGUAGE_NAMES[prefs.language] }}</span>
        <QasaIcon name="chevron-down" :size="13" class="chevron" />
      </button>
    </footer>
    <PickerSheet v-model="prefs.language" :open="showLanguage" :title="t('settings.language')" :options="languageOptions" @close="showLanguage = false" />
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
  padding: 4px var(--space-screen-padding) var(--space-screen-padding);
}

/* An accent icon badge (iOS `IconBadge`: 36, radius 10, tint at 12%). */
.header-button {
  display: grid;
  place-items: center;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  padding: 0;
  border: 0;
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  cursor: pointer;
}

.header-button svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.chips {
  display: flex;
  gap: 8px;
}

/* The pairing code, as iOS LAN sync's code (`kasoMono(44, bold)`), at the popup's width. */
.code {
  display: block;
  margin: 4px 0;
  font-family: var(--font-mono);
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
  font-weight: 400;
  color: var(--text-secondary);
}

.empty {
  padding: 8px 4px;
}

.notice {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-control);
  background: var(--danger-background);
  color: var(--danger-text);
  font-size: 14px;
}

.footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 8px var(--space-screen-padding);
  border-top: var(--size-stroke) solid var(--stroke);
  background: var(--chrome);
}

.language {
  display: flex;
  align-items: center;
  gap: 6px;
  height: var(--size-field-icon-button);
  padding: 0 10px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  background: var(--card-background);
  color: var(--text-secondary);
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  cursor: pointer;
}

.language .chevron {
  color: var(--text-tertiary);
}
</style>
