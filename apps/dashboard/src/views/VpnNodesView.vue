<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Activity, Gauge, Plus, Timer, UsersRound } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import StatTile from '@/components/kit/StatTile.vue'
import NodeCard from '@/components/vpn/NodeCard.vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { vpnKpis as mockVpnKpis, vpnNodes as mockVpnNodes } from '@/data/mock'
import { fetchVpnNodes, type VpnNodeRow } from '@/api/admin'
import { isLiveApiEnabled } from '@/api/client'
import { toast } from 'vue-sonner'

const vpnKpis = ref([...mockVpnKpis])
const vpnNodes = ref<VpnNodeRow[]>([...mockVpnNodes])

onMounted(async () => {
  if (!isLiveApiEnabled()) return
  try {
    const data = await fetchVpnNodes()
    vpnKpis.value = data.kpis
    vpnNodes.value = data.nodes
  } catch {
    toast.error('Failed to load VPN nodes')
  }
})

const kpiStyle = [
  { icon: UsersRound, accent: 'var(--chart-2)' },
  { icon: Gauge, accent: 'var(--chart-4)' },
  { icon: Timer, accent: 'var(--chart-3)' },
  { icon: Activity, accent: 'var(--chart-5)' },
]
const styleFor = (i: number) => kpiStyle[i % kpiStyle.length]!

const open = ref(false)
const city = ref('')
const ip = ref('')

function addNode() {
  if (!city.value || !ip.value) {
    toast.error('City and IP are required')
    return
  }
  toast.success('Node added', { description: `${city.value}, ${ip.value}` })
  open.value = false
  city.value = ''
  ip.value = ''
}
</script>

<template>
  <div class="space-y-6">
    <div class="grid gap-6 sm:grid-cols-2 xl:grid-cols-4">
      <StatTile
        v-for="(kpi, i) in vpnKpis"
        :key="kpi.label"
        :label="kpi.label"
        :value="kpi.value"
        :meta="kpi.meta"
        :icon="styleFor(i).icon"
        :accent="styleFor(i).accent"
      />
    </div>

    <div class="flex items-center justify-between gap-3">
      <h2 class="font-heading text-xl font-bold">Nodes</h2>
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <Button>
            <Plus />
            Add node
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle class="font-heading text-xl">Add a VPN node</DialogTitle>
            <DialogDescription>Registers a new WireGuard endpoint.</DialogDescription>
          </DialogHeader>
          <div class="grid gap-4 py-2">
            <div class="grid gap-2">
              <Label for="city">City</Label>
              <Input id="city" v-model="city" placeholder="Erbil" />
            </div>
            <div class="grid gap-2">
              <Label for="ip">IP address</Label>
              <Input id="ip" v-model="ip" placeholder="10.0.4.1" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" @click="open = false">Cancel</Button>
            <Button @click="addNode">Add node</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div class="grid gap-6 lg:grid-cols-3">
      <NodeCard v-for="node in vpnNodes" :key="node.name" :node="node" />
    </div>

    <Card class="gap-4 py-6">
      <CardHeader class="flex flex-row flex-wrap items-start justify-between gap-3 px-6">
        <div>
          <CardTitle class="text-xl font-bold">Network traffic</CardTitle>
          <CardDescription>Combined bandwidth across all nodes</CardDescription>
        </div>
        <div class="flex gap-4 text-sm font-medium">
          <span class="inline-flex items-center gap-2"><i class="bg-chart-2 size-3 rounded-full" /> Ingress (Rx)</span>
          <span class="inline-flex items-center gap-2"><i class="bg-chart-1 size-3 rounded-full" /> Egress (Tx)</span>
        </div>
      </CardHeader>
      <CardContent class="px-6">
        <svg viewBox="0 0 900 180" class="h-52 w-full" preserveAspectRatio="none" role="img" aria-label="Ingress and egress traffic over time">
          <path
            d="M0 110 C90 90, 160 60, 240 80 S380 140, 470 95 S650 30, 740 70 S850 120, 900 95 L900 180 L0 180 Z"
            fill="color-mix(in oklch, var(--chart-2) 16%, transparent)"
          />
          <path
            d="M0 110 C90 90, 160 60, 240 80 S380 140, 470 95 S650 30, 740 70 S850 120, 900 95"
            fill="none"
            stroke="var(--chart-2)"
            stroke-width="5"
            stroke-linecap="round"
            vector-effect="non-scaling-stroke"
          />
          <path
            d="M0 140 C100 120, 170 100, 250 115 S390 150, 480 120 S660 50, 750 90 S860 130, 900 115"
            fill="none"
            stroke="var(--chart-1)"
            stroke-width="5"
            stroke-linecap="round"
            vector-effect="non-scaling-stroke"
          />
        </svg>
      </CardContent>
    </Card>
  </div>
</template>
