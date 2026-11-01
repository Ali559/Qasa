<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { MessageSquareWarning, RefreshCw } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import StatusPill from '@/components/kit/StatusPill.vue'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { fetchCancellations, type CancellationRow } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const rows = ref<CancellationRow[]>([])
const loading = ref(false)

async function load() {
  if (!isLiveApiEnabled()) {
    rows.value = [
      {
        id: 'demo-1',
        accountId: 'acc_demo',
        planId: 'premium_monthly',
        amountIqd: 10000,
        reason: 'Too expensive for now',
        cancelledAt: new Date().toISOString(),
        expiresAt: new Date(Date.now() + 86400000 * 20).toISOString(),
      },
    ]
    return
  }
  loading.value = true
  try {
    const data = await fetchCancellations()
    rows.value = data.cancellations
  } catch {
    toast.error('Failed to load cancellations')
  } finally {
    loading.value = false
  }
}

function planLabel(planId: string) {
  if (planId === 'premium_yearly') return 'Yearly'
  return 'Monthly'
}

function formatDate(value: string | null) {
  if (!value) return 'Unknown'
  return new Date(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

onMounted(load)
</script>

<template>
  <div class="space-y-5">
    <div class="flex justify-end">
      <Button variant="outline" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <Card class="gap-0 py-0">
      <CardContent class="px-0">
        <Table>
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="h-12 pl-6">Reason</TableHead>
              <TableHead>Account</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead class="text-right">Amount</TableHead>
              <TableHead>Cancelled</TableHead>
              <TableHead class="pr-6">Access ends</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="!rows.length" class="hover:bg-transparent">
              <TableCell colspan="6" class="py-14 text-center">
                <span class="bg-chart-4 mx-auto mb-3 grid size-12 -rotate-6 place-items-center rounded-2xl text-[#1c1b33] shadow-[0_3px_0_color-mix(in_oklch,var(--chart-4)_60%,black)]">
                  <MessageSquareWarning class="size-5" />
                </span>
                <p class="font-heading text-lg font-bold">No cancellations yet</p>
                <p class="text-muted-foreground text-sm">Reasons appear here when someone cancels Premium.</p>
              </TableCell>
            </TableRow>
            <TableRow v-for="row in rows" :key="row.id">
              <TableCell class="max-w-sm py-4 pl-6 whitespace-normal">
                <span
                  class="bg-muted/70 inline-block rounded-2xl rounded-bl-md px-3.5 py-2 text-sm"
                  :class="{ 'text-muted-foreground italic': !row.reason }"
                >{{ row.reason || 'No reason given' }}</span>
              </TableCell>
              <TableCell class="font-mono text-xs">{{ row.accountId }}</TableCell>
              <TableCell><StatusPill tone="info" :dot="false">{{ planLabel(row.planId) }}</StatusPill></TableCell>
              <TableCell class="text-right">
                <span class="font-heading text-base font-bold tabular-nums">{{ row.amountIqd.toLocaleString() }}</span>
                <span class="text-muted-foreground ml-1 text-xs">IQD</span>
              </TableCell>
              <TableCell class="text-muted-foreground text-sm">{{ formatDate(row.cancelledAt) }}</TableCell>
              <TableCell class="pr-6 text-sm">{{ formatDate(row.expiresAt) }}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
