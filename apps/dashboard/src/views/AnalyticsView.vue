<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-end">
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <Card v-for="kpi in data?.kpis ?? []" :key="kpi.label">
        <CardHeader class="pb-2">
          <p class="text-muted-foreground font-mono text-[11px] tracking-wider uppercase">
            {{ kpi.label }}
          </p>
          <CardTitle class="text-2xl">
            {{ kpi.value }}
            <span v-if="kpi.unit" class="text-muted-foreground text-sm font-normal">{{ kpi.unit }}</span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p class="text-muted-foreground text-xs">{{ kpi.meta }}</p>
        </CardContent>
      </Card>
    </div>

    <Card v-if="data?.funnel">
      <CardHeader>
        <CardTitle>Payment funnel</CardTitle>
      </CardHeader>
      <CardContent class="flex flex-wrap gap-4 text-sm">
        <Badge variant="secondary">Intents {{ data.funnel.intents }}</Badge>
        <Badge variant="outline" class="border-emerald-400/40 text-emerald-300">
          Success {{ data.funnel.success }}
        </Badge>
        <Badge variant="outline" class="border-red-400/40 text-red-300">
          Failed {{ data.funnel.failed }}
        </Badge>
        <span class="text-muted-foreground">
          Conversion {{ data.funnel.conversion_pct.toFixed(1) }}%
        </span>
      </CardContent>
    </Card>

    <Card>
      <CardHeader>
        <CardTitle>Recent cancellations</CardTitle>
      </CardHeader>
      <CardContent class="space-y-3">
        <div
          v-for="c in data?.cancellations ?? []"
          :key="c.id"
          class="bg-muted/40 rounded-lg border px-3 py-2 text-sm"
        >
          <div class="flex items-center justify-between gap-2">
            <Badge variant="secondary">{{ c.planId }}</Badge>
            <span class="text-muted-foreground font-mono text-xs">{{ c.cancelledAt }}</span>
          </div>
          <p class="mt-1">{{ c.reason || 'No reason given' }}</p>
        </div>
        <p v-if="!(data?.cancellations?.length)" class="text-muted-foreground text-sm">
          No cancellations yet.
        </p>
      </CardContent>
    </Card>
  </div>
</template>
