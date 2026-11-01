<script setup lang="ts">
import { computed } from 'vue'
import { Card } from '@/components/ui/card'
import SegmentMeter from '@/components/kit/SegmentMeter.vue'
import StatusPill from '@/components/kit/StatusPill.vue'
import type { VpnNodeRow } from '@/api/admin'

const props = defineProps<{ node: VpnNodeRow }>()

const online = computed(() => props.node.status === 'online')
</script>

<template>
  <Card
    class="h-full gap-5 px-6 py-6"
    :style="online ? undefined : { '--lip': 'color-mix(in oklch, var(--warn) 70%, var(--background))' }"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="min-w-0">
        <h3 class="font-heading truncate text-2xl font-bold">{{ node.city }}</h3>
        <p class="text-muted-foreground mt-1 font-mono text-xs">{{ node.name }}, {{ node.ip }}, {{ node.service }}</p>
      </div>
      <StatusPill :tone="online ? 'ok' : 'warn'">{{ online ? 'Online' : 'High load' }}</StatusPill>
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
        <dd class="font-heading mt-0.5 text-lg font-bold">{{ node.peers }}</dd>
      </div>
    </dl>
  </Card>
</template>
