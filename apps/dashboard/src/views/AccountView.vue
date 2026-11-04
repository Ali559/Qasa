<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import {
  ArrowLeft,
  Ban,
  Copy,
  Gift,
  Gauge,
  LockOpen,
  // MailWarning, (breach monitors tile, parked)
  RefreshCw,
  ShieldCheck,
  Smartphone,
  Undo2,
  Unlink,
} from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import FilterChips from '@/components/kit/FilterChips.vue'
import StatTile from '@/components/kit/StatTile.vue'
import StatusPill, { type PillTone } from '@/components/kit/StatusPill.vue'
import {
  blockAccount,
  fetchAccountDetail,
  grantPremium,
  revokeGrant,
  unblockAccount,
  unlinkAccountDevice,
  type AccountDetail,
  type AccountDevice,
  type AccountSubscription,
} from '@/api/admin'
import { canWrite, errorMessage, isLiveApiEnabled } from '@/api/client'
import { demoAccountDetail } from '@/data/mock'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const route = useRoute()
const accountId = computed(() => String(route.params.accountId ?? ''))

const detail = ref<AccountDetail | null>(null)
const loading = ref(false)
const notFound = ref(false)

async function load() {
  notFound.value = false
  if (!live) {
    detail.value = demoAccountDetail(accountId.value)
    return
  }
  loading.value = true
  try {
    detail.value = await fetchAccountDetail(accountId.value)
  } catch (err) {
    if ((err as { status?: number }).status === 404) notFound.value = true
    else toast.error('Failed to load account', { description: errorMessage(err) })
  } finally {
    loading.value = false
  }
}

watch(accountId, load, { immediate: true })

const account = computed(() => detail.value?.account)
const blocked = computed(() => Boolean(account.value?.blocked_at))
const activeDevices = computed(() => detail.value?.devices.filter((d) => !d.revoked_at) ?? [])

const statusLabel = computed(() => {
  if (!account.value) return ''
  if (blocked.value) return 'blocked'
  if (account.value.lapsed) return 'expired'
  return detail.value?.entitlements.tier ?? 'free'
})
const statusTone = computed<PillTone>(() =>
  blocked.value || account.value?.lapsed ? 'bad' : detail.value?.entitlements.tier === 'premium' ? 'info' : 'neutral',
)

const tiles = computed(() => {
  const d = detail.value
  if (!d) return []
  const gb = d.vpn_usage.bytes_used / 1_000_000_000
  return [
    { label: 'Plan', value: d.entitlements.tier === 'premium' ? 'Premium' : 'Free', meta: d.entitlements.vpn ? 'VPN' : 'No VPN', icon: ShieldCheck, accent: 'var(--chart-2)' },
    { label: 'Devices', value: `${activeDevices.value.length} / ${d.entitlements.device_limit}`, meta: `${d.devices.length - activeDevices.value.length} unlinked`, icon: Smartphone, accent: 'var(--chart-4)' },
    { label: 'VPN data this period', value: gb >= 10 ? gb.toFixed(0) : gb.toFixed(1), unit: 'GB', meta: d.vpn_usage.throttled ? 'Throttled' : `since ${fmtDate(d.vpn_usage.period_start)}`, icon: Gauge, accent: 'var(--chart-3)' },
    // Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
    // { label: 'Breach monitors', value: String(d.breach_monitors), meta: `limit ${d.entitlements.breach_monitor_limit}`, icon: MailWarning, accent: 'var(--chart-5)' },
  ]
})

function fmtDate(value: string | null | undefined) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
}

function fmtDateTime(value: string | null | undefined) {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return value
  return d.toLocaleString(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
}

const fmtIqd = (n: number) => n.toLocaleString('en-US')
const planLabel = (id: string) => (id === 'premium_yearly' ? 'Yearly' : id === 'premium_monthly' ? 'Monthly' : id)

const payTone: Record<string, PillTone> = { success: 'ok', pending: 'warn', failed: 'bad', expired: 'neutral' }
const subTone: Record<string, PillTone> = { active: 'ok', cancelled: 'warn', expired: 'neutral', revoked: 'bad' }

function isGrant(s: AccountSubscription) {
  return Boolean(s.granted_by || s.grant_days)
}

function copyId() {
  if (!account.value) return
  navigator.clipboard.writeText(account.value.id)
  toast.success('Account ID copied')
}

// --- Block / unblock -------------------------------------------------------

const blockOpen = ref(false)
const blockReason = ref('')
const busy = ref(false)

async function confirmBlock() {
  const reason = blockReason.value.trim()
  if (!reason || !detail.value) return
  busy.value = true
  try {
    if (live) {
      await blockAccount(accountId.value, reason)
      await load()
    } else {
      detail.value.account.blocked_at = new Date().toISOString()
      detail.value.account.blocked_reason = reason
    }
    toast.success('User blocked')
    blockOpen.value = false
    blockReason.value = ''
  } catch (err) {
    toast.error('User not blocked', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

async function unblock() {
  if (!detail.value) return
  busy.value = true
  try {
    if (live) {
      await unblockAccount(accountId.value)
      await load()
    } else {
      detail.value.account.blocked_at = null
      detail.value.account.blocked_reason = null
    }
    toast.success('User unblocked')
  } catch (err) {
    toast.error('User not unblocked', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

// --- Devices ---------------------------------------------------------------

const unlinkTarget = ref<AccountDevice | null>(null)

async function confirmUnlink() {
  const device = unlinkTarget.value
  if (!device || !detail.value) return
  busy.value = true
  try {
    if (live) {
      await unlinkAccountDevice(accountId.value, device.id)
      await load()
    } else {
      device.revoked_at = new Date().toISOString()
    }
    toast.success('Device unlinked', { description: device.device_name })
    unlinkTarget.value = null
  } catch (err) {
    toast.error('Device not unlinked', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

// --- Grants ----------------------------------------------------------------

const grantOpen = ref(false)
const grantDays = ref<string | number>(7)
const grantReason = ref('')
const grantPlan = ref<'premium_monthly' | 'premium_yearly'>('premium_monthly')
const planOptions = [
  { value: 'premium_monthly' as const, label: 'Monthly quotas' },
  { value: 'premium_yearly' as const, label: 'Yearly quotas' },
]
const grantDaysNum = computed(() => Number(grantDays.value))
const grantValid = computed(
  () => Number.isInteger(grantDaysNum.value) && grantDaysNum.value >= 1 && grantDaysNum.value <= 3650 && grantReason.value.trim().length > 0,
)

async function confirmGrant() {
  if (!grantValid.value || !detail.value) return
  busy.value = true
  try {
    if (live) {
      const res = await grantPremium(accountId.value, {
        days: grantDaysNum.value,
        reason: grantReason.value.trim(),
        plan_id: grantPlan.value,
      })
      toast.success(`Granted ${res.granted_days} days of Premium`, {
        description: `Now expires ${fmtDate(res.subscription.expires_at)}`,
      })
      await load()
    } else {
      toast.success(`Granted ${grantDaysNum.value} days of Premium (demo mode)`)
    }
    grantOpen.value = false
    grantReason.value = ''
    grantDays.value = 7
  } catch (err) {
    toast.error('Grant not saved', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

const revokeTarget = ref<AccountSubscription | null>(null)

async function confirmRevoke() {
  const sub = revokeTarget.value
  if (!sub || !detail.value) return
  busy.value = true
  try {
    if (live) {
      await revokeGrant(sub.id)
      await load()
    } else {
      sub.grant_revoked_at = new Date().toISOString()
    }
    toast.success('Grant revoked')
    revokeTarget.value = null
  } catch (err) {
    toast.error('Grant not revoked', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

function auditSummary(payload: Record<string, unknown> | null) {
  if (!payload) return ''
  return Object.entries(payload)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(', ')
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <Button variant="outline" size="sm" as-child>
        <RouterLink :to="{ name: 'users' }">
          <ArrowLeft />
          All users
        </RouterLink>
      </Button>
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw />
        Refresh
      </Button>
    </div>

    <Card v-if="notFound" class="px-6 py-10 text-center">
      <p class="font-heading text-xl font-bold">Account not found</p>
      <p class="text-muted-foreground mt-1 text-sm">It may have been deleted. Go back and search again.</p>
    </Card>

    <template v-else-if="detail && account">
      <Card class="gap-4 px-6 py-6">
        <div class="flex flex-wrap items-start justify-between gap-4">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-3">
              <h2 class="font-heading text-2xl font-bold">{{ account.public_id }}</h2>
              <StatusPill :tone="statusTone">{{ statusLabel }}</StatusPill>
            </div>
            <button
              type="button"
              class="text-muted-foreground hover:text-foreground mt-1 inline-flex items-center gap-1.5 font-mono text-xs"
              @click="copyId"
            >
              {{ account.id }}
              <Copy class="size-3" />
            </button>
            <p class="text-muted-foreground mt-1 text-sm">Joined {{ fmtDate(account.created_at) }}</p>
          </div>
          <div v-if="canWrite()" class="flex flex-wrap gap-2">
            <Button variant="outline" :disabled="busy || blocked" @click="grantOpen = true">
              <Gift />
              Grant Premium
            </Button>
            <Button v-if="blocked" variant="outline" :disabled="busy" @click="unblock">
              <LockOpen />
              Unblock
            </Button>
            <Button v-else variant="destructive" :disabled="busy" @click="blockOpen = true">
              <Ban />
              Block user
            </Button>
          </div>
        </div>
        <div v-if="blocked" class="bg-bad/12 rounded-2xl p-4 text-sm">
          <p class="font-semibold">Blocked {{ fmtDateTime(account.blocked_at) }}</p>
          <p class="text-muted-foreground">{{ account.blocked_reason || 'No reason recorded' }}</p>
        </div>
      </Card>

      <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-3">
        <StatTile v-for="t in tiles" :key="t.label" v-bind="t" />
      </div>

      <Card class="gap-0 py-0">
        <CardHeader class="px-6 pt-6 pb-4">
          <CardTitle class="text-xl font-bold">Devices</CardTitle>
          <CardDescription>The oldest linked device is the primary.</CardDescription>
        </CardHeader>
        <CardContent class="px-0">
          <Table>
            <TableHeader>
              <TableRow class="hover:bg-transparent">
                <TableHead class="h-12 pl-6">Device</TableHead>
                <TableHead>Platform</TableHead>
                <TableHead>Linked</TableHead>
                <TableHead>Last seen</TableHead>
                <TableHead>Status</TableHead>
                <TableHead class="pr-6 text-right"><span class="sr-only">Actions</span></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="d in detail.devices" :key="d.id" :class="d.revoked_at ? 'opacity-60' : ''">
                <TableCell class="py-3.5 pl-6">
                  <p class="font-semibold">{{ d.device_name }}</p>
                  <p class="text-muted-foreground text-xs">{{ d.location || 'Unknown location' }}</p>
                </TableCell>
                <TableCell class="capitalize">{{ d.platform }}</TableCell>
                <TableCell class="text-muted-foreground text-sm">{{ fmtDate(d.created_at) }}</TableCell>
                <TableCell class="text-muted-foreground text-sm">{{ fmtDateTime(d.last_seen_at) }}</TableCell>
                <TableCell>
                  <StatusPill v-if="d.revoked_at" tone="neutral">unlinked {{ fmtDate(d.revoked_at) }}</StatusPill>
                  <StatusPill v-else-if="d.is_primary" tone="info">primary</StatusPill>
                  <StatusPill v-else tone="ok">linked</StatusPill>
                </TableCell>
                <TableCell class="pr-6 text-right">
                  <Button
                    v-if="!d.revoked_at && canWrite()"
                    variant="ghost"
                    size="sm"
                    class="text-bad hover:text-bad"
                    @click="unlinkTarget = d"
                  >
                    <Unlink />
                    Unlink
                  </Button>
                </TableCell>
              </TableRow>
              <TableRow v-if="detail.devices.length === 0" class="hover:bg-transparent">
                <TableCell colspan="6" class="text-muted-foreground h-24 text-center">No devices.</TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div class="grid gap-6 xl:grid-cols-2">
        <Card class="gap-0 py-0">
          <CardHeader class="px-6 pt-6 pb-4">
            <CardTitle class="text-xl font-bold">Subscriptions</CardTitle>
            <CardDescription>
              Last 20, grants included.
              <template v-if="detail.payment_method">
                Renews with the saved {{ detail.payment_method.gateway.toUpperCase() }} wallet (saved {{ fmtDate(detail.payment_method.created_at) }}).
              </template>
              <template v-else>No saved wallet, so nothing renews automatically.</template>
            </CardDescription>
          </CardHeader>
          <CardContent class="px-0">
            <Table>
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead class="h-12 pl-6">Plan</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Period</TableHead>
                  <TableHead class="pr-6 text-right"><span class="sr-only">Actions</span></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="s in detail.subscriptions" :key="s.id">
                  <TableCell class="py-3.5 pl-6">
                    <p class="font-semibold">{{ planLabel(s.plan_id) }}</p>
                    <p v-if="isGrant(s)" class="text-muted-foreground text-xs">
                      Grant, {{ s.grant_days }} days: {{ s.grant_reason }}
                    </p>
                    <p v-else class="text-muted-foreground text-xs">
                      {{ fmtIqd(s.amount_iqd) }} IQD<template v-if="s.auto_renew">, auto-renew</template><template v-if="s.renewal_attempts">, {{ s.renewal_attempts }} renewal {{ s.renewal_attempts === 1 ? 'attempt' : 'attempts' }}</template>
                    </p>
                  </TableCell>
                  <TableCell>
                    <StatusPill v-if="s.grant_revoked_at" tone="bad">grant revoked</StatusPill>
                    <StatusPill v-else :tone="subTone[s.status] ?? 'neutral'">{{ s.status }}</StatusPill>
                  </TableCell>
                  <TableCell class="text-muted-foreground text-xs">
                    {{ fmtDate(s.started_at) }} to {{ fmtDate(s.expires_at) }}
                  </TableCell>
                  <TableCell class="pr-6 text-right">
                    <Button
                      v-if="isGrant(s) && !s.grant_revoked_at && canWrite()"
                      variant="ghost"
                      size="sm"
                      @click="revokeTarget = s"
                    >
                      <Undo2 />
                      Revoke
                    </Button>
                  </TableCell>
                </TableRow>
                <TableRow v-if="detail.subscriptions.length === 0" class="hover:bg-transparent">
                  <TableCell colspan="4" class="text-muted-foreground h-24 text-center">No subscriptions yet.</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <Card class="gap-0 py-0">
          <CardHeader class="px-6 pt-6 pb-4">
            <CardTitle class="text-xl font-bold">Payments</CardTitle>
            <CardDescription>Last 20</CardDescription>
          </CardHeader>
          <CardContent class="px-0">
            <Table>
              <TableHeader>
                <TableRow class="hover:bg-transparent">
                  <TableHead class="h-12 pl-6">Order</TableHead>
                  <TableHead class="text-right">Amount</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead class="pr-6">Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                <TableRow v-for="p in detail.payments" :key="p.payment_id">
                  <TableCell class="py-3.5 pl-6">
                    <p class="font-mono text-[0.8rem] font-medium">{{ p.gateway_order_id ?? p.payment_id }}</p>
                    <p class="text-muted-foreground text-xs">
                      <span class="uppercase">{{ p.gateway }}</span>, {{ planLabel(p.plan_id) }}<template v-if="p.kind === 'renewal'">, automatic renewal</template>
                    </p>
                  </TableCell>
                  <TableCell class="text-right">
                    <span class="font-heading font-bold tabular-nums">{{ fmtIqd(p.amount_iqd) }}</span>
                    <span class="text-muted-foreground ml-1 text-xs">IQD</span>
                  </TableCell>
                  <TableCell><StatusPill :tone="payTone[p.status] ?? 'neutral'">{{ p.status === 'success' ? 'paid' : p.status }}</StatusPill></TableCell>
                  <TableCell class="text-muted-foreground pr-6 text-xs">{{ fmtDateTime(p.created_at) }}</TableCell>
                </TableRow>
                <TableRow v-if="detail.payments.length === 0" class="hover:bg-transparent">
                  <TableCell colspan="4" class="text-muted-foreground h-24 text-center">No payments yet.</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      <Card class="gap-4 py-6">
        <CardHeader class="px-6">
          <CardTitle class="text-xl font-bold">Staff actions</CardTitle>
          <CardDescription>Audit log entries about this account</CardDescription>
        </CardHeader>
        <CardContent class="px-6">
          <ul v-if="detail.audit.length" class="divide-y">
            <li v-for="a in detail.audit" :key="a.id" class="flex flex-col gap-1 py-3 sm:flex-row sm:items-center sm:gap-4">
              <span class="text-muted-foreground w-36 shrink-0 text-xs tabular-nums">{{ fmtDateTime(a.created_at) }}</span>
              <StatusPill tone="info" :dot="false">{{ a.action.replace(/_/g, ' ') }}</StatusPill>
              <span class="text-sm">{{ auditSummary(a.payload) }}</span>
              <span class="text-muted-foreground text-xs sm:ml-auto">{{ a.admin_email ?? a.admin_id }}</span>
            </li>
          </ul>
          <p v-else class="text-muted-foreground text-sm">No staff actions on this account.</p>
        </CardContent>
      </Card>
    </template>

    <Card v-else-if="loading" class="text-muted-foreground px-6 py-10 text-center text-sm">Loading account…</Card>

    <Dialog v-model:open="blockOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Block this user?</DialogTitle>
          <DialogDescription>
            Every device is signed out, VPN sessions end and the app can't register new devices until you unblock.
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="acct-block-reason">Reason</Label>
          <Textarea id="acct-block-reason" v-model="blockReason" placeholder="Chargeback abuse, ticket #1234" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="blockOpen = false">Cancel</Button>
          <Button variant="destructive" :disabled="busy || !blockReason.trim()" @click="confirmBlock">Block user</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog v-model:open="grantOpen">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Grant Premium</DialogTitle>
          <DialogDescription>Adds days on top of any time the account already has. No payment is taken.</DialogDescription>
        </DialogHeader>
        <div class="grid gap-4">
          <div class="grid gap-2">
            <Label for="grant-days">Days (1 to 3650)</Label>
            <Input id="grant-days" v-model="grantDays" type="number" min="1" max="3650" />
          </div>
          <div class="grid gap-2">
            <Label>Quotas</Label>
            <FilterChips v-model="grantPlan" :options="planOptions" label="Plan quotas" />
          </div>
          <div class="grid gap-2">
            <Label for="grant-reason">Reason</Label>
            <Textarea id="grant-reason" v-model="grantReason" placeholder="Outage compensation, ticket #1234" />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" @click="grantOpen = false">Cancel</Button>
          <Button :disabled="busy || !grantValid" @click="confirmGrant">Grant</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!unlinkTarget" @update:open="(o) => !o && (unlinkTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Unlink this device?</DialogTitle>
          <DialogDescription>
            {{ unlinkTarget?.device_name }} is signed out, stops getting notifications and its VPN session ends.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="unlinkTarget = null">Cancel</Button>
          <Button variant="destructive" :disabled="busy" @click="confirmUnlink">Unlink device</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    <Dialog :open="!!revokeTarget" @update:open="(o) => !o && (revokeTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Revoke this grant?</DialogTitle>
          <DialogDescription>
            Takes {{ revokeTarget?.grant_days }} days back off the subscription. Paid time is untouched.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="revokeTarget = null">Cancel</Button>
          <Button variant="destructive" :disabled="busy" @click="confirmRevoke">Revoke grant</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
