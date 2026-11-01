<script setup lang="ts">
import { computed } from 'vue'
import { EllipsisVertical, KeyRound, Pause, Pencil, Play, Trash2 } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import SegmentMeter from '@/components/kit/SegmentMeter.vue'
import StatusPill, { type PillTone } from '@/components/kit/StatusPill.vue'
import type { VpnNodeRow } from '@/api/admin'

const props = defineProps<{ node: VpnNodeRow; editable?: boolean }>()
const emit = defineEmits<{
  edit: []
  setStatus: [status: 'online' | 'draining']
  rotate: []
  retire: []
}>()

const online = computed(() => props.node.status === 'online')
const retired = computed(() => props.node.status === 'retired')

const statusPill = computed<{ tone: PillTone; label: string }>(() => {
  switch (props.node.status) {
    case 'online':
      return { tone: 'ok', label: 'Online' }
    case 'draining':
      return { tone: 'warn', label: 'Draining' }
    case 'retired':
      return { tone: 'neutral', label: 'Retired' }
    case 'warn':
      return { tone: 'warn', label: 'High load' }
    default:
      return { tone: 'bad', label: props.node.status }
  }
})

const heartbeat = computed(() => {
  const at = props.node.lastHeartbeat
  if (at === undefined) return null
  if (!at) return 'No heartbeat yet'
  const mins = Math.round((Date.now() - new Date(at).getTime()) / 60_000)
  if (mins < 1) return 'Heartbeat just now'
  if (mins < 60) return `Heartbeat ${mins} min ago`
  const hours = Math.round(mins / 60)
  return hours < 48 ? `Heartbeat ${hours} h ago` : `Heartbeat ${new Date(at).toLocaleDateString()}`
})

const peersLabel = computed(() =>
  props.node.maxPeers ? `${props.node.peers} / ${props.node.maxPeers}` : props.node.peers,
)
</script>

<template>
  <Card
    class="h-full gap-5 px-6 py-6"
    :class="retired ? 'opacity-60' : ''"
    :style="online || retired ? undefined : { '--lip': 'color-mix(in oklch, var(--warn) 70%, var(--background))' }"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <h3 class="font-heading truncate text-2xl font-bold">{{ node.city }}</h3>
        <p class="text-muted-foreground mt-1 font-mono text-xs">
          {{ node.name }}, {{ node.ip }}<template v-if="node.endpointPort">:{{ node.endpointPort }}</template>, {{ node.service }}
        </p>
        <p v-if="heartbeat" class="text-muted-foreground mt-0.5 text-xs">{{ heartbeat }}</p>
      </div>
      <div class="flex items-center gap-1">
        <StatusPill :tone="statusPill.tone">{{ statusPill.label }}</StatusPill>
        <DropdownMenu v-if="editable && node.id && !retired">
          <DropdownMenuTrigger as-child>
            <Button variant="ghost" size="icon-sm" :aria-label="`Actions for ${node.name}`">
              <EllipsisVertical />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" class="w-48 rounded-xl">
            <DropdownMenuItem @click="emit('edit')"><Pencil /> Edit</DropdownMenuItem>
            <DropdownMenuItem v-if="node.status !== 'online'" @click="emit('setStatus', 'online')">
              <Play /> Set online
            </DropdownMenuItem>
            <DropdownMenuItem v-else @click="emit('setStatus', 'draining')"><Pause /> Drain</DropdownMenuItem>
            <DropdownMenuItem @click="emit('rotate')"><KeyRound /> Rotate agent token</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem variant="destructive" @click="emit('retire')"><Trash2 /> Retire</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>

    <dl class="space-y-4">
      <div class="space-y-2">
        <div class="flex justify-between text-sm">
          <dt class="text-muted-foreground">CPU</dt>
          <dd class="font-semibold tabular-nums">{{ node.cpu }}%</dd>
        </div>
        <SegmentMeter :value="node.cpu" :label="`${node.city} CPU ${node.cpu}%`" size="sm" />
      </div>
      <div class="space-y-2">
        <div class="flex justify-between text-sm">
          <dt class="text-muted-foreground">Memory</dt>
          <dd class="font-semibold tabular-nums">{{ node.memoryLabel }}</dd>
        </div>
        <SegmentMeter :value="node.memory" :label="`${node.city} memory ${node.memory}%`" size="sm" />
      </div>
    </dl>

    <dl class="mt-auto grid grid-cols-2 gap-3">
      <div class="bg-muted/60 rounded-2xl px-4 py-3">
        <dt class="text-muted-foreground text-xs">Bandwidth</dt>
        <dd class="font-heading mt-0.5 text-lg font-bold">{{ node.bandwidth }}</dd>
      </div>
      <div class="bg-muted/60 rounded-2xl px-4 py-3">
        <dt class="text-muted-foreground text-xs">Connected peers</dt>
        <dd class="font-heading mt-0.5 text-lg font-bold">{{ peersLabel }}</dd>
      </div>
    </dl>
  </Card>
</template>
