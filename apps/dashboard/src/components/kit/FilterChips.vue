<script setup lang="ts" generic="T extends string">
defineProps<{
  options: { value: T; label: string; count?: number }[]
  label: string
}>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="flex flex-wrap gap-2" role="radiogroup" :aria-label="label">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      role="radio"
      :aria-checked="model === opt.value"
      class="chip inline-flex h-9 items-center gap-2 rounded-xl px-3.5 text-sm font-semibold outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
      :class="model === opt.value ? 'bg-foreground text-background' : 'bg-card text-foreground'"
      @click="model = opt.value"
    >
      {{ opt.label }}
      <span
        v-if="opt.count !== undefined"
        class="rounded-md px-1.5 text-xs tabular-nums"
        :class="model === opt.value ? 'bg-background/20' : 'bg-muted text-muted-foreground'"
      >{{ opt.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.chip {
  box-shadow: 0 3px 0 var(--lip);
  transition: translate 80ms ease, box-shadow 80ms ease;
}

.chip[aria-checked='true'] {
  translate: 0 2px;
  box-shadow: 0 1px 0 var(--lip);
}
</style>
