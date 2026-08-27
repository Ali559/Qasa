<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Plus, RefreshCw, Save } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { fetchPlans, upsertPlan, type PlanRow } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const plans = ref<PlanRow[]>([])
const loading = ref(false)
const editing = ref<PlanRow | null>(null)
const notify = ref(true)

async function load() {
  if (!isLiveApiEnabled()) {
    plans.value = [
      {
        id: 'premium_monthly',
        title: 'Premium Monthly',
        interval: 'monthly',
        amount_iqd: 10000,
        days: 30,
        vpn_quota_gb: 100,
        active: true,
        sort_order: 1,
        features_json: ['5 devices', 'VPN'],
      },
      {
        id: 'premium_yearly',
        title: 'Premium Yearly',
        interval: 'yearly',
        amount_iqd: 100000,
        days: 365,
        vpn_quota_gb: 100,
        active: true,
        sort_order: 2,
        features_json: ['5 devices', 'VPN', 'Save'],
      },
    ]
    return
  }
  loading.value = true
  try {
    const data = await fetchPlans()
    plans.value = data.plans
  } catch {
    toast.error('Failed to load plans')
  } finally {
    loading.value = false
  }
}

function startEdit(plan: PlanRow) {
  editing.value = { ...plan, features_json: [...(plan.features_json ?? [])] }
}

async function save() {
  if (!editing.value) return
  if (!isLiveApiEnabled()) {
    toast.message('Mock save', { description: editing.value.title })
    return
  }
  try {
    await upsertPlan({
      ...editing.value,
      features: editing.value.features_json ?? [],
      notify_subscribers: notify.value,
    })
    toast.success('Plan saved')
    editing.value = null
    await load()
  } catch {
    toast.error('Failed to save plan')
  }
}

onMounted(load)
</script>

<template>
  <div class="space-y-4">
    <div class="flex justify-end gap-2">
      <Button variant="outline" size="sm" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <Card>
      <CardHeader class="sr-only"><span>Plans</span></CardHeader>
      <CardContent class="px-0 pt-0">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead class="pl-6">Plan</TableHead>
              <TableHead>Interval</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>VPN quota</TableHead>
              <TableHead>Status</TableHead>
              <TableHead class="pr-6 text-right">Action</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            <TableRow v-for="p in plans" :key="p.id">
              <TableCell class="pl-6 font-medium">{{ p.title }}</TableCell>
              <TableCell><Badge variant="secondary">{{ p.interval }}</Badge></TableCell>
              <TableCell>{{ p.amount_iqd.toLocaleString() }} IQD</TableCell>
              <TableCell>{{ p.vpn_quota_gb }} GB</TableCell>
              <TableCell>
                <Badge :variant="p.active ? 'outline' : 'secondary'">
                  {{ p.active ? 'active' : 'inactive' }}
                </Badge>
              </TableCell>
              <TableCell class="pr-6 text-right">
                <Button variant="ghost" size="sm" @click="startEdit(p)">Edit</Button>
              </TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </CardContent>
    </Card>

    <Card v-if="editing">
      <CardHeader>
        <span class="font-medium">Edit {{ editing.id }}</span>
      </CardHeader>
      <CardContent class="space-y-3">
        <div class="grid gap-3 sm:grid-cols-2">
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Title</span>
            <Input v-model="editing.title" />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Amount (IQD)</span>
            <Input v-model.number="editing.amount_iqd" type="number" />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">Days</span>
            <Input v-model.number="editing.days" type="number" />
          </label>
          <label class="space-y-1 text-sm">
            <span class="text-muted-foreground">VPN quota (GB)</span>
            <Input v-model.number="editing.vpn_quota_gb" type="number" />
          </label>
        </div>
        <label class="flex items-center gap-2 text-sm">
          <input v-model="notify" type="checkbox" />
          Notify subscribers on save
        </label>
        <div class="flex gap-2">
          <Button size="sm" @click="save"><Save /> Save</Button>
          <Button size="sm" variant="outline" @click="editing = null">Cancel</Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
