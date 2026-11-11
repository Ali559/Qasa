<script setup lang="ts">
// iOS `CardContainer`: card fill, 1pt stroke, `--radius-card` (20) corners, 17pt padding. Flat.
defineProps<{ padding?: number; muted?: boolean; borderColor?: string }>()
</script>

<template>
  <div class="card" :class="{ muted }" :style="{ '--card-padding': padding === undefined ? undefined : `${padding}px`, borderColor: borderColor }">
    <slot />
  </div>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  padding: var(--card-padding, 17px);
  background: var(--card-background);
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
}

.card.muted {
  background: var(--card-background-light);
}
</style>
