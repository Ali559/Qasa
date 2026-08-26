<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { MessageSquareWarning, RefreshCw } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
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
  if (!value) return '—'
  return new Date(value).toLocaleString()
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex flex-wrap items-center justify-end gap-2">
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <Card>
      <CardHeader class="sr-only">
        <span>Cancellation feedback</span>
      </CardHeader>
      <CardContent class="px-0 pt-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="pl-6">Cancelled</TableHead>
              <TableHead>Account</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Expires</TableHead>
              <TableHead class="pr-6">Reason</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-if="!rows.length">
              <TableCell colspan="6" class="text-muted-foreground py-10 text-center">
                <div class="inline-flex items-center gap-2">
                  <MessageSquareWarning class="size-4" />
                  No cancellation feedback yet.
                </div>
              </TableCell>
            </TableRow>
            <TableRow v-for="row in rows" :key="row.id">
              <TableCell class="text-muted-foreground pl-6 font-mono text-xs">
                {{ formatDate(row.cancelledAt) }}
              </TableCell>
              <TableCell class="font-mono text-xs">{{ row.accountId }}</TableCell>
              <TableCell>
                <Badge variant="secondary">{{ planLabel(row.planId) }}</Badge>
              </TableCell>
              <TableCell>{{ row.amountIqd.toLocaleString() }} IQD</TableCell>
              <TableCell class="text-muted-foreground font-mono text-xs">
                {{ formatDate(row.expiresAt) }}
              </TableCell>
              <TableCell class="pr-6 max-w-sm">
                <span v-if="row.reason" class="text-sm">{{ row.reason }}</span>
                <span v-else class="text-muted-foreground text-sm italic">No reason given</span>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  </div>
</template>
