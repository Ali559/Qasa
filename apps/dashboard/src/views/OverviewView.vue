<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Gauge, Smartphone, UserRoundCheck } from '@lucide/vue'
import HeroRevenue from '@/components/overview/HeroRevenue.vue'
import StatTile from '@/components/kit/StatTile.vue'
import GatewayMix from '@/components/overview/GatewayMix.vue'
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

// First KPI (revenue) leads the page; the rest become accent tiles in order.
const hero = computed(() => overviewKpis.value[0])
const tiles = computed(() => overviewKpis.value.slice(1))
const tileStyle = [
  { icon: UserRoundCheck, accent: 'var(--chart-2)' },
  { icon: Smartphone, accent: 'var(--chart-4)' },
  { icon: Gauge, accent: 'var(--chart-3)' },
]
const styleFor = (i: number) => tileStyle[i % tileStyle.length]!

function exportGrowth() {
  toast.success('Export started', { description: 'Subscription growth CSV is preparing.' })
}
</script>

<template>
  <div class="grid gap-6 xl:grid-cols-12">
    <div v-if="hero" class="xl:col-span-8 xl:row-span-2">
      <HeroRevenue :kpi="hero" :growth="growthData" @export="exportGrowth" />
    </div>

    <div
      v-for="(kpi, i) in tiles"
      :key="kpi.label"
      class="xl:col-span-4"
    >
      <StatTile v-bind="kpi" :icon="styleFor(i).icon" :accent="styleFor(i).accent" />
    </div>

    <div class="xl:col-span-8">
      <GatewayMix :gateways="gateways" />
    </div>
  </div>
</template>
