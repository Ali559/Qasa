<script setup lang="ts">
import type { Component } from 'vue'
import { Card } from '@/components/ui/card'
import SegmentMeter from './SegmentMeter.vue'

defineProps<{
  label: string
  value: string
  unit?: string
  meta?: string
  progress?: number
  icon: Component
  accent: string
}>()
</script>

<template>
  <Card class="h-full justify-between gap-5 px-5 py-5">
    <div class="flex items-center gap-3">
      <span
        class="grid size-10 shrink-0 -rotate-6 place-items-center rounded-xl text-[#1c1b33]"
        :style="{ background: accent, boxShadow: `0 3px 0 color-mix(in oklch, ${accent} 60%, black)` }"
        aria-hidden="true"
      >
        <component :is="icon" class="size-5" />
      </span>
      <p class="text-muted-foreground text-sm font-medium">{{ label }}</p>
    </div>

    <div>
      <p class="font-heading flex items-baseline gap-1.5 text-4xl leading-none font-bold">
        {{ value }}
        <span v-if="unit" class="text-muted-foreground text-base font-semibold">{{ unit }}</span>
      </p>

      <template v-if="progress !== undefined">
        <SegmentMeter class="mt-4" :value="progress" :color="accent" :label="`${label}: ${progress}% of quota`" />
        <p class="text-muted-foreground mt-2 text-xs">{{ progress }}% of monthly quota used</p>
      </template>
      <p v-else-if="meta" class="text-muted-foreground mt-2 text-sm">{{ meta }}</p>
    </div>
  </Card>
</template>
