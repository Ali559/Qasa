<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Ban, MonitorSmartphone, RefreshCw, Search } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter, CardHeader } from '@/components/ui/card'
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
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { users as mockUsers, type UserRow } from '@/data/mock'
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
const tier = ref('all')
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
  <div class="space-y-4">
    <Card>
        <CardHeader class="gap-4">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div class="relative w-full max-w-sm">
              <Search class="text-muted-foreground absolute top-1/2 left-2.5 size-4 -translate-y-1/2" />
              <Input
                v-model="query"
                class="pl-8"
                placeholder="Search by ID or Hash..."
                @update:model-value="page = 1"
              />
            </div>
            <Select v-model="tier" @update:model-value="page = 1">
              <SelectTrigger class="w-[160px]">
                <SelectValue placeholder="Tier" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Tier: All</SelectItem>
                <SelectItem value="premium">Premium</SelectItem>
                <SelectItem value="free">Free</SelectItem>
                <SelectItem value="expired">Expired</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardHeader>

        <CardContent class="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead class="pl-6">User ID</TableHead>
                <TableHead>Tier</TableHead>
                <TableHead>Devices</TableHead>
                <TableHead>Expiry</TableHead>
                <TableHead class="pr-6 text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              <TableRow v-for="user in pageRows" :key="user.id" class="group">
                <TableCell class="pl-6">
                  <div class="flex items-center gap-2">
                    <span
                      class="size-2 rounded-full"
                      :class="{
                        'bg-emerald-400': user.status === 'active',
                        'bg-zinc-500': user.status === 'idle',
                        'bg-red-400': user.status === 'expired',
                      }"
                    />
                    <span class="text-primary font-mono text-xs">{{ user.id }}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    :class="{
                      'border-amber-400/40 text-amber-300': user.tier === 'premium',
                      'border-red-400/40 text-red-300': user.tier === 'expired',
                    }"
                  >
                    {{ user.tier }}
                  </Badge>
                </TableCell>
                <TableCell>{{ user.devices }}</TableCell>
                <TableCell :class="{ 'text-destructive': user.expiryAlert }">
                  {{ user.expiry }}
                </TableCell>
                <TableCell class="pr-6">
                  <div class="flex justify-end gap-1 opacity-80 transition-opacity group-hover:opacity-100">
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <Button variant="ghost" size="icon-sm">
                          <MonitorSmartphone />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>View devices</TooltipContent>
                    </Tooltip>
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <Button variant="ghost" size="icon-sm" @click="refreshUser(user)">
                          <RefreshCw />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Refresh session</TooltipContent>
                    </Tooltip>
                    <Tooltip>
                      <TooltipTrigger as-child>
                        <Button variant="ghost" size="icon-sm" class="text-destructive" @click="blockTarget = user">
                          <Ban />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Block user</TooltipContent>
                    </Tooltip>
                  </div>
                </TableCell>
              </TableRow>
              <TableRow v-if="pageRows.length === 0">
                <TableCell colspan="5" class="text-muted-foreground h-24 text-center">
                  No users match your filters.
                </TableCell>
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>

        <CardFooter class="justify-between border-t">
          <p class="text-muted-foreground text-sm">
            Showing {{ pageRows.length }} of {{ filtered.length }} entries
          </p>
          <div class="flex gap-2">
            <Button variant="outline" size="sm" :disabled="page <= 1" @click="prevPage">Prev</Button>
            <Button variant="outline" size="sm" :disabled="page >= totalPages" @click="nextPage">Next</Button>
          </div>
        </CardFooter>
      </Card>

    <Dialog :open="!!blockTarget" @update:open="(open) => !open && (blockTarget = null)">
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Block user?</DialogTitle>
          <DialogDescription>
            This will revoke access for <span class="font-mono">{{ blockTarget?.id }}</span>.
          </DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button variant="outline" @click="blockTarget = null">Cancel</Button>
          <Button variant="destructive" @click="confirmBlock">Block</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
