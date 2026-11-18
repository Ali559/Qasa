<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
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
        { label: 'Monthly recurring revenue', value: '1.2M', unit: 'IQD', meta: '+8% vs last month', tone: 'up' },
        { label: 'Paid subscribers', value: '42', meta: '+3 vs last month', tone: 'up' },
        { label: 'VPN data this period', value: '12.4', unit: 'GB', meta: 'No one throttled' },
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


// Each funnel step is drawn as a share of checkout attempts.
const funnelSteps = computed(() => {
  const f = data.value?.funnel
  if (!f) return []
  const base = Math.max(f.intents, 1)
  return [
    { label: 'Checkouts started', value: f.intents, pct: 100, color: 'var(--muted-foreground)' },
    { label: 'Paid', value: f.success, pct: (f.success / base) * 100, color: 'var(--primary)' },
    { label: 'Failed', value: f.failed, pct: (f.failed / base) * 100, color: 'var(--bad)' },
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
        v-for="kpi in data?.kpis ?? []"
        :key="kpi.label"
        v-bind="kpi"
      />
    </div>

    <div class="grid gap-6 xl:grid-cols-5">
      <Card v-if="data?.funnel" class="gap-6 py-6 xl:col-span-3">
        <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-4 px-6">
          <div>
            <CardTitle>Payment funnel</CardTitle>
            <CardDescription>From checkout to confirmed payment</CardDescription>
          </div>
          <div class="text-right">
            <p class="text-[1.75rem] leading-none font-medium tabular-nums">{{ data.funnel.conversion_pct.toFixed(1) }}%</p>
            <p class="text-muted-foreground mt-1 text-xs">of checkouts get paid</p>
          </div>
        </CardHeader>
        <CardContent class="space-y-4 px-6">
          <div v-for="step in funnelSteps" :key="step.label">
            <div class="mb-2 flex justify-between text-sm">
              <span>{{ step.label }}</span>
              <span class="tabular-nums">{{ step.value }}</span>
            </div>
            <div class="bg-muted h-1.5 overflow-hidden rounded-full">
              <div class="h-full rounded-full" :style="{ width: `${step.pct}%`, background: step.color }" />
            </div>
          </div>
        </CardContent>
      </Card>

      <Card class="gap-5 py-6 xl:col-span-2">
        <CardHeader class="px-6">
          <CardTitle>Recent cancellations</CardTitle>
          <CardDescription>What people said when they left</CardDescription>
        </CardHeader>
        <CardContent class="divide-y px-6">
          <figure
            v-for="c in data?.cancellations ?? []"
            :key="c.id"
            class="py-3 first:pt-0"
          >
            <blockquote class="text-sm" :class="{ 'text-muted-foreground': !c.reason }">
              {{ c.reason || 'No reason given' }}
            </blockquote>
            <figcaption class="text-muted-foreground mt-2 text-xs">
              {{ c.planId }}, {{ formatDate(c.cancelledAt) }}
            </figcaption>
          </figure>
          <p v-if="!(data?.cancellations?.length)" class="text-muted-foreground text-sm">
            No one has cancelled yet
          </p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
