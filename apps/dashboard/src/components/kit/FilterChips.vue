<script setup lang="ts" generic="T extends string">
defineProps<{
  options: { value: T; label: string; count?: number }[]
  label: string
}>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="bg-muted inline-flex flex-wrap gap-0.5 rounded-[0.625rem] p-0.5" role="radiogroup" :aria-label="label">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      role="radio"
      :aria-checked="model === opt.value"
      class="inline-flex h-8 items-center gap-2 rounded-lg px-3 text-sm font-medium outline-none transition-colors duration-150 focus-visible:ring-3 focus-visible:ring-ring/50"
      :class="model === opt.value
        ? 'bg-card text-foreground shadow-[0_0_0_1px_var(--edge),0_1px_2px_rgb(0_0_0/0.06)]'
        : 'text-muted-foreground hover:text-foreground'"
      @click="model = opt.value"
    >
      {{ opt.label }}
      <span v-if="opt.count !== undefined" class="text-muted-foreground text-xs tabular-nums">{{ opt.count }}</span>
    </button>
  </div>
</template>
