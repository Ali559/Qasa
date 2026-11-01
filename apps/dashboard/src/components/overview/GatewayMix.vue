<script setup lang="ts">
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

defineProps<{
  gateways: { name: string; value: number; color: string }[]
}>()
</script>

<template>
  <Card class="h-full gap-6 py-6">
    <CardHeader class="px-6">
      <CardTitle class="text-xl font-bold">Revenue by payment gateway</CardTitle>
      <CardDescription>Share of revenue by source</CardDescription>
    </CardHeader>
    <CardContent class="space-y-6 px-6">
      <div class="flex h-8 gap-1.5" role="img" :aria-label="gateways.map((g) => `${g.name} ${g.value}%`).join(', ')">
        <span
          v-for="g in gateways"
          :key="g.name"
          class="h-full rounded-lg first:rounded-l-full last:rounded-r-full"
          :style="{
            width: `${g.value}%`,
            background: g.color,
            boxShadow: `inset 0 2px 0 rgb(255 255 255 / 0.35), 0 3px 0 color-mix(in oklch, ${g.color} 60%, black)`,
          }"
        />
      </div>
      <ul class="grid gap-3 sm:grid-cols-3">
        <li
          v-for="g in gateways"
          :key="g.name"
          class="bg-muted/60 flex items-center justify-between gap-3 rounded-2xl px-4 py-3"
        >
          <span class="flex items-center gap-2 text-sm font-semibold">
            <i class="size-3 rounded-full" :style="{ background: g.color }" />
            {{ g.name }}
          </span>
          <span class="font-heading text-2xl font-bold">{{ g.value }}%</span>
        </li>
      </ul>
    </CardContent>
  </Card>
</template>
