<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { AlertTriangle, Database, Download, Filter, Timer, UsersRound } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import FilterChips from '@/components/kit/FilterChips.vue'
import InkPanel from '@/components/kit/InkPanel.vue'
import SegmentMeter from '@/components/kit/SegmentMeter.vue'
import StatTile from '@/components/kit/StatTile.vue'
import { events, resources, telemetryMetrics as mockTelemetry } from '@/data/mock'
import { fetchAudit, fetchTelemetry, type AuditEntry, type TelemetryMetric } from '@/api/admin'
import { errorMessage, isLiveApiEnabled } from '@/api/client'
import { downloadCsv } from '@/lib/reports'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const telemetryMetrics = ref<TelemetryMetric[]>([...mockTelemetry])

type Level = 'INFO' | 'WARN' | 'DEBUG' | 'ERROR'
type EventRow = { key: string; time: string; level: Level; message: string; who?: string }

const mockEvents: EventRow[] = events.map((e) => ({ ...e, key: e.time + e.message }))
const auditEvents = ref<EventRow[]>([])
const auditNext = ref<string | null>(null)
const auditLoading = ref(false)

// Destructive staff actions read as warnings in the feed.
const WARN_ACTIONS = /^(block|retire|revoke|unlink|rotate)/

function toEvent(a: AuditEntry): EventRow {
  const d = new Date(a.created_at)
  const detail = a.payload
    ? Object.entries(a.payload)
        .filter(([, v]) => v !== null && v !== undefined && v !== '')
        .map(([k, v]) => `${k}=${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
        .join(' ')
    : ''
  return {
    key: a.id,
    time: d.toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' }),
    level: WARN_ACTIONS.test(a.action) ? 'WARN' : 'INFO',
    message: `${a.action.replace(/_/g, ' ')} ${a.target_type} ${a.target_id}${detail ? ` (${detail})` : ''}`,
    who: a.admin_email ?? a.admin_id,
  }
}

async function loadAudit(append = false) {
  auditLoading.value = true
  try {
    const res = await fetchAudit({ limit: 50, before: append ? auditNext.value : undefined })
    const rows = res.entries.map(toEvent)
    auditEvents.value = append ? [...auditEvents.value, ...rows] : rows
    auditNext.value = res.next_before
  } catch (err) {
    toast.error('Failed to load the audit log', { description: errorMessage(err) })
  } finally {
    auditLoading.value = false
  }
}

onMounted(async () => {
  if (!live) return
  loadAudit()
  try {
    const data = await fetchTelemetry()
    telemetryMetrics.value = data.metrics
  } catch (err) {
    toast.error('Failed to load telemetry', { description: errorMessage(err) })
  }
})

const metricStyle = [
  { icon: UsersRound, accent: 'var(--chart-2)' },
  { icon: Timer, accent: 'var(--chart-3)' },
  { icon: AlertTriangle, accent: 'var(--chart-4)' },
  { icon: Database, accent: 'var(--chart-5)' },
]
const styleFor = (i: number) => metricStyle[i % metricStyle.length]!

const region = ref<'mena' | 'global'>('mena')
const regionOptions = [
  { value: 'mena' as const, label: 'MENA' },
  { value: 'global' as const, label: 'Global' },
]
const regionStats = computed(() =>
  region.value === 'mena'
    ? [
        { label: 'Threats (1h)', value: '12,491', accent: 'var(--chart-2)' },
        { label: 'Active connections', value: '45,103', accent: 'var(--chart-4)' },
        { label: 'Average latency', value: '28 ms', accent: 'var(--chart-3)' },
      ]
    : [
        { label: 'Threats (1h)', value: '28,104', accent: 'var(--chart-2)' },
        { label: 'Active connections', value: '91,220', accent: 'var(--chart-4)' },
        { label: 'Average latency', value: '42 ms', accent: 'var(--chart-3)' },
      ],
)

const levelColor: Record<Level, string> = {
  INFO: 'var(--ink-info)',
  WARN: 'var(--ink-warn)',
  DEBUG: 'var(--ink-ok)',
  ERROR: 'var(--ink-bad)',
}
const hideDebug = ref(false)

const visibleEvents = computed(() => {
  const list = live ? auditEvents.value : mockEvents
  return hideDebug.value ? list.filter((e) => e.level !== 'DEBUG') : list
})

function exportEvents() {
  if (visibleEvents.value.length === 0) {
    toast.error('Nothing to export')
    return
  }
  downloadCsv(
    'qasa-events.csv',
    visibleEvents.value.map((e) => ({ time: e.time, level: e.level, message: e.message, admin: e.who ?? '' })),
  )
}
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
      <StatTile
        v-for="(m, i) in telemetryMetrics"
        :key="m.label"
        :label="m.label"
        :value="m.value"
        :meta="m.meta"
        :icon="styleFor(i).icon"
        :accent="styleFor(i).accent"
      />
    </div>

    <div class="grid gap-6 xl:grid-cols-3">
      <Card class="gap-6 py-6 xl:col-span-2">
        <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-3 px-6">
          <div>
            <CardTitle class="text-xl font-bold">Traffic and threats</CardTitle>
            <CardDescription>Last hour, by region</CardDescription>
          </div>
          <FilterChips v-model="region" :options="regionOptions" label="Region" />
        </CardHeader>
        <CardContent class="grid gap-4 px-6 sm:grid-cols-3">
          <div
            v-for="stat in regionStats"
            :key="stat.label"
            class="rounded-3xl p-5 text-ink"
            :style="{ background: stat.accent, boxShadow: `inset 0 2px 0 rgb(255 255 255 / 0.35), 0 4px 0 color-mix(in oklch, ${stat.accent} 60%, black)` }"
          >
            <p class="text-sm font-semibold opacity-75">{{ stat.label }}</p>
            <p class="font-heading mt-3 text-4xl leading-none font-extrabold tabular-nums">{{ stat.value }}</p>
          </div>
          <p class="text-muted-foreground text-sm sm:col-span-3">
            Latency under 50 ms is healthy; 50 to 150 ms means a node is under pressure.
          </p>
        </CardContent>
      </Card>

      <Card class="gap-6 py-6">
        <CardHeader class="px-6">
          <CardTitle class="text-xl font-bold">Cluster resources</CardTitle>
          <CardDescription>Capacity used across all servers</CardDescription>
        </CardHeader>
        <CardContent class="space-y-5 px-6">
          <div v-for="r in resources" :key="r.label" class="space-y-2">
            <div class="flex justify-between text-sm">
              <span class="text-muted-foreground">{{ r.label }}</span>
              <span class="font-semibold tabular-nums">{{ r.value }}</span>
            </div>
            <SegmentMeter :value="r.width" :label="`${r.label} ${r.value}`" size="sm" />
          </div>
        </CardContent>
      </Card>
    </div>

    <InkPanel>
      <div class="flex flex-wrap items-center justify-between gap-3 px-6 pt-5 pb-4">
        <div>
          <h2 class="font-heading text-xl font-bold">{{ live ? 'Staff activity' : 'Live events' }}</h2>
          <p class="text-sidebar-foreground/60 text-sm">
            {{ live ? 'Admin audit log, newest first' : 'Newest first, from every service' }}
          </p>
        </div>
        <div class="flex items-center gap-2">
          <Button v-if="!live" variant="secondary" size="sm" @click="hideDebug = !hideDebug">
            <Filter />
            {{ hideDebug ? 'Show debug' : 'Hide debug' }}
          </Button>
          <Button variant="secondary" size="sm" @click="exportEvents">
            <Download />
            Export
          </Button>
        </div>
      </div>
      <ScrollArea class="h-[300px]">
        <ul class="divide-sidebar-border divide-y border-t border-sidebar-border font-mono text-xs">
          <li
            v-for="event in visibleEvents"
            :key="event.key"
            class="flex flex-col gap-1.5 px-6 py-3 sm:flex-row sm:items-center sm:gap-4"
          >
            <span class="text-sidebar-foreground/55 shrink-0 tabular-nums">{{ event.time }}</span>
            <span
              class="w-16 shrink-0 rounded-md px-2 py-0.5 text-center text-[0.7rem] font-semibold text-ink"
              :style="{ background: levelColor[event.level] }"
            >{{ event.level }}</span>
            <span class="min-w-0 break-words">{{ event.message }}</span>
            <span v-if="event.who" class="text-sidebar-foreground/55 shrink-0 sm:ml-auto">{{ event.who }}</span>
          </li>
          <li v-if="live && !auditLoading && visibleEvents.length === 0" class="text-sidebar-foreground/60 px-6 py-6">
            No staff actions recorded yet.
          </li>
          <li v-if="live && auditNext" class="px-6 py-3">
            <Button variant="secondary" size="sm" :disabled="auditLoading" @click="loadAudit(true)">Load older</Button>
          </li>
        </ul>
      </ScrollArea>
    </InkPanel>
  </div>
</template>
