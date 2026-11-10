<script setup lang="ts">
// iOS `HomeView` + `HomeCards`: greeting, the status hero from the local health report,
// the health bar card, the VPN card and the breached-passwords card. The page wash turns
// warm when nothing needs attention and cool when something does. On desktop the VPN card
// points to the phone (no VPN in v1), and the notifications bell arrives with the inbox.
import { Bell, CheckCircle2, DatabaseBackup, KeyRound, Lightbulb, Lock, ShieldAlert, ShieldHalf } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'

import PrimaryButton from '@/components/PrimaryButton.vue'
import { account } from '@/account/store'
import { backupApi } from '@/backup/api'
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
          <Bell :size="18" />
          <span v-if="account.unread > 0" class="badge">{{ account.unread > 99 ? '99+' : account.unread }}</span>
        </button>
      </div>

      <section class="hero">
        <div class="disc">
          <div class="inner">
            <span v-if="status === 'loading'" class="spinner" aria-hidden="true" />
            <component :is="hero.icon" v-else :size="40" :class="status === 'allGood' ? 'good' : 'accent'" />
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
        <div class="card-row">
          <span class="tile"><DatabaseBackup :size="18" /></span>
          <span class="card-text">
            <span class="card-title">{{ t('backup.reminder.title') }}</span>
            <span class="card-line">{{ t('backup.reminder.body') }}</span>
          </span>
        </div>
        <div class="card-actions">
          <button type="button" class="card-action primary" @click="openPage('backups')">{{ t('backup.reminder.action') }}</button>
          <button type="button" class="card-action" @click="snoozeBackupReminder">{{ t('backup.reminder.dismiss') }}</button>
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
          <span class="tile"><ShieldHalf :size="18" /></span>
          <span class="card-text">
            <span class="card-title">{{ t('nav.vpn') }}</span>
            <span class="card-line">{{ t('home.vpnDesktop') }}</span>
          </span>
        </div>
      </div>

      <button type="button" class="home-card" @click="premium ? selectTab('alerts') : openPage('premiumUpgrade')">
        <div class="card-row">
          <span class="tile"><ShieldAlert v-if="premium" :size="18" /><Lock v-else :size="18" /></span>
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
.home {
  height: 100%;
  overflow-y: auto;
  transition: background 400ms ease-in-out;
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
  gap: 16px;
  max-width: 720px;
  margin-inline: auto;
  padding: 16px var(--screen-padding) 40px;
}

.greeting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
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
  min-width: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: var(--accent);
  color: var(--on-accent);
  font-size: 11px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
}

.greeting {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 44px;
  justify-content: center;
}

.hello {
  font-size: 14px;
  color: var(--text-secondary);
}

.name {
  font-size: 22px;
  font-weight: 700;
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
  width: 24px;
  height: 24px;
  border: 3px solid color-mix(in srgb, var(--accent) 25%, transparent);
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
  max-width: 420px;
  font-size: 14px;
  color: var(--text-secondary);
}

.hero-action {
  width: 100%;
  max-width: 360px;
  padding-top: 10px;
}

.home-card {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  width: 100%;
  padding: 17px;
  border: 0;
  border-radius: var(--corner-radius);
  background: var(--card-background);
  text-align: start;
  cursor: pointer;
  transition: transform var(--motion-press) ease-out;
}

.home-card:active:not(.static) {
  transform: scale(0.99);
}

.home-card.static {
  cursor: default;
}

.card-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-line {
  font-size: 13px;
  color: var(--text-secondary);
}

.spacer {
  flex: 1;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-inline-start: 52px;
}

.card-action {
  padding: 8px 14px;
  border: 0;
  border-radius: 999px;
  background: var(--card-muted);
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.card-action.primary {
  background: var(--accent);
  color: var(--on-accent);
}

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

.pill {
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
</style>
