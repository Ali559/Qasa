<script setup lang="ts">
import { computed } from 'vue'
import { Download } from '@lucide/vue'
import { Button } from '@/components/ui/button'
import type { OverviewKpi } from '@/api/admin'

const props = defineProps<{
  kpi: OverviewKpi
  growth: { month: string; subscribers: number }[]
  chartLabel?: string
}>()
const emit = defineEmits<{ export: [] }>()

const peak = computed(() => Math.max(...props.growth.map((d) => d.subscribers), 1))
const latest = computed(() => props.growth.at(-1))
</script>

<template>
  <section
    class="hero bg-primary text-primary-foreground relative isolate flex h-full flex-col gap-8 overflow-hidden rounded-[2rem] p-6 sm:p-8"
    aria-labelledby="hero-label"
  >
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p id="hero-label" class="text-sm font-medium opacity-80">{{ kpi.label }}</p>
        <p class="font-heading hero-figure mt-1 flex items-baseline gap-3 font-extrabold">
          {{ kpi.value }}
          <span v-if="kpi.unit" class="text-2xl font-bold opacity-70">{{ kpi.unit }}</span>
        </p>
      </div>
      <p
        v-if="kpi.meta"
        class="bg-sun text-sun-ink sticker max-w-40 rotate-6 rounded-2xl px-4 py-3 text-sm leading-snug font-semibold"
      >
        {{ kpi.meta }}
      </p>
    </div>

    <div class="mt-auto">
      <div class="mb-3 flex items-center justify-between gap-3">
        <p class="text-sm font-semibold">{{ chartLabel ?? 'Paid subscribers by month' }}</p>
        <Button
          variant="secondary"
          size="sm"
          class="rounded-lg"
          @click="emit('export')"
        >
          <Download />
          Export CSV
        </Button>
      </div>
      <ol class="flex h-44 items-end gap-2 sm:gap-3" :aria-label="chartLabel ?? 'Paid subscribers by month'">
        <li
          v-for="(d, i) in growth"
          :key="d.month"
          class="flex h-full flex-1 flex-col items-center gap-2"
        >
          <!-- Every column shares one track height so bars compare honestly; 85% leaves room for the label. -->
          <span class="flex w-full flex-1 items-end">
            <span
              class="bar relative w-full rounded-t-xl rounded-b-md"
              :class="d === latest ? 'bg-sun' : 'bg-primary-foreground/22'"
              :style="{ height: `${(d.subscribers / peak) * 85}%`, animationDelay: `${i * 55}ms` }"
              :title="`${d.month}: ${d.subscribers} subscribers`"
            >
              <span
                v-if="d === latest"
                class="font-heading absolute inset-x-0 -top-7 text-center text-lg leading-none font-bold"
              >{{ d.subscribers }}</span>
            </span>
          </span>
          <span class="text-xs font-medium opacity-75">{{ d.month }}</span>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.hero {
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.25),
    0 6px 0 color-mix(in oklch, var(--primary) 55%, black),
    0 28px 40px -24px color-mix(in oklch, var(--primary) 70%, transparent);
}

/* Soft dotted texture keeps the slab from reading as a flat swatch. */
.hero::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(rgb(255 255 255 / 0.12) 1.2px, transparent 1.2px);
  background-size: 18px 18px;
  mask-image: linear-gradient(to bottom left, black, transparent 70%);
}

.hero-figure {
  font-size: clamp(3.25rem, 7vw, 6rem);
  font-variation-settings: 'wdth' 82;
  line-height: 0.95;
  letter-spacing: -0.03em;
}

.sticker {
  box-shadow: 0 4px 0 #b8861a;
}

.bar {
  min-height: 0.75rem;
  transform-origin: bottom;
  animation: rise 650ms cubic-bezier(0.34, 1.56, 0.64, 1) both;
  box-shadow: inset 0 2px 0 rgb(255 255 255 / 0.3);
}

@keyframes rise {
  from {
    transform: scaleY(0);
  }
}
</style>
