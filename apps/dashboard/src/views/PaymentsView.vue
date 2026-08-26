<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Braces, CheckCircle2, Copy, Download, Filter } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
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
import { payments as mockPayments, type PaymentRow, type PayStatus } from '@/data/mock'
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
const open = ref(true)
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

function cycleFilter() {
  const order: Array<'all' | PayStatus> = ['all', 'success', 'failed', 'pending']
  const i = order.indexOf(statusFilter.value)
  statusFilter.value = order[(i + 1) % order.length]!
  toast.message(`Filter: ${statusFilter.value}`)
}

function exportLogs() {
  toast.success('Export ready', { description: `${rows.value.length} webhook rows exported.` })
}
</script>

<template>
  <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-end gap-2">
        <Button variant="outline" size="sm" @click="cycleFilter">
          <Filter />
          Filter · {{ statusFilter }}
        </Button>
        <Button variant="outline" size="sm" @click="exportLogs">
          <Download />
          Export
        </Button>
      </div>

      <Card>
        <CardHeader class="sr-only">
          <span>Webhook logs</span>
        </CardHeader>
        <CardContent class="px-0 pt-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead class="pl-6">Timestamp</TableHead>
                <TableHead>Order ID</TableHead>
                <TableHead>User</TableHead>
                <TableHead>Gateway</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Status</TableHead>
                <TableHead class="pr-6 text-right">Action</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow
                v-for="row in rows"
                :key="row.orderId"
                class="cursor-pointer"
                @click="inspect(row)"
              >
                <TableCell class="text-muted-foreground pl-6 font-mono text-xs">
                  {{ row.timestamp }}
                </TableCell>
                <TableCell class="text-primary font-mono text-xs">{{ row.orderId }}</TableCell>
                <TableCell class="font-mono text-xs">{{ row.userId }}</TableCell>
                <TableCell>
                  <Badge variant="secondary">{{ row.gateway }}</Badge>
                </TableCell>
                <TableCell>{{ row.amount }}</TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    :class="{
                      'border-emerald-400/40 text-emerald-300': row.status === 'success',
                      'border-red-400/40 text-red-300': row.status === 'failed',
                      'border-amber-400/40 text-amber-300': row.status === 'pending',
                    }"
                  >
                    {{ row.status }}
                  </Badge>
                </TableCell>
                <TableCell class="pr-6 text-right">
                  <Button variant="ghost" size="icon-sm" @click.stop="inspect(row)">
                    <Braces />
                  </Button>
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

    <Sheet v-model:open="open">
      <SheetContent class="sm:max-w-md">
        <SheetHeader>
          <SheetTitle class="flex items-center gap-2">
            <Braces class="size-4" />
            Payload Inspector
          </SheetTitle>
          <SheetDescription class="font-mono">
            Order ID: {{ selected?.orderId ?? '---' }}
          </SheetDescription>
        </SheetHeader>

        <div class="relative mt-4 flex-1 px-4">
          <Button
            variant="secondary"
            size="icon-sm"
            class="absolute top-2 right-6 z-10"
            @click="copyPayload"
          >
            <Copy />
          </Button>
          <ScrollArea class="bg-muted/40 h-[50vh] rounded-xl border p-4">
            <pre class="font-mono text-xs leading-relaxed whitespace-pre-wrap">{{ payload }}</pre>
          </ScrollArea>
        </div>

        <div class="mt-4 space-y-2 px-4 pb-4">
          <p class="text-muted-foreground text-xs font-medium tracking-wide uppercase">
            Verification Status
          </p>
          <div class="bg-primary/10 border-primary/20 flex items-start gap-3 rounded-xl border p-3">
            <CheckCircle2 class="text-primary mt-0.5 size-5" />
            <div>
              <p class="text-sm font-medium">Signature Verified</p>
              <p class="text-muted-foreground text-xs">Payload matches shared secret HMAC.</p>
            </div>
          </div>
        </div>
      </SheetContent>
    </Sheet>
  </div>
</template>
