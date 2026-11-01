<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value: number
    label: string
    /** Fixed fill colour; when omitted the fill shifts mint → sunflower → red with load. */
    color?: string
    segments?: number
    size?: 'sm' | 'md'
  }>(),
  { segments: 10, size: 'md' },
)

const filled = computed(() => Math.round((Math.min(Math.max(props.value, 0), 100) / 100) * props.segments))
const fill = computed(() => {
  if (props.color) return props.color
  if (props.value >= 85) return 'var(--bad)'
  if (props.value >= 65) return 'var(--chart-3)'
  return 'var(--chart-4)'
})
</script>

<template>
  <div
    class="flex gap-1"
    role="meter"
    :aria-valuenow="value"
    aria-valuemin="0"
    aria-valuemax="100"
    :aria-label="label"
  >
    <span
      v-for="n in segments"
      :key="n"
      class="flex-1 rounded-full"
      :class="[size === 'sm' ? 'h-2' : 'h-3', n <= filled ? '' : 'bg-muted shadow-[inset_0_2px_0_var(--edge)]']"
      :style="n <= filled ? { background: fill } : undefined"
    />
  </div>
</template>
