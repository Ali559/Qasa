<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@lucide/vue'
import FadeIn from '@/components/FadeIn.vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card'
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
import { Progress } from '@/components/ui/progress'
import { vpnKpis, vpnNodes } from '@/data/mock'
import { toast } from 'vue-sonner'

const open = ref(false)
const city = ref('')
const ip = ref('')

function addNode() {
  if (!city.value || !ip.value) {
    toast.error('City and IP are required')
    return
  }
  toast.success('Node queued', { description: `${city.value} · ${ip.value}` })
  open.value = false
  city.value = ''
  ip.value = ''
}
</script>

<template>
  <div class="space-y-6">
    <div class="flex justify-end">
      <Dialog v-model:open="open">
        <DialogTrigger as-child>
          <Button>
            <Plus />
            Add New Node
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add VPN node</DialogTitle>
            <DialogDescription>Provision a new WireGuard edge endpoint.</DialogDescription>
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
            <Button @click="addNode">Provision</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      <FadeIn v-for="(kpi, i) in vpnKpis" :key="kpi.label" :delay="i * 0.05">
        <Card>
          <CardHeader class="pb-2">
            <CardDescription class="font-mono text-[11px] tracking-wider uppercase">
              {{ kpi.label }}
            </CardDescription>
            <CardTitle class="text-3xl font-semibold tracking-tight">{{ kpi.value }}</CardTitle>
          </CardHeader>
          <CardContent v-if="kpi.meta">
            <Badge variant="secondary" class="font-mono text-[11px]">↗ {{ kpi.meta }}</Badge>
          </CardContent>
        </Card>
      </FadeIn>
    </div>

    <div class="grid gap-4 lg:grid-cols-3">
      <FadeIn v-for="(node, i) in vpnNodes" :key="node.name" :delay="0.1 + i * 0.05">
        <Card class="h-full transition-transform hover:-translate-y-0.5">
          <CardHeader>
            <div class="flex items-start justify-between gap-3">
              <div>
                <CardTitle class="text-base" :class="{ 'text-amber-300': node.status === 'warn' }">
                  {{ node.name }} — {{ node.city }}
                </CardTitle>
                <CardDescription class="font-mono">{{ node.ip }} · {{ node.service }}</CardDescription>
              </div>
              <Badge :variant="node.status === 'online' ? 'secondary' : 'outline'">
                <span
                  class="mr-1.5 size-1.5 rounded-full"
                  :class="node.status === 'online' ? 'bg-emerald-400' : 'bg-amber-400'"
                />
                {{ node.status === 'online' ? 'Online' : 'High Load' }}
              </Badge>
            </div>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="space-y-2">
              <div class="flex justify-between text-xs">
                <span class="text-muted-foreground">CPU</span>
                <span class="font-mono">{{ node.cpu }}%</span>
              </div>
              <Progress :model-value="node.cpu" class="h-2" />
            </div>
            <div class="space-y-2">
              <div class="flex justify-between text-xs">
                <span class="text-muted-foreground">Memory</span>
                <span class="font-mono">{{ node.memoryLabel }}</span>
              </div>
              <Progress :model-value="node.memory" class="h-2" />
            </div>
          </CardContent>
          <CardFooter class="text-muted-foreground justify-between border-t text-xs">
            <span>Bandwidth <strong class="text-foreground ml-1">{{ node.bandwidth }}</strong></span>
            <span>Peers <strong class="text-foreground ml-1">{{ node.peers }}</strong></span>
          </CardFooter>
        </Card>
      </FadeIn>
    </div>

    <FadeIn :delay="0.25">
      <Card>
        <CardHeader>
          <CardTitle>Global Network Traffic</CardTitle>
          <CardDescription>Real-time aggregate bandwidth (Tx/Rx).</CardDescription>
        </CardHeader>
        <CardContent>
          <div class="mb-3 flex gap-4 text-xs">
            <span class="text-muted-foreground inline-flex items-center gap-2">
              <i class="bg-chart-2 h-0.5 w-4 rounded-full" /> Ingress (Rx)
            </span>
            <span class="text-muted-foreground inline-flex items-center gap-2">
              <i class="bg-chart-1 h-0.5 w-4 rounded-full" /> Egress (Tx)
            </span>
          </div>
          <svg viewBox="0 0 900 180" class="text-border h-48 w-full" preserveAspectRatio="none">
            <path
              d="M0 110 C90 90, 160 60, 240 80 S380 140, 470 95 S650 30, 740 70 S850 120, 900 95"
              fill="none"
              stroke="var(--chart-2)"
              stroke-width="2.5"
              class="animate-in fade-in duration-700"
            />
            <path
              d="M0 140 C100 120, 170 100, 250 115 S390 150, 480 120 S660 50, 750 90 S860 130, 900 115"
              fill="none"
              stroke="var(--chart-1)"
              stroke-width="2.5"
              class="animate-in fade-in duration-1000"
            />
          </svg>
        </CardContent>
      </Card>
    </FadeIn>
  </div>
</template>
