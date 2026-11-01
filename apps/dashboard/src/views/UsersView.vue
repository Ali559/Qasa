<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Ban, MonitorSmartphone, RefreshCw, Search } from '@lucide/vue'
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
import { fetchUsers } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const users = ref<UserRow[]>([...mockUsers])

onMounted(async () => {
  if (!isLiveApiEnabled()) return
  try {
    users.value = await fetchUsers()
  } catch {
    toast.error('Failed to load users')
  }
})

const query = ref('')
const tier = ref<'all' | UserTier>('all')
const page = ref(1)
const pageSize = 5
const blockTarget = ref<UserRow | null>(null)

const filtered = computed(() =>
  users.value.filter((u) => {
    const q = query.value.trim().toLowerCase()
    const matchQuery = !q || u.id.toLowerCase().includes(q)
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

const tierTone: Record<UserTier, PillTone> = { premium: 'info', free: 'neutral', expired: 'bad' }
const statusTone: Record<UserRow['status'], PillTone> = { active: 'ok', idle: 'neutral', expired: 'bad' }

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

function refreshUser(user: UserRow) {
  toast.success('Session refreshed', { description: user.id })
}

function confirmBlock() {
  if (!blockTarget.value) return
  toast.error('User blocked', { description: blockTarget.value.id })
  blockTarget.value = null
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
          placeholder="Search by user ID"
          aria-label="Search by user ID"
          @update:model-value="page = 1"
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
            <TableRow v-for="user in pageRows" :key="user.id" class="group">
              <TableCell class="py-3.5 pl-6 font-mono text-[0.8rem] font-medium">{{ user.id }}</TableCell>
              <TableCell><StatusPill :tone="statusTone[user.status]">{{ user.status }}</StatusPill></TableCell>
              <TableCell><StatusPill :tone="tierTone[user.tier]" :dot="false">{{ user.tier }}</StatusPill></TableCell>
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
                      <Button variant="ghost" size="icon-sm" aria-label="View devices">
                        <MonitorSmartphone />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>View devices</TooltipContent>
                  </Tooltip>
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <Button variant="ghost" size="icon-sm" aria-label="Refresh session" @click="refreshUser(user)">
                        <RefreshCw />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Refresh session</TooltipContent>
                  </Tooltip>
                  <Tooltip>
                    <TooltipTrigger as-child>
                      <Button variant="ghost" size="icon-sm" class="text-bad hover:text-bad" aria-label="Block user" @click="blockTarget = user">
                        <Ban />
                      </Button>
                    </TooltipTrigger>
                    <TooltipContent>Block user</TooltipContent>
                  </Tooltip>
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
          {{ filtered.length }} users, page {{ page }} of {{ totalPages }}
        </p>
        <div class="flex gap-2">
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
            <span class="font-mono">{{ blockTarget?.id }}</span> loses VPN access on every device.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="blockTarget = null">Cancel</Button>
          <Button variant="destructive" @click="confirmBlock">Block user</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
