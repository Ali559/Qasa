<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Pencil, RefreshCw, Save } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import StatusPill from '@/components/kit/StatusPill.vue'
import { Input } from '@/components/ui/input'
import { fetchPlans, upsertPlan, type PlanRow } from '@/api/admin'
import { canWrite, isLiveApiEnabled } from '@/api/client'
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
        amount_iqd: 5000,
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
        amount_iqd: 50000,
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
    toast.message('Saved locally (demo mode)', { description: editing.value.title })
    return
  }
  try {
    await upsertPlan({
      ...editing.value,
      features: editing.value.features_json ?? [],
      notify_subscribers: notify.value,
    })
    toast.success('Changes saved')
    editing.value = null
    await load()
  } catch {
    toast.error('Changes not saved. Check the values and try again.')
  }
}

onMounted(load)

const perLabel = (interval: string) => (interval === 'yearly' ? 'year' : interval === 'monthly' ? 'month' : interval)
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-end">
      <Button variant="outline" :disabled="loading" @click="load">
        <RefreshCw :class="{ 'animate-spin': loading }" />
        Refresh
      </Button>
    </div>

    <div class="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
      <Card
        v-for="p in plans"
        :key="p.id"
        class="relative gap-6 px-6 py-6"
        :class="{ 'outline-primary outline-2 outline-offset-2': editing?.id === p.id }"
      >
        <div class="flex items-start justify-between gap-3">
          <div>
            <h3 class="text-base font-medium">{{ p.title }}</h3>
            <p class="text-muted-foreground mt-1 font-mono text-xs">{{ p.id }}</p>
          </div>
          <StatusPill :tone="p.active ? 'ok' : 'neutral'">{{ p.active ? 'Active' : 'Inactive' }}</StatusPill>
        </div>

        <p class="flex items-baseline gap-1.5">
          <span class="text-[2rem] leading-none font-medium tabular-nums">{{ p.amount_iqd.toLocaleString() }}</span>
          <span class="text-muted-foreground text-sm">IQD per {{ perLabel(p.interval) }}</span>
        </p>

        <ul class="text-muted-foreground grid gap-1.5 border-t pt-4 text-sm">
          <li class="flex justify-between gap-3"><span>Length</span><span class="text-foreground tabular-nums">{{ p.days }} days</span></li>
          <li class="flex justify-between gap-3"><span>VPN quota</span><span class="text-foreground tabular-nums">{{ p.vpn_quota_gb }} GB</span></li>
          <li v-if="p.features_json?.length" class="flex justify-between gap-3">
            <span>Includes</span><span class="text-foreground text-end">{{ p.features_json.join(', ') }}</span>
          </li>
        </ul>

        <Button v-if="canWrite()" variant="outline" class="mt-auto w-full" @click="startEdit(p)">
          <Pencil />
          Edit plan
        </Button>
      </Card>
    </div>

    <Card v-if="editing" class="gap-6 py-6">
      <CardHeader class="px-6">
        <CardTitle>Edit {{ editing.title }}</CardTitle>
      </CardHeader>
      <CardContent class="space-y-5 px-6">
        <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <div class="grid gap-2">
            <Label for="plan-title">Plan name</Label>
            <Input id="plan-title" v-model="editing.title" />
          </div>
          <div class="grid gap-2">
            <Label for="plan-amount">Price (IQD)</Label>
            <Input id="plan-amount" v-model.number="editing.amount_iqd" type="number" />
          </div>
          <div class="grid gap-2">
            <Label for="plan-days">Length (days)</Label>
            <Input id="plan-days" v-model.number="editing.days" type="number" />
          </div>
          <div class="grid gap-2">
            <Label for="plan-quota">VPN quota (GB)</Label>
            <Input id="plan-quota" v-model.number="editing.vpn_quota_gb" type="number" />
          </div>
        </div>
        <label class="flex items-center gap-2.5 text-sm">
          <Checkbox v-model="notify" />
          Notify subscribers about this change
        </label>
        <div class="flex gap-2">
          <Button @click="save"><Save /> Save changes</Button>
          <Button variant="outline" @click="editing = null">Cancel</Button>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
