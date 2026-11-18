<script setup lang="ts">
import { Card } from '@/components/ui/card'
import SegmentMeter from './SegmentMeter.vue'

// A stat is a number: a label, the figure and what it compares to. No icon tiles or mini charts.
defineProps<{
  label: string
  value: string
  unit?: string
  meta?: string
  tone?: string
  progress?: number
}>()

const toneClass = (tone?: string) =>
  tone === 'up' || tone === 'good' ? 'text-ok' : tone === 'down' || tone === 'bad' ? 'text-bad' : 'text-muted-foreground'
</script>

<template>
  <Card class="h-full justify-between gap-4 px-5 py-5">
    <p class="text-muted-foreground text-sm">{{ label }}</p>

    <div>
      <p class="flex items-baseline gap-1.5 text-[1.75rem] leading-none font-medium tabular-nums">
        {{ value }}
        <span v-if="unit" class="text-muted-foreground text-sm font-normal">{{ unit }}</span>
      </p>

      <template v-if="progress !== undefined">
        <SegmentMeter class="mt-4" :value="progress" :label="`${label}: ${progress}% of quota`" />
        <p class="text-muted-foreground mt-2 text-xs tabular-nums">{{ progress }}% of monthly quota used</p>
      </template>
      <p v-else-if="meta" class="mt-2 text-sm tabular-nums" :class="toneClass(tone)">{{ meta }}</p>
    </div>
  </Card>
</template>
