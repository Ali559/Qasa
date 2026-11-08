<script setup lang="ts">
// iOS `PasswordHealthView` + `PasswordHealthIssuesView`: the half-circle score gauge
// (animated 1.1 s, off with reduced motion), four stat cards, the locked breach card on
// Free, "How scoring works", and the issue lists (Reused / Weak / Breached) one level in.
import { ArrowLeftRight, Lock, ShieldCheck, TriangleAlert, ShieldAlert } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import FilterChip from '@/components/FilterChip.vue'
import PageFrame from '@/components/PageFrame.vue'
import { nav, selectTab } from '@/nav'
import { useT } from '@/prefs'
import { session } from '@/vault/session'
import { toolsApi, type HealthReport } from '@/vault/tools'
import VaultRow from '../vault/VaultRow.vue'

defineEmits<{ back: [] }>()
const t = useT()

type IssueTab = 'reused' | 'weak' | 'breached'
const report = ref<HealthReport | null>(null)
const issues = ref<IssueTab | null>(null)
const shownScore = ref(0)

const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

function animateTo(target: number) {
  if (reduceMotion) {
    shownScore.value = target
    return
  }
  const start = performance.now()
  const step = (now: number) => {
    const p = Math.min((now - start) / 1100, 1)
    const eased = 1 - Math.pow(1 - p, 3)
    shownScore.value = Math.round(target * eased)
    if (p < 1) requestAnimationFrame(step)
  }
  shownScore.value = 0
  requestAnimationFrame(step)
}

async function refresh() {
  report.value = await toolsApi.health()
  animateTo(report.value.score)
}

onMounted(refresh)
watch(() => session.items, refresh)

const breachLocked = computed(() => report.value?.breachLocked ?? true)
const scoreColor = computed(() => (shownScore.value >= 80 ? 'var(--accent)' : shownScore.value >= 50 ? 'var(--gold)' : 'var(--danger)'))
const scoreLabel = computed(() => {
  const s = shownScore.value
  return t(s >= 90 ? 'health.excellent' : s >= 75 ? 'health.strong' : s >= 50 ? 'health.fair' : 'health.needsAttention')
})

// Gauge geometry: an upper semicircle, 14pt stroke, in a 220 × 118 box.
const R = 103
const ARC = Math.PI * R
const arcPath = `M ${110 - R} 111 A ${R} ${R} 0 0 1 ${110 + R} 111`

const idsFor = computed<Record<IssueTab, string[]>>(() => ({
  reused: report.value?.reusedIds ?? [],
  weak: report.value?.weakIds ?? [],
  breached: report.value?.breachedIds ?? [],
}))

const issueRows = computed(() => {
  if (!issues.value) return []
  const byId = new Map(session.items.map((i) => [i.id, i]))
  return idsFor.value[issues.value].map((id) => byId.get(id)).filter((r) => !!r)
})

const EMPTY = {
  reused: { title: 'health.noReusedTitle', body: 'health.noReusedBody', icon: ArrowLeftRight },
  weak: { title: 'health.noWeakTitle', body: 'health.noWeakBody', icon: TriangleAlert },
  breached: { title: 'breached.noneTitle', body: 'breached.noneBody', icon: ShieldCheck },
}

function openItem(id: string) {
  nav.selectedItemId = id
  selectTab('passwords')
}
</script>

<template>
  <!-- Issues list -->
  <PageFrame v-if="issues" :title="t('health.issuesTitle')" back :gap="16" @back="issues = null">
    <div class="chips">
      <FilterChip v-for="tab in ['reused', 'weak', 'breached'] as IssueTab[]" :key="tab" :title="t(`health.${tab}`)" :selected="issues === tab" @click="issues = tab" />
    </div>
    <CardContainer v-if="issues === 'breached' && breachLocked" :padding="20">
      <div class="stack">
        <h2 class="locked"><ShieldAlert :size="20" />{{ t('breached.lockedTitle') }}</h2>
        <p class="muted">{{ t('breached.lockedBody') }}</p>
      </div>
    </CardContainer>
    <div v-else-if="issueRows.length === 0" class="empty">
      <component :is="EMPTY[issues].icon" :size="40" />
      <strong>{{ t(EMPTY[issues].title) }}</strong>
      <span>{{ t(EMPTY[issues].body) }}</span>
    </div>
    <VaultRow
      v-for="row in issueRows"
      v-else
      :key="row!.id"
      :row="row!"
      :selected="false"
      @select="openItem(row!.id)"
      @edit="openItem(row!.id)"
    />
  </PageFrame>

  <!-- Overview -->
  <PageFrame v-else :title="t('nav.passwordHealth')" back :gap="28" @back="$emit('back')">
    <div class="gauge-wrap">
      <div class="gauge">
        <svg width="220" height="118" viewBox="0 0 220 118" aria-hidden="true">
          <path :d="arcPath" class="track" />
          <path :d="arcPath" class="value" :style="{ stroke: scoreColor }" :stroke-dasharray="ARC" :stroke-dashoffset="ARC * (1 - shownScore / 100)" />
        </svg>
        <div class="score" role="img" :aria-label="t('a11y.healthScore', report?.score ?? 0)">
          <span class="number" :style="{ color: scoreColor }">{{ shownScore }}</span>
          <span class="out-of">{{ t('health.outOf100') }}</span>
        </div>
      </div>
      <span class="label">{{ scoreLabel }}</span>
      <p v-if="breachLocked" class="muted center">{{ t('health.breachedLocked') }}</p>
    </div>

    <div class="stats">
      <button type="button" class="stat" @click="issues = 'reused'">
        <span class="value" style="color: var(--accent)">{{ report?.totalPasswords ?? 0 }}</span>
        <span class="title">{{ t('health.passwords') }}</span>
      </button>
      <button type="button" class="stat" @click="issues = 'reused'">
        <span class="value" style="color: var(--gold)">{{ report?.reusedCount ?? 0 }}</span>
        <span class="title">{{ t('health.reused') }}</span>
      </button>
      <button type="button" class="stat" @click="issues = 'weak'">
        <span class="value" style="color: var(--danger)">{{ report?.weakCount ?? 0 }}</span>
        <span class="title">{{ t('health.weak') }}</span>
      </button>
      <button v-if="breachLocked" type="button" class="stat" @click="issues = 'breached'">
        <span class="value lock"><Lock :size="24" /></span>
        <span class="title">{{ t('health.breached') }}</span>
      </button>
      <button v-else type="button" class="stat" @click="issues = 'breached'">
        <span class="value" style="color: var(--danger)">{{ report?.breachCheckFailed ? '—' : (report?.breachedCount ?? 0) }}</span>
        <span class="title">{{ t('health.breached') }}</span>
      </button>
    </div>

    <CardContainer v-if="breachLocked" :padding="20">
      <div class="stack">
        <h2 class="locked"><ShieldAlert :size="20" />{{ t('breached.lockedTitle') }}</h2>
        <p class="muted">{{ t('breached.lockedBody') }}</p>
      </div>
    </CardContainer>

    <CardContainer>
      <div class="stack">
        <h3 class="how">{{ t('health.howScoring') }}</h3>
        <p class="muted">{{ t('health.howScoringBody') }}</p>
      </div>
    </CardContainer>
  </PageFrame>
</template>

<style scoped>
.gauge-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding-block: 8px;
}

.gauge {
  position: relative;
  width: 220px;
  height: 118px;
  margin-top: 8px;
}

.track,
.value {
  fill: none;
  stroke-width: 14;
  stroke-linecap: round;
}

.track {
  stroke: var(--stroke);
}

.score {
  position: absolute;
  inset-inline: 0;
  bottom: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.number {
  font-size: 44px;
  font-weight: 700;
  line-height: 1;
}

.out-of {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.label {
  font-size: 18px;
  font-weight: 600;
}

.stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 18px 0;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
  cursor: pointer;
}

.stat:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--stroke));
}

.stat .value {
  font-size: 28px;
  font-weight: 700;
}

.stat .lock {
  display: grid;
  place-items: center;
  height: 34px;
  color: var(--text-secondary);
}

.stat .title {
  font-size: 13px;
  color: var(--text-secondary);
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.locked {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.how {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.center {
  text-align: center;
}

.chips {
  display: flex;
  gap: 8px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 40px;
  color: var(--text-secondary);
  text-align: center;
}

.empty strong {
  color: var(--text-primary);
  font-size: 17px;
}

@media (prefers-reduced-motion: no-preference) {
  .value {
    transition: stroke 300ms;
  }
}
</style>
