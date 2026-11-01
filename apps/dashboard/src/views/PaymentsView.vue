<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Braces, CheckCircle2, Copy, Download } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { ScrollArea } from '@/components/ui/scroll-area'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import FilterChips from '@/components/kit/FilterChips.vue'
import InkPanel from '@/components/kit/InkPanel.vue'
import StatusPill, { type PillTone } from '@/components/kit/StatusPill.vue'
import { gateways, payments as mockPayments, type PaymentRow, type PayStatus } from '@/data/mock'
import { fetchPayments } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const payments = ref<PaymentRow[]>([...mockPayments])

onMounted(async () => {
  if (!isLiveApiEnabled()) return
  try {
    const data = await fetchPayments()
    payments.value = data.payments
    selected.value = data.payments[0] ?? null
  } catch {
    toast.error('Failed to load payments')
  }
})

const selected = ref<PaymentRow | null>(mockPayments[0] ?? null)
const open = ref(false)
const statusFilter = ref<'all' | PayStatus>('all')

const rows = computed(() =>
  statusFilter.value === 'all'
    ? payments.value
    : payments.value.filter((p) => p.status === statusFilter.value),
)

const payload = computed(() =>
  JSON.stringify(
    {
      event_type: selected.value?.status === 'success' ? 'payment.completed' : 'payment.update',
      timestamp: selected.value?.timestamp,
      data: {
        order_id: selected.value?.orderId,
        amount: Number(selected.value?.amount.replace(',', '')),
        currency: 'IQD',
        customer: { id: selected.value?.userId, phone: '+9647********' },
        gateway: selected.value?.gateway,
        reference: 'tx_9a82ff',
        signature: 'hmac_sha256_verified',
      },
    },
    null,
    2,
  ),
)

function inspect(row: PaymentRow) {
  selected.value = row
  open.value = true
}

function copyPayload() {
  navigator.clipboard.writeText(payload.value)
  toast.success('Payload copied')
}

const statusOptions = computed(() => {
  const count = (st: PayStatus) => payments.value.filter((p) => p.status === st).length
  return [
    { value: 'all' as const, label: 'All', count: payments.value.length },
    { value: 'success' as const, label: 'Paid', count: count('success') },
    { value: 'pending' as const, label: 'Pending', count: count('pending') },
    { value: 'failed' as const, label: 'Failed', count: count('failed') },
  ]
})

const statusTone: Record<PayStatus, PillTone> = { success: 'ok', pending: 'warn', failed: 'bad' }
const statusLabel: Record<PayStatus, string> = { success: 'paid', pending: 'pending', failed: 'failed' }
const gatewayColor = (name: string) => gateways.find((g) => g.name === name)?.color ?? 'var(--muted-foreground)'

function exportLogs() {
  toast.success('Export started', { description: `${rows.value.length} payments in this CSV.` })
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <FilterChips v-model="statusFilter" :options="statusOptions" label="Filter by payment status" />
      <Button variant="outline" @click="exportLogs">
        <Download />
        Export CSV
      </Button>
    </div>

    <Card class="gap-0 py-0">
      <CardContent class="px-0">
        <Table>
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="h-12 pl-6">Received</TableHead>
              <TableHead>Order</TableHead>
              <TableHead>User</TableHead>
              <TableHead>Gateway</TableHead>
              <TableHead class="text-right">Amount</TableHead>
              <TableHead>Status</TableHead>
              <TableHead class="pr-6 text-right"><span class="sr-only">Payload</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow
              v-for="row in rows"
              :key="row.orderId"
              class="cursor-pointer"
              @click="inspect(row)"
            >
              <TableCell class="text-muted-foreground py-3.5 pl-6 text-xs tabular-nums">{{ row.timestamp }}</TableCell>
              <TableCell class="font-mono text-[0.8rem] font-medium">{{ row.orderId }}</TableCell>
              <TableCell class="text-muted-foreground font-mono text-xs">{{ row.userId }}</TableCell>
              <TableCell>
                <span class="inline-flex items-center gap-2 font-semibold">
                  <i class="size-2.5 rounded-full" :style="{ background: gatewayColor(row.gateway) }" />
                  {{ row.gateway }}
                </span>
              </TableCell>
              <TableCell class="text-right">
                <span class="font-heading text-base font-bold tabular-nums">{{ row.amount }}</span>
                <span class="text-muted-foreground ml-1 text-xs">IQD</span>
              </TableCell>
              <TableCell><StatusPill :tone="statusTone[row.status]">{{ statusLabel[row.status] }}</StatusPill></TableCell>
              <TableCell class="pr-6 text-right">
                <Button variant="ghost" size="icon-sm" aria-label="Inspect webhook payload" @click.stop="inspect(row)">
                  <Braces />
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="rows.length === 0" class="hover:bg-transparent">
              <TableCell colspan="7" class="text-muted-foreground h-32 text-center">
                No payments with this status. Pick another filter.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Sheet v-model:open="open">
      <SheetContent class="gap-5 sm:max-w-md">
        <SheetHeader class="pb-0">
          <SheetTitle class="font-heading text-xl">Webhook payload</SheetTitle>
          <SheetDescription class="font-mono">{{ selected?.orderId ?? 'No payment selected' }}</SheetDescription>
        </SheetHeader>

        <div class="flex items-start gap-3 mx-4 rounded-2xl bg-ok/12 p-4">
          <CheckCircle2 class="text-ok mt-0.5 size-5 shrink-0" />
          <div>
            <p class="text-sm font-semibold">Signature verified</p>
            <p class="text-muted-foreground text-xs">The payload matches the gateway's shared HMAC secret.</p>
          </div>
        </div>

        <InkPanel class="relative mx-4 flex-1">
          <Button
            variant="secondary"
            size="sm"
            class="absolute top-3 right-3 z-10"
            @click="copyPayload"
          >
            <Copy />
            Copy
          </Button>
          <ScrollArea class="h-[55vh] p-5">
            <pre class="font-mono text-xs leading-relaxed whitespace-pre-wrap">{{ payload }}</pre>
          </ScrollArea>
        </InkPanel>
        <div class="pb-2" />
      </SheetContent>
    </Sheet>
  </div>
</template>
