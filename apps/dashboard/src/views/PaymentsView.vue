<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { AlertTriangle, Braces, CheckCircle2, Copy, Download, Search } from '@lucide/vue'
import { useDebounceFn } from '@vueuse/core'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
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
import GatewayMix from '@/components/overview/GatewayMix.vue'
import {
  demoPaymentDetail,
  gateways as mockGateways,
  payments as mockPayments,
  type PaymentRow,
  type PayStatus,
} from '@/data/mock'
import { fetchPaymentDetail, fetchPayments, fetchTimeseries, type PaymentDetail } from '@/api/admin'
import { errorMessage, isLiveApiEnabled } from '@/api/client'
import { downloadCsv, gatewayColor, gatewayShares } from '@/lib/reports'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const PAGE = 100

const payments = ref<PaymentRow[]>([...mockPayments])
const nextBefore = ref<string | null>(null)
const loading = ref(false)
const query = ref('')
const statusFilter = ref<'all' | PayStatus>('all')
const gateways = ref([...mockGateways])
const mixDescription = ref<string | undefined>(undefined)

// Live mode filters by status and searches (order-id prefix or account id) server-side,
// paging with `next_before`. Demo mode filters the mock rows locally.
async function load(append = false) {
  if (!live) return
  loading.value = true
  try {
    const data = await fetchPayments({
      q: query.value.trim() || undefined,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
      limit: PAGE,
      before: append ? nextBefore.value : undefined,
    })
    payments.value = append ? [...payments.value, ...data.payments] : data.payments
    nextBefore.value = data.next_before
  } catch (err) {
    toast.error('Failed to load payments', { description: errorMessage(err) })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!live) return
  load()
  try {
    const ts = await fetchTimeseries(30)
    gateways.value = gatewayShares(ts.gateway_mix)
    mixDescription.value = `Share of successful payments, last ${ts.days} days`
  } catch (err) {
    toast.error('Failed to load gateway mix', { description: errorMessage(err) })
  }
})

const debouncedLoad = useDebounceFn(() => load(), 300)
watch(query, () => live && debouncedLoad())
watch(statusFilter, () => live && load())

const selected = ref<PaymentRow | null>(null)
const detail = ref<PaymentDetail | null>(null)
const detailLoading = ref(false)
const open = ref(false)

const rows = computed(() => {
  if (live) return payments.value
  const q = query.value.trim().toLowerCase()
  return payments.value.filter(
    (p) =>
      (statusFilter.value === 'all' || p.status === statusFilter.value) &&
      (!q || p.orderId?.toLowerCase().startsWith(q) || p.userId.toLowerCase().includes(q)),
  )
})

const anyInvalid = computed(() => detail.value?.webhooks.some((w) => !w.signature_valid) ?? false)

async function inspect(row: PaymentRow) {
  selected.value = row
  detail.value = null
  open.value = true
  if (!live || !row.paymentId) {
    detail.value = demoPaymentDetail(row)
    return
  }
  detailLoading.value = true
  try {
    detail.value = await fetchPaymentDetail(row.paymentId)
  } catch (err) {
    toast.error('Failed to load payment', { description: errorMessage(err) })
  } finally {
    detailLoading.value = false
  }
}

function copyPayload(value: unknown) {
  navigator.clipboard.writeText(JSON.stringify(value, null, 2))
  toast.success('Payload copied')
}

const statusOptions = computed(() => {
  // Counts are only meaningful when every row is loaded locally (demo mode).
  const count = (st: PayStatus) => (live ? undefined : payments.value.filter((p) => p.status === st).length)
  return [
    { value: 'all' as const, label: 'All', count: live ? undefined : payments.value.length },
    { value: 'success' as const, label: 'Paid', count: count('success') },
    { value: 'pending' as const, label: 'Pending', count: count('pending') },
    { value: 'failed' as const, label: 'Failed', count: count('failed') },
    { value: 'expired' as const, label: 'Expired', count: count('expired') },
  ]
})

const statusTone: Record<string, PillTone> = { success: 'ok', pending: 'warn', failed: 'bad', expired: 'neutral' }
const statusLabel: Record<string, string> = { success: 'paid', pending: 'pending', failed: 'failed', expired: 'expired' }

function fmtTime(value: string) {
  const d = new Date(value)
  return Number.isNaN(d.getTime()) ? value : d.toLocaleString()
}

function exportLogs() {
  if (rows.value.length === 0) {
    toast.error('Nothing to export')
    return
  }
  downloadCsv(
    'qasa-payments.csv',
    rows.value.map((r) => ({
      payment_id: r.paymentId ?? '',
      timestamp: r.timestamp,
      order_id: r.orderId,
      account_id: r.userId,
      gateway: r.gateway,
      plan_id: r.planId ?? '',
      amount_iqd: r.amount.replace(/,/g, ''),
      status: r.status,
    })),
  )
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <FilterChips v-model="statusFilter" :options="statusOptions" label="Filter by payment status" />
      <div class="flex w-full flex-wrap items-center gap-3 lg:w-auto">
        <div class="relative w-full lg:w-72">
          <Search class="text-muted-foreground absolute top-1/2 left-3 size-4 -translate-y-1/2" />
          <Input
            v-model="query"
            class="bg-card h-10 pl-9"
            placeholder="Order ID or account ID"
            aria-label="Search by order ID or account ID"
          />
        </div>
        <Button variant="outline" @click="exportLogs">
          <Download />
          Export CSV
        </Button>
      </div>
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
              :key="row.paymentId ?? row.orderId"
              class="cursor-pointer"
              @click="inspect(row)"
            >
              <TableCell class="text-muted-foreground py-3.5 pl-6 text-xs tabular-nums">{{ row.timestamp }}</TableCell>
              <TableCell class="font-mono text-[0.8rem] font-medium">{{ row.orderId ?? '—' }}</TableCell>
              <TableCell class="text-muted-foreground font-mono text-xs">
                <RouterLink
                  v-if="live"
                  :to="{ name: 'account', params: { accountId: row.userId } }"
                  class="hover:text-foreground hover:underline"
                  @click.stop
                >{{ row.userId.slice(0, 8) }}…</RouterLink>
                <template v-else>{{ row.userId }}</template>
              </TableCell>
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
              <TableCell><StatusPill :tone="statusTone[row.status] ?? 'neutral'">{{ statusLabel[row.status] ?? row.status }}</StatusPill></TableCell>
              <TableCell class="pr-6 text-right">
                <Button variant="ghost" size="icon-sm" aria-label="Inspect webhook payload" @click.stop="inspect(row)">
                  <Braces />
                </Button>
              </TableCell>
            </TableRow>
            <TableRow v-if="rows.length === 0" class="hover:bg-transparent">
              <TableCell colspan="7" class="text-muted-foreground h-32 text-center">
                {{ loading ? 'Loading payments…' : 'No payments match. Pick another filter or clear the search.' }}
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
      <div v-if="nextBefore" class="flex justify-center border-t px-6 py-4">
        <Button variant="outline" size="sm" :disabled="loading" @click="load(true)">Load more</Button>
      </div>
    </Card>

    <GatewayMix :gateways="gateways" :description="mixDescription" />

    <Sheet v-model:open="open">
      <SheetContent class="gap-5 sm:max-w-md">
        <SheetHeader class="pb-0">
          <SheetTitle class="font-heading text-xl">Webhook events</SheetTitle>
          <SheetDescription class="font-mono">{{ selected?.orderId ?? 'No payment selected' }}</SheetDescription>
        </SheetHeader>

        <dl v-if="detail" class="mx-4 grid grid-cols-2 gap-3 text-sm">
          <div class="bg-muted/60 rounded-2xl px-4 py-3">
            <dt class="text-muted-foreground text-xs">Amount</dt>
            <dd class="font-heading font-bold">{{ detail.payment.amount_iqd.toLocaleString('en-US') }} IQD</dd>
          </div>
          <div class="bg-muted/60 rounded-2xl px-4 py-3">
            <dt class="text-muted-foreground text-xs">Status</dt>
            <dd class="font-semibold capitalize">{{ statusLabel[detail.payment.status] ?? detail.payment.status }}</dd>
          </div>
          <div class="bg-muted/60 rounded-2xl px-4 py-3">
            <dt class="text-muted-foreground text-xs">Plan</dt>
            <dd class="font-semibold">
              {{ detail.payment.plan_id }}<template v-if="detail.payment.kind === 'renewal'">, renewal</template>
            </dd>
          </div>
          <div class="bg-muted/60 rounded-2xl px-4 py-3">
            <dt class="text-muted-foreground text-xs">Completed</dt>
            <dd class="font-semibold">{{ detail.payment.completed_at ? fmtTime(detail.payment.completed_at) : '—' }}</dd>
          </div>
        </dl>

        <p v-if="detailLoading" class="text-muted-foreground mx-4 text-sm">Loading webhook events…</p>

        <template v-else-if="detail">
          <div v-if="detail.webhooks.length === 0" class="bg-muted/60 mx-4 rounded-2xl p-4 text-sm">
            <p class="font-semibold">No webhooks received</p>
            <p class="text-muted-foreground text-xs">The gateway hasn't called back for this order yet.</p>
          </div>
          <div
            v-else
            class="mx-4 flex items-start gap-3 rounded-2xl p-4"
            :class="anyInvalid ? 'bg-bad/12' : 'bg-ok/12'"
          >
            <AlertTriangle v-if="anyInvalid" class="text-bad mt-0.5 size-5 shrink-0" />
            <CheckCircle2 v-else class="text-ok mt-0.5 size-5 shrink-0" />
            <div>
              <p class="text-sm font-semibold">
                {{ anyInvalid ? 'A webhook failed signature checks' : 'Every signature verified' }}
              </p>
              <p class="text-muted-foreground text-xs">
                {{ detail.webhooks.length }} event{{ detail.webhooks.length === 1 ? '' : 's' }} received for this order.
              </p>
            </div>
          </div>

          <ScrollArea v-if="detail.webhooks.length" class="mx-4 h-[50vh]">
            <div class="space-y-4">
              <InkPanel v-for="w in detail.webhooks" :key="w.id" class="relative">
                <div class="flex flex-wrap items-center gap-2 px-5 pt-4 text-xs">
                  <span class="tabular-nums opacity-70">{{ fmtTime(w.received_at) }}</span>
                  <span
                    class="rounded-md px-2 py-0.5 font-semibold text-[#1c1b33]"
                    :style="{ background: w.signature_valid ? 'var(--ink-ok)' : 'var(--ink-bad)' }"
                  >{{ w.signature_valid ? 'signature ok' : 'bad signature' }}</span>
                  <span
                    class="rounded-md px-2 py-0.5 font-semibold text-[#1c1b33]"
                    :style="{ background: w.error_message ? 'var(--ink-bad)' : w.processed ? 'var(--ink-info)' : 'var(--ink-warn)' }"
                  >{{ w.error_message ? 'rejected' : w.processed ? 'processed' : 'not processed' }}</span>
                  <Button variant="secondary" size="sm" class="ml-auto" @click="copyPayload(w.payload)">
                    <Copy />
                    Copy
                  </Button>
                </div>
                <p v-if="w.error_message" class="px-5 pt-2 text-xs" :style="{ color: 'var(--ink-bad)' }">{{ w.error_message }}</p>
                <pre class="p-5 font-mono text-xs leading-relaxed whitespace-pre-wrap">{{ JSON.stringify(w.payload, null, 2) }}</pre>
              </InkPanel>
            </div>
          </ScrollArea>
        </template>
        <div class="pb-2" />
      </SheetContent>
    </Sheet>
  </div>
</template>
