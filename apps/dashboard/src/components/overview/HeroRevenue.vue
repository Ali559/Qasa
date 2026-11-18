<script setup lang="ts">
import { computed } from 'vue'
import { Download } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import type { OverviewKpi } from '@/api/admin'

const props = defineProps<{
  kpi: OverviewKpi
  growth: { month: string; subscribers: number }[]
  chartLabel?: string
}>()
const emit = defineEmits<{ export: [] }>()

// Round the axis up to a tidy step so the three gridlines carry readable numbers.
const axisMax = computed(() => {
  const peak = Math.max(...props.growth.map((d) => d.subscribers), 1)
  const step = 10 ** Math.max(Math.floor(Math.log10(peak)) - 1, 0) * 5
  return Math.ceil(peak / step) * step
})
const ticks = computed(() => [1, 0.5, 0].map((f) => Math.round(axisMax.value * f)))
const latest = computed(() => props.growth.at(-1))
const label = computed(() => props.chartLabel ?? 'Paid subscribers by month')
const metaTone = computed(() =>
  props.kpi.tone === 'up' ? 'text-ok' : props.kpi.tone === 'down' ? 'text-bad' : 'text-muted-foreground',
)
</script>

<template>
  <Card class="h-full gap-8 px-6 py-6" aria-labelledby="hero-label">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p id="hero-label" class="text-muted-foreground text-sm">{{ kpi.label }}</p>
        <p class="mt-2 flex items-baseline gap-2 text-5xl leading-none font-medium tabular-nums">
          {{ kpi.value }}
          <span v-if="kpi.unit" class="text-muted-foreground text-base font-normal">{{ kpi.unit }}</span>
        </p>
        <p v-if="kpi.meta" class="mt-3 text-sm tabular-nums" :class="metaTone">{{ kpi.meta }}</p>
      </div>
      <Button variant="outline" size="sm" @click="emit('export')">
        <Download />
        Export CSV
      </Button>
    </div>

    <figure class="mt-auto">
      <figcaption class="mb-4 flex items-baseline justify-between gap-3 text-sm">
        <span class="text-muted-foreground">{{ label }}</span>
        <span v-if="latest" class="tabular-nums">
          <span class="text-primary font-medium">{{ latest.subscribers.toLocaleString('en-US') }}</span>
          <span class="text-muted-foreground"> in {{ latest.month }}</span>
        </span>
      </figcaption>

      <div class="relative h-60 ps-10">
        <!-- Gridlines with their values; the plot sits on the bottom line. -->
        <div class="pointer-events-none absolute inset-0 flex flex-col justify-between" aria-hidden="true">
          <div v-for="t in ticks" :key="t" class="flex items-center gap-3">
            <span class="text-muted-foreground w-7 text-end font-mono text-[0.6875rem] tabular-nums">{{ t }}</span>
            <span class="bg-border h-px flex-1" />
          </div>
        </div>

        <ol class="relative flex h-full items-end gap-3 sm:gap-5" :aria-label="label">
          <li
            v-for="(d, i) in growth"
            :key="d.month"
            class="group flex h-full flex-1 items-end justify-center"
            :title="`${d.month}: ${d.subscribers.toLocaleString('en-US')}`"
          >
            <span
              class="bar w-full max-w-12 rounded-t-md transition-colors duration-150"
              :class="d === latest ? 'bg-primary' : 'bg-foreground/12 group-hover:bg-foreground/20'"
              :style="{ height: `${(d.subscribers / axisMax) * 100}%`, animationDelay: `${Math.min(i, 6) * 40}ms` }"
            />
            <span class="sr-only">{{ d.month }}: {{ d.subscribers }}</span>
          </li>
        </ol>
      </div>
      <ol class="mt-2 flex gap-3 ps-10 sm:gap-5" aria-hidden="true">
        <li
          v-for="d in growth"
          :key="d.month"
          class="flex-1 text-center font-mono text-[0.6875rem]"
          :class="d === latest ? 'text-foreground' : 'text-muted-foreground'"
        >{{ d.month }}</li>
      </ol>
    </figure>
  </Card>
</template>

<style scoped>
.bar {
  min-height: 2px;
  transform-origin: bottom;
  animation: rise 480ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes rise {
  from {
    transform: scaleY(0);
  }
}
</style>
