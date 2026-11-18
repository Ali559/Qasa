<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import HeroRevenue from '@/components/overview/HeroRevenue.vue'
import StatTile from '@/components/kit/StatTile.vue'
import GatewayMix from '@/components/overview/GatewayMix.vue'
import { gateways as mockGateways, growthData as mockGrowth, overviewKpis as mockKpis } from '@/data/mock'
import { fetchOverview, fetchTimeseries, type OverviewKpi } from '@/api/admin'
import { errorMessage, isLiveApiEnabled } from '@/api/client'
import { downloadCsv, gatewayShares, monthlyNewPaid } from '@/lib/reports'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const overviewKpis = ref<OverviewKpi[]>([...mockKpis])
const growth = ref([...mockGrowth])
const gateways = ref([...mockGateways])
const growthLabel = ref<string | undefined>(undefined)
const gatewayDescription = ref<string | undefined>(undefined)

onMounted(async () => {
  if (!live) return
  const [kpis, series] = await Promise.allSettled([fetchOverview(), fetchTimeseries(366)])
  if (kpis.status === 'fulfilled') overviewKpis.value = kpis.value.kpis
  else toast.error('Failed to load overview KPIs', { description: errorMessage(kpis.reason) })

  if (series.status === 'fulfilled') {
    growth.value = monthlyNewPaid(series.value)
    growthLabel.value = 'New paid subscriptions by month'
    gateways.value = gatewayShares(series.value.gateway_mix)
    gatewayDescription.value = `Share of successful payments, last ${series.value.days} days`
  } else {
    toast.error('Failed to load charts', { description: errorMessage(series.reason) })
  }
})

// First KPI (revenue) leads the page with the growth chart; the rest sit in one row of compact stats.
const hero = computed(() => overviewKpis.value[0])
const tiles = computed(() => overviewKpis.value.slice(1))

function exportGrowth() {
  downloadCsv(
    'qasa-growth.csv',
    growth.value.map((d) => ({ month: d.month, [live ? 'new_paid_subscriptions' : 'subscribers']: d.subscribers })),
  )
}
</script>

<template>
  <div class="grid gap-6">
    <div class="grid gap-6 xl:grid-cols-3">
      <div v-if="hero" class="xl:col-span-2">
        <HeroRevenue :kpi="hero" :growth="growth" :chart-label="growthLabel" @export="exportGrowth" />
      </div>
      <GatewayMix :gateways="gateways" :description="gatewayDescription" />
    </div>

    <div class="grid gap-6 sm:grid-cols-2" :class="tiles.length >= 4 ? 'xl:grid-cols-4' : 'xl:grid-cols-3'">
      <StatTile v-for="kpi in tiles" :key="kpi.label" v-bind="kpi" />
    </div>
  </div>
</template>
