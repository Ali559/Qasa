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
import { fetchTelemetry, type TelemetryMetric } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const telemetryMetrics = ref<TelemetryMetric[]>([...mockTelemetry])

onMounted(async () => {
  if (!isLiveApiEnabled()) return
  try {
    const data = await fetchTelemetry()
    telemetryMetrics.value = data.metrics
  } catch {
    toast.error('Failed to load telemetry')
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

const levelColor: Record<(typeof events)[number]['level'], string> = {
  INFO: 'var(--ink-info)',
  WARN: 'var(--ink-warn)',
  DEBUG: 'var(--ink-ok)',
  ERROR: 'var(--ink-bad)',
}
const hideDebug = ref(false)

const visibleEvents = computed(() =>
  hideDebug.value ? events.filter((e) => e.level !== 'DEBUG') : events,
)

function exportEvents() {
  toast.success('Export started', {
    description: `${visibleEvents.value.length} events in this file.`,
  })
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
            class="rounded-3xl p-5 text-[#1c1b33]"
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
          <h2 class="font-heading text-xl font-bold">Live events</h2>
          <p class="text-sidebar-foreground/60 text-sm">Newest first, from every service</p>
        </div>
        <div class="flex items-center gap-2">
          <Button variant="secondary" size="sm" @click="hideDebug = !hideDebug">
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
            :key="event.time + event.message"
            class="flex flex-col gap-1.5 px-6 py-3 sm:flex-row sm:items-center sm:gap-4"
          >
            <span class="text-sidebar-foreground/55 shrink-0 tabular-nums">{{ event.time }}</span>
            <span
              class="w-16 shrink-0 rounded-md px-2 py-0.5 text-center text-[0.7rem] font-semibold text-[#1c1b33]"
              :style="{ background: levelColor[event.level] }"
            >{{ event.level }}</span>
            <span>{{ event.message }}</span>
          </li>
        </ul>
      </ScrollArea>
    </InkPanel>
  </div>
</template>
