<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CurveType } from '@unovis/ts'
import { VisArea, VisAxis, VisLine, VisXYContainer } from '@unovis/vue'
import { Download, TrendingUp } from '@lucide/vue'
import FadeIn from '@/components/FadeIn.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer, type ChartConfig } from '@/components/ui/chart'
import { Progress } from '@/components/ui/progress'
import { Separator } from '@/components/ui/separator'
import { gateways, growthData, overviewKpis as mockKpis } from '@/data/mock'
import { fetchOverview, type OverviewKpi } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const overviewKpis = ref<OverviewKpi[]>([...mockKpis])

onMounted(async () => {
  if (!isLiveApiEnabled()) return
  try {
    const data = await fetchOverview()
    overviewKpis.value = data.kpis
  } catch {
    toast.error('Failed to load overview KPIs')
  }
})

const chartConfig = {
  subscribers: { label: 'Subscribers', color: 'var(--chart-1)' },
} satisfies ChartConfig

const x = (_: (typeof growthData)[number], i: number) => i
const y = (d: (typeof growthData)[number]) => d.subscribers

function exportGrowth() {
  toast.success('Export started', { description: 'Subscription growth CSV is preparing.' })
}
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <FadeIn v-for="(kpi, i) in overviewKpis" :key="kpi.label" :delay="i * 0.05">
        <Card class="overflow-hidden transition-shadow hover:shadow-md">
          <CardHeader class="pb-2">
            <CardDescription class="font-mono text-[11px] tracking-wider uppercase">
              {{ kpi.label }}
            </CardDescription>
            <CardTitle class="flex items-baseline gap-2 text-3xl font-semibold tracking-tight">
              {{ kpi.value }}
              <span v-if="kpi.unit" class="text-muted-foreground text-sm font-normal">{{ kpi.unit }}</span>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <Progress v-if="kpi.progress !== undefined" :model-value="kpi.progress" class="h-2" />
            <div v-else class="flex items-center gap-1.5 text-sm">
              <TrendingUp v-if="kpi.tone === 'up'" class="text-primary size-3.5" />
              <Badge :variant="kpi.tone === 'up' ? 'secondary' : 'outline'" class="font-mono text-[11px]">
                {{ kpi.meta }}
              </Badge>
            </div>
          </CardContent>
        </Card>
      </FadeIn>
    </div>

    <div class="grid gap-4 xl:grid-cols-3">
      <FadeIn :delay="0.15" class="xl:col-span-2">
        <Card class="h-full">
          <CardHeader class="flex flex-row items-start justify-between gap-3">
            <div>
              <CardTitle>Subscription Growth</CardTitle>
              <CardDescription>Trailing 6 months</CardDescription>
            </div>
            <Button variant="outline" size="icon" @click="exportGrowth">
              <Download />
            </Button>
          </CardHeader>
          <CardContent>
            <ChartContainer :config="chartConfig" class="aspect-[16/9] w-full" cursor>
              <VisXYContainer :data="growthData" :margin="{ top: 8, right: 8, bottom: 24, left: 28 }">
                <VisArea
                  :x="x"
                  :y="y"
                  :color="() => 'var(--chart-1)'"
                  :opacity="0.2"
                  :curve-type="CurveType.MonotoneX"
                />
                <VisLine
                  :x="x"
                  :y="y"
                  :color="() => 'var(--chart-1)'"
                  :curve-type="CurveType.MonotoneX"
                  :line-width="2.5"
                />
                <VisAxis type="x" :tick-format="(i: number) => growthData[i]?.month ?? ''" :tick-line="false" :domain-line="false" />
                <VisAxis type="y" :tick-line="false" :domain-line="false" :grid-line="true" />
              </VisXYContainer>
            </ChartContainer>
          </CardContent>
        </Card>
      </FadeIn>

      <FadeIn :delay="0.2">
        <Card class="h-full">
          <CardHeader>
            <CardTitle>Gateway Distribution</CardTitle>
            <CardDescription>Revenue source breakdown</CardDescription>
          </CardHeader>
          <CardContent class="space-y-5">
            <div class="flex items-end justify-center gap-2 py-4">
              <div class="text-center">
                <div class="text-4xl font-semibold tracking-tight">3</div>
                <div class="text-muted-foreground font-mono text-xs tracking-wide uppercase">Active Gateways</div>
              </div>
            </div>
            <div class="bg-muted flex h-3 overflow-hidden rounded-full">
              <div
                v-for="g in gateways"
                :key="g.name"
                class="h-full transition-all"
                :style="{ width: `${g.value}%`, background: g.color }"
              />
            </div>
            <Separator />
            <ul class="space-y-3">
              <li
                v-for="g in gateways"
                :key="g.name"
                class="bg-muted/40 flex items-center justify-between rounded-lg border px-3 py-2.5"
              >
                <span class="flex items-center gap-2 text-sm">
                  <i class="size-2.5 rounded-full" :style="{ background: g.color }" />
                  {{ g.name }}
                </span>
                <span class="text-muted-foreground font-mono text-xs">{{ g.value }}%</span>
              </li>
            </ul>
          </CardContent>
        </Card>
      </FadeIn>
    </div>
  </div>
</template>
