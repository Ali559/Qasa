<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { Ban, LockOpen, Search, UserRoundSearch } from '@lucide/vue'
import { useDebounceFn } from '@vueuse/core'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
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
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import FilterChips from '@/components/kit/FilterChips.vue'
import StatusPill, { type PillTone } from '@/components/kit/StatusPill.vue'
import { users as mockUsers, type UserRow, type UserTier } from '@/data/mock'
import { blockAccount, fetchUsers, unblockAccount } from '@/api/admin'
import { canWrite, errorMessage, isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const live = isLiveApiEnabled()
const SERVER_PAGE = 100

const users = ref<UserRow[]>([...mockUsers])
const nextBefore = ref<string | null>(null)
const loading = ref(false)

// Live mode searches server-side (account-id prefix or masked id) and pages with the
// X-Next-Before cursor; demo mode filters the mock rows locally.
async function load(append = false) {
  if (!live) return
  loading.value = true
  try {
    const res = await fetchUsers({
      q: query.value.trim() || undefined,
      limit: SERVER_PAGE,
      before: append ? nextBefore.value : undefined,
    })
    users.value = append ? [...users.value, ...res.users] : res.users
    nextBefore.value = res.nextBefore
  } catch (err) {
    toast.error('Failed to load users', { description: errorMessage(err) })
  } finally {
    loading.value = false
  }
}

onMounted(() => load())

const query = ref('')
const tier = ref<'all' | UserTier>('all')
const page = ref(1)
const pageSize = 5
const blockTarget = ref<UserRow | null>(null)
const blockReason = ref('')
const busy = ref(false)

const debouncedSearch = useDebounceFn(() => load(), 300)
watch(query, () => {
  page.value = 1
  if (live) debouncedSearch()
})

const filtered = computed(() =>
  users.value.filter((u) => {
    const q = query.value.trim().toLowerCase()
    const matchQuery =
      live || !q || u.id.toLowerCase().includes(q) || u.accountId.toLowerCase().startsWith(q)
    const matchTier = tier.value === 'all' || u.tier === tier.value
    return matchQuery && matchTier
  }),
)

const tierOptions = computed(() => {
  const count = (t: UserTier) => users.value.filter((u) => u.tier === t).length
  return [
    { value: 'all' as const, label: 'All', count: users.value.length },
    { value: 'premium' as const, label: 'Premium', count: count('premium') },
    { value: 'free' as const, label: 'Free', count: count('free') },
    { value: 'expired' as const, label: 'Expired', count: count('expired') },
  ]
})

const tierTone: Record<string, PillTone> = { premium: 'info', free: 'neutral', expired: 'bad' }
const statusTone: Record<string, PillTone> = { active: 'ok', idle: 'neutral', expired: 'bad', blocked: 'bad' }

// "3 / 5" → one pip per allowed device, filled for those in use.
function devicePips(devices: string) {
  const [used = 0, max = 0] = devices.split('/').map((n) => Number(n.trim()) || 0)
  return { used, max }
}

watch(tier, () => (page.value = 1))

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

function openBlock(user: UserRow) {
  blockTarget.value = user
  blockReason.value = ''
}

function setStatus(user: UserRow, status: UserRow['status']) {
  users.value = users.value.map((u) => (u.accountId === user.accountId ? { ...u, status } : u))
}

async function confirmBlock() {
  const target = blockTarget.value
  if (!target) return
  const reason = blockReason.value.trim()
  if (!reason) {
    toast.error('Add a reason. It is kept in the audit log.')
    return
  }
  busy.value = true
  try {
    if (live) await blockAccount(target.accountId, reason)
    setStatus(target, 'blocked')
    toast.success('User blocked', { description: target.id })
    blockTarget.value = null
  } catch (err) {
    toast.error('User not blocked', { description: errorMessage(err) })
  } finally {
    busy.value = false
  }
}

async function unblock(user: UserRow) {
  try {
    if (live) {
      await unblockAccount(user.accountId)
      await load()
    } else {
      setStatus(user, 'active')
    }
    toast.success('User unblocked', { description: user.id })
  } catch (err) {
    toast.error('User not unblocked', { description: errorMessage(err) })
  }
}

function nextPage() {
  if (page.value < totalPages.value) page.value += 1
}

function prevPage() {
  if (page.value > 1) page.value -= 1
}
</script>

<template>
  <div class="space-y-5">
    <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
      <FilterChips v-model="tier" :options="tierOptions" label="Filter by plan" />
      <div class="relative w-full lg:max-w-xs">
        <Search class="text-muted-foreground absolute top-1/2 left-3 size-4 -translate-y-1/2" />
        <Input
          v-model="query"
          class="bg-card h-10 pl-9"
          placeholder="Search by user or account ID"
          aria-label="Search by user or account ID"
        />
      </div>
    </div>

    <Card class="gap-0 py-0">
      <CardContent class="px-0">
        <Table>
          <TableHeader>
            <TableRow class="hover:bg-transparent">
              <TableHead class="h-12 pl-6">User</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Plan</TableHead>
              <TableHead>Devices</TableHead>
              <TableHead>Expires</TableHead>
              <TableHead class="pr-6 text-right"><span class="sr-only">Actions</span></TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="user in pageRows" :key="user.accountId" class="group">
              <TableCell class="py-3.5 pl-6 font-mono text-[0.8rem] font-medium">
                <RouterLink :to="{ name: 'account', params: { accountId: user.accountId } }" class="hover:underline">{{ user.id }}</RouterLink>
              </TableCell>
              <TableCell><StatusPill :tone="statusTone[user.status] ?? 'neutral'">{{ user.status }}</StatusPill></TableCell>
              <TableCell><StatusPill :tone="tierTone[user.tier] ?? 'neutral'" :dot="false">{{ user.tier }}</StatusPill></TableCell>
              <TableCell>
                <span class="flex items-center gap-2" :aria-label="`${user.devices} devices`">
                  <span class="flex gap-1" aria-hidden="true">
                    <i
                      v-for="n in devicePips(user.devices).max"
                      :key="n"
                      class="size-2.5 rounded-[3px]"
                      :class="n <= devicePips(user.devices).used ? 'bg-chart-4' : 'bg-muted shadow-[inset_0_1px_0_var(--edge)]'"
                    />
                  </span>
                  <span class="text-muted-foreground text-xs tabular-nums">{{ user.devices }}</span>
                </span>
              </TableCell>
              <TableCell :class="user.expiryAlert ? 'text-bad font-semibold' : ''">{{ user.expiry }}</TableCell>
              <TableCell class="pr-6">
                <div class="flex justify-end gap-1">
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <Button variant="ghost" size="icon-sm" as-child aria-label="Open account">
                        <RouterLink :to="{ name: 'account', params: { accountId: user.accountId } }">
                          <UserRoundSearch />
                        </RouterLink>
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Devices, payments and grants</TooltipContent>
                  </Tooltip>
                  <template v-if="canWrite()">
                    <Tooltip v-if="user.status === 'blocked'">
                      <TooltipTrigger as-child>
                        <Button variant="ghost" size="icon-sm" aria-label="Unblock user" @click="unblock(user)">
                          <LockOpen />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Unblock user</TooltipContent>
                    </Tooltip>
                    <Tooltip v-else>
                      <TooltipTrigger as-child>
                        <Button variant="ghost" size="icon-sm" class="text-bad hover:text-bad" aria-label="Block user" @click="openBlock(user)">
                          <Ban />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Block user</TooltipContent>
                    </Tooltip>
                  </template>
                </div>
              </TableCell>
            </TableRow>
            <TableRow v-if="pageRows.length === 0" class="hover:bg-transparent">
              <TableCell colspan="6" class="text-muted-foreground h-32 text-center">
                No users match these filters. Clear the search or pick another plan.
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>

      <CardFooter class="justify-between border-t px-6 py-4">
        <p class="text-muted-foreground text-sm">
          {{ filtered.length }} users<template v-if="nextBefore"> loaded</template>, page {{ page }} of {{ totalPages }}
        </p>
        <div class="flex gap-2">
          <Button v-if="nextBefore" variant="outline" size="sm" :disabled="loading" @click="load(true)">Load more</Button>
          <Button variant="outline" size="sm" :disabled="page <= 1" @click="prevPage">Previous</Button>
          <Button variant="outline" size="sm" :disabled="page >= totalPages" @click="nextPage">Next</Button>
        </div>
      </CardFooter>
    </Card>

    <Dialog :open="!!blockTarget" @update:open="(open) => !open && (blockTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle class="font-heading text-xl">Block this user?</DialogTitle>
          <DialogDescription>
            <span class="font-mono">{{ blockTarget?.id }}</span> is signed out on every device and loses VPN
            access until unblocked.
          </DialogDescription>
        </DialogHeader>
        <div class="grid gap-2">
          <Label for="block-reason">Reason</Label>
          <Textarea id="block-reason" v-model="blockReason" placeholder="Chargeback abuse, ticket #1234" />
        </div>
        <DialogFooter>
          <Button variant="outline" @click="blockTarget = null">Cancel</Button>
          <Button variant="destructive" :disabled="busy || !blockReason.trim()" @click="confirmBlock">Block user</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
