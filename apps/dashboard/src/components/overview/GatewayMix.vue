<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

const props = withDefaults(
  defineProps<{
    gateways: { name: string; value: number; color?: string }[]
    description?: string
  }>(),
  { description: 'Share of revenue by source' },
)

// A share reads best as a ranked list: the leader carries the accent, the rest stay neutral.
const ranked = computed(() => [...props.gateways].sort((a, b) => b.value - a.value))
</script>

<template>
  <Card class="h-full gap-5 py-6">
    <CardHeader class="px-6">
      <CardTitle>Revenue by payment gateway</CardTitle>
      <CardDescription>{{ description }}</CardDescription>
    </CardHeader>
    <CardContent v-if="ranked.length === 0" class="px-6">
      <p class="text-muted-foreground text-sm">No successful payments in this period yet</p>
    </CardContent>
    <CardContent v-else class="px-6">
      <ol class="grid gap-4">
        <li v-for="(g, i) in ranked" :key="g.name" class="grid gap-2">
          <div class="flex items-baseline justify-between gap-3 text-sm">
            <span>{{ g.name }}</span>
            <span class="tabular-nums" :class="i === 0 ? 'font-medium' : 'text-muted-foreground'">{{ g.value }}%</span>
          </div>
          <div class="bg-muted h-1.5 overflow-hidden rounded-full" aria-hidden="true">
            <span
              class="block h-full rounded-full"
              :class="i === 0 ? 'bg-primary' : 'bg-muted-foreground/45'"
              :style="{ width: `${g.value}%` }"
            />
          </div>
        </li>
      </ol>
    </CardContent>
  </Card>
</template>
