<script setup lang="ts">
// iOS `HomeView` + `HomeCards`: greeting, the status hero from the local health report,
// the health bar card, the VPN card and the breached-passwords card. The page wash turns
// warm when nothing needs attention and cool when something does. On desktop the VPN card
// points to the phone (no VPN in v1), and the notifications bell arrives with the inbox.
import { Bell, CheckCircle2, DatabaseBackup, KeyRound, Lightbulb, Lock, ShieldAlert, ShieldHalf } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import QasaMark from '@/components/QasaMark.vue'
import { account } from '@/account/store'
import { backupApi } from '@/backup/api'
import { bridgeApi } from '@/bridge/api'
import { backup, refreshBackup, setBackupStatus } from '@/backup/store'
import { selectTab, openPage } from '@/nav'
import { useT } from '@/prefs'
import { session } from '@/vault/session'
import { toolsApi, type HealthReport } from '@/vault/tools'

const t = useT()
const report = ref<HealthReport | null>(null)

type Status = 'loading' | 'empty' | 'allGood' | 'needsAttention'
const status = computed<Status>(() => {
  const r = report.value
  if (!r) return 'loading'
  if (r.totalPasswords === 0) return 'empty'
  return r.reusedCount + r.weakCount + r.breachedCount === 0 ? 'allGood' : 'needsAttention'
})

async function refresh() {
  try {
    report.value = await toolsApi.health()
  } catch (e) {
    console.error('Could not build the health report', e)
  }
}

onMounted(refresh)
onMounted(refreshBackup)
watch(() => session.items, refresh)
// The reminder depends on the item count (an empty vault isn't reminded).
watch(() => session.items.length, refreshBackup)

async function snoozeBackupReminder() {
  try {
    setBackupStatus(await backupApi.snoozeReminder())
  } catch (e) {
    console.error('Could not dismiss the backup reminder', e)
  }
}
watch(() => account.status?.isPremium, refresh)

// "Fill passwords in your browser": the desktop's autofill is the browser extension, so
// Home asks until a browser is connected or the user taps Not now (hidden for 3 days).
const PROMPT_SNOOZE_KEY = 'qasa.autofill.promptSnoozedUntil'
const PROMPT_SNOOZE_MS = 3 * 24 * 60 * 60 * 1000
const askBrowser = ref(false)
async function refreshBrowserPrompt() {
  let snoozedUntil = 0
  try {
    snoozedUntil = Number(localStorage.getItem(PROMPT_SNOOZE_KEY) ?? 0)
  } catch {
    // Storage blocked: keep asking.
  }
  if (Date.now() < snoozedUntil) {
    askBrowser.value = false
    return
  }
  try {
    askBrowser.value = (await bridgeApi.clients()).length === 0
  } catch {
    askBrowser.value = false
  }
}
function snoozeBrowserPrompt() {
  try {
    localStorage.setItem(PROMPT_SNOOZE_KEY, String(Date.now() + PROMPT_SNOOZE_MS))
  } catch {
    // Storage blocked: hide it for this visit only.
  }
  askBrowser.value = false
}
onMounted(() => {
  void refreshBrowserPrompt()
  window.addEventListener('focus', refreshBrowserPrompt)
})
onBeforeUnmount(() => window.removeEventListener('focus', refreshBrowserPrompt))

const premium = computed(() => account.status?.isPremium ?? false)

const greetingKey = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour <= 11) return 'home.greetingMorning'
  if (hour >= 12 && hour <= 17) return 'home.greetingAfternoon'
  return 'home.greetingEvening'
})

const hero = computed(() => {
  switch (status.value) {
    case 'allGood':
      return { icon: CheckCircle2, title: t('home.allgoodTitle'), body: t('home.allgoodBody') }
    case 'needsAttention':
      return { icon: Lightbulb, title: t('home.attentionTitle'), body: t('home.attentionHint') }
    case 'empty':
      return { icon: KeyRound, title: t('home.emptyTitle'), body: t('home.emptyBody') }
    default:
      return { icon: null, title: t('home.checking'), body: null }
  }
})

const health = computed(() => {
  const r = report.value
  const total = r?.totalPasswords ?? 0
  const reused = r?.reusedCount ?? 0
  const weak = r?.weakCount ?? 0
  const breached = r?.breachedCount ?? 0
  // A login can be both reused and weak; count it once.
  const toFix = r ? new Set([...r.reusedIds, ...r.weakIds, ...r.breachedIds]).size : 0
  const fine = Math.max(total - toFix, 0)
  const sum = Math.max(fine + reused + weak, 1)
  const line =
    toFix === 0
      ? t('home.healthAllgood', total)
      : breached > 0
        ? t('home.attentionBreachedBody', reused, weak, breached)
        : t('home.attentionBody', reused, weak)
  return { toFix, line, fine: (fine / sum) * 100, reused: (reused / sum) * 100, weak: (weak / sum) * 100 }
})
</script>

<template>
  <div class="home" :class="status === 'allGood' ? 'warm' : 'calm'">
    <div class="column">
      <div class="greeting-row">
        <div class="greeting">
          <span class="hello">{{ t(greetingKey) }}</span>
          <span class="name">{{ t('session.qasaUser') }}</span>
        </div>
        <button type="button" class="bell" :aria-label="t('nav.notifications')" :title="t('nav.notifications')" @click="openPage('notifications')">
          <Bell :size="18" aria-hidden="true" />
          <span v-if="account.unread > 0" class="badge">{{ account.unread > 99 ? '99+' : account.unread }}</span>
        </button>
      </div>

      <!-- No browser connected yet: the desktop's autofill is the browser extension. -->
      <div v-if="askBrowser" class="home-card static prompt" role="region" :aria-label="t('autofill.prompt.desktopTitle')">
        <div class="card-row">
          <span class="mark" aria-hidden="true"><QasaMark :size="24" /></span>
          <span class="card-title">{{ t('autofill.prompt.desktopTitle') }}</span>
        </div>
        <span class="card-line">{{ t('autofill.prompt.desktopBody') }}</span>
        <div class="prompt-actions">
          <button type="button" class="qasa-button" @click="openPage('browserExtension')"><span>{{ t('autofill.prompt.activate') }}</span></button>
          <button type="button" class="qasa-button plain inline" @click="snoozeBrowserPrompt"><span>{{ t('autofill.prompt.notNow') }}</span></button>
        </div>
      </div>

      <section class="hero">
        <div class="disc">
          <div class="inner">
            <span v-if="status === 'loading'" class="spinner" aria-hidden="true" />
            <component :is="hero.icon" v-else :size="40" :class="status === 'allGood' ? 'good' : 'accent'" aria-hidden="true" />
          </div>
        </div>
        <h1 class="hero-title">{{ hero.title }}</h1>
        <p v-if="hero.body" class="hero-body">{{ hero.body }}</p>
        <div v-if="status === 'empty'" class="hero-action">
          <PrimaryButton :title="t('home.addfirst')" @click="selectTab('passwords')" />
        </div>
      </section>

      <!-- Backup reminder: the vault is only on this computer (spec: backups §7). -->
      <div v-if="backup.status?.remind" class="home-card static" role="region" :aria-label="t('backup.reminder.title')">
        <div class="card-row top">
          <span class="tile" aria-hidden="true"><DatabaseBackup :size="18" /></span>
          <span class="card-text">
            <span class="card-title">{{ t('backup.reminder.title') }}</span>
            <span class="card-line">{{ t('backup.reminder.body') }}</span>
          </span>
        </div>
        <div class="card-actions">
          <button type="button" class="pill-button" @click="openPage('backups')"><span class="pill muted">{{ t('backup.reminder.action') }}</span></button>
          <button type="button" class="text-button" @click="snoozeBackupReminder">{{ t('backup.reminder.dismiss') }}</button>
        </div>
      </div>

      <button v-if="status !== 'empty'" type="button" class="home-card" @click="openPage('passwordHealth')">
        <div class="card-row">
          <span class="card-title">{{ t('home.healthTitle') }}</span>
          <span class="spacer" />
          <span v-if="health.toFix > 0" class="pill danger">{{ t('home.tofix', health.toFix) }}</span>
          <span v-else class="pill good">{{ t('home.allfine') }}</span>
        </div>
        <div class="bar" aria-hidden="true">
          <span :style="{ width: `${health.fine}%`, background: 'var(--success)' }" />
          <span :style="{ width: `${health.reused}%`, background: 'var(--gold)' }" />
          <span :style="{ width: `${health.weak}%`, background: 'var(--danger)' }" />
        </div>
        <span class="card-line">{{ health.line }}</span>
      </button>

      <div class="home-card static">
        <div class="card-row">
          <span class="tile" aria-hidden="true"><ShieldHalf :size="18" /></span>
          <span class="card-text">
            <span class="card-title">{{ t('nav.vpn') }}</span>
            <span class="card-line">{{ t('home.vpnDesktop') }}</span>
          </span>
        </div>
      </div>

      <button type="button" class="home-card" @click="premium ? selectTab('alerts') : openPage('premiumUpgrade')">
        <div class="card-row">
          <span class="tile" aria-hidden="true"><ShieldAlert v-if="premium" :size="18" /><Lock v-else :size="18" /></span>
          <span class="card-text">
            <span class="card-title">{{ t(premium ? 'nav.breachedPasswords' : 'breached.lockedTitle') }}</span>
            <span class="card-line">{{ t(premium ? 'home.breachedBody' : 'breached.lockedBody') }}</span>
          </span>
        </div>
        <span v-if="!premium" class="pill muted">{{ t('breached.unlock') }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
/* The wash crossfades over 0.4s ease-in-out, as iOS (proposed token: `motion.wash`). */
.home {
  height: 100%;
  overflow-y: auto;
  transition: background 400ms var(--ease-ease-in-out);
}

.home.warm {
  background: linear-gradient(to bottom, var(--warm) 0%, var(--background) 55%);
}

.home.calm {
  background: linear-gradient(to bottom, var(--calm) 0%, var(--background) 55%);
}

.column {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
  max-width: 720px;
  margin-inline: auto;
  padding: 16px var(--space-screen-padding) 40px;
}

.greeting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* iOS: 44pt card-filled square, radius 14, 18 medium bell; the badge 11 semibold, 4 in. */
.bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 0;
  border-radius: 14px;
  background: var(--card-background);
  color: var(--text-primary);
  cursor: pointer;
}

.badge {
  position: absolute;
  top: 4px;
  inset-inline-end: 4px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: var(--type-field-error-size);
  font-weight: 600;
  text-align: center;
}

.greeting {
  display: flex;
  flex-direction: column;
  gap: var(--space-row-text-gap);
  min-width: 0;
}

.hello {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.name {
  font-size: 22px;
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
}

.disc {
  display: grid;
  place-items: center;
  width: 168px;
  height: 168px;
  margin-bottom: 6px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--card-background) 55%, transparent);
}

.inner {
  display: grid;
  place-items: center;
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background: var(--card-background);
}

.good {
  color: var(--success);
}

.accent {
  color: var(--accent);
}

.spinner {
  width: 20px;
  height: 20px;
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

.hero-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
}

.hero-body {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.hero-action {
  width: 100%;
  padding-top: 10px;
}

/* iOS `HomeCard`: card fill, radius 20, padding 17, 8 between its lines, no stroke. */
.home-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  padding: 17px;
  border: 0;
  border-radius: var(--radius-card);
  background: var(--card-background);
  color: var(--text-primary);
  text-align: start;
  cursor: pointer;
}

.home-card.static {
  cursor: default;
}

/* The autofill prompt (iOS `HomeAutofillPromptCard`): 12 apart, with a 1px stroke. */
.home-card.prompt {
  gap: 12px;
  border: var(--size-stroke) solid var(--stroke);
}

.card-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-row.top {
  align-items: flex-start;
}

.card-text {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--space-row-text-gap);
  min-width: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-line {
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  color: var(--text-secondary);
}

.spacer {
  flex: 1;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.prompt-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.prompt-actions .qasa-button:not(.inline) {
  flex: 1;
}

.pill-button,
.text-button {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.text-button {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

/* iOS `HomeIconTile`: 40pt, radius 12, cardMuted, 18 medium accent glyph. */
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

/* iOS `HomePill`: capsule, 13 medium, padding 12 × 4. */
.pill {
  display: inline-block;
  align-self: flex-start;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}

.pill.danger {
  background: var(--danger-background);
  color: var(--danger-text);
}

.pill.good {
  background: color-mix(in srgb, var(--success) 14%, transparent);
  color: var(--success);
}

.pill.muted {
  background: var(--card-muted);
  color: var(--on-accent-chip);
}

.bar {
  display: flex;
  height: 8px;
  margin-block: 4px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--stroke);
}

/* The Qasa mark, 24, in a 44pt accent circle. */
.prompt .mark {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--accent);
  color: var(--on-accent);
}
</style>
