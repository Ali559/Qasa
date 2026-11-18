<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: number
  label: string
  /** Fixed fill colour; when omitted the fill stays neutral and only turns warn/bad under real load. */
  color?: string
}>()

const clamped = computed(() => Math.min(Math.max(props.value, 0), 100))
const fill = computed(() => {
  if (props.color) return props.color
  if (props.value >= 85) return 'var(--bad)'
  if (props.value >= 65) return 'var(--warn)'
  return 'var(--muted-foreground)'
})
</script>

<template>
  <div
    class="bg-muted h-1.5 overflow-hidden rounded-full"
    role="meter"
    :aria-valuenow="value"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="label"
  >
    <span
      class="block h-full rounded-full transition-[width] duration-300"
      :style="{ width: `${clamped}%`, background: fill }"
    />
  </div>
</template>
