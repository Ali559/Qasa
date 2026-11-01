<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Coins, HardDrive, RefreshCw, UserRoundCheck } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import StatTile from '@/components/kit/StatTile.vue'
import { fetchAnalytics, type AnalyticsResponse } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const data = ref<AnalyticsResponse | null>(null)
const loading = ref(false)

async function load() {
  if (!isLiveApiEnabled()) {
    data.value = {
      kpis: [
        { label: 'MRR', value: '1.2M', unit: 'IQD', meta: 'demo' },
        { label: 'Paid Subscribers', value: '42', meta: 'demo' },
        { label: 'VPN Bytes (period)', value: '12.4 GB', meta: '0 throttled' },
      ],
      funnel: { intents: 100, success: 72, failed: 8, conversion_pct: 72 },
      cancellations: [{ id: '1', planId: 'premium_monthly', reason: 'Too expensive', cancelledAt: new Date().toISOString() }],
    }
    return
  }
  loading.value = true
  try {
    data.value = await fetchAnalytics()
  } catch {
    toast.error('Failed to load analytics')
  } finally {
    loading.value = false
  }
}

onMounted(load)

const kpiStyle = [
  { icon: Coins, accent: 'var(--chart-3)' },
  { icon: UserRoundCheck, accent: 'var(--chart-2)' },
  { icon: HardDrive, accent: 'var(--chart-4)' },
]
const styleFor = (i: number) => kpiStyle[i % kpiStyle.length]!

// Each funnel step is drawn as a share of checkout attempts.
const funnelSteps = computed(() => {
  const f = data.value?.funnel
  if (!f) return []
  const base = Math.max(f.intents, 1)
  return [
    { label: 'Checkouts started', value: f.intents, pct: 100, color: 'var(--chart-1)' },
    { label: 'Paid', value: f.success, pct: (f.success / base) * 100, color: 'var(--chart-4)' },
    { label: 'Failed', value: f.failed, pct: (f.failed / base) * 100, color: 'var(--chart-2)' },
  ]
})

function formatDate(value: string | null) {
  return value ? new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : 'Unknown date'
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-end">
      <Button variant="outline" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
      <StatTile
        v-for="(kpi, i) in data?.kpis ?? []"
        :key="kpi.label"
        v-bind="kpi"
        :icon="styleFor(i).icon"
        :accent="styleFor(i).accent"
      />
    </div>

    <div class="grid gap-6 xl:grid-cols-5">
      <Card v-if="data?.funnel" class="gap-6 py-6 xl:col-span-3">
        <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-4 px-6">
          <div>
            <CardTitle class="text-xl font-bold">Payment funnel</CardTitle>
            <CardDescription>From checkout to confirmed payment</CardDescription>
          </div>
          <div class="text-right">
            <p class="font-heading text-4xl leading-none font-extrabold">{{ data.funnel.conversion_pct.toFixed(1) }}%</p>
            <p class="text-muted-foreground mt-1 text-xs">of checkouts get paid</p>
          </div>
        </CardHeader>
        <CardContent class="space-y-4 px-6">
          <div v-for="step in funnelSteps" :key="step.label">
            <div class="mb-1.5 flex justify-between text-sm">
              <span class="font-medium">{{ step.label }}</span>
              <span class="font-semibold tabular-nums">{{ step.value }}</span>
            </div>
            <div class="bg-muted h-7 rounded-xl shadow-[inset_0_2px_0_var(--edge)]">
              <div
                class="h-full min-w-7 rounded-xl"
                :style="{ width: `${step.pct}%`, background: step.color, boxShadow: `inset 0 2px 0 rgb(255 255 255 / 0.3), 0 3px 0 color-mix(in oklch, ${step.color} 60%, black)` }"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card class="gap-5 py-6 xl:col-span-2">
        <CardHeader class="px-6">
          <CardTitle class="text-xl font-bold">Recent cancellations</CardTitle>
          <CardDescription>What people said when they left</CardDescription>
        </CardHeader>
        <CardContent class="space-y-3 px-6">
          <figure
            v-for="c in data?.cancellations ?? []"
            :key="c.id"
            class="bg-muted/60 rounded-2xl rounded-bl-md px-4 py-3"
          >
            <blockquote class="text-sm" :class="{ 'text-muted-foreground italic': !c.reason }">
              {{ c.reason || 'No reason given' }}
            </blockquote>
            <figcaption class="text-muted-foreground mt-2 text-xs">
              {{ c.planId }}, {{ formatDate(c.cancelledAt) }}
            </figcaption>
          </figure>
          <p v-if="!(data?.cancellations?.length)" class="text-muted-foreground text-sm">
            No one has cancelled yet.
          </p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
