<script setup lang="ts">
// iOS `CardContainer`: card fill, 1pt stroke, 20pt radius, 17pt padding. Flat.
withDefaults(defineProps<{ padding?: number; muted?: boolean; borderColor?: string }>(), { padding: 17 })
</script>

<template>
  <div
    class="card"
    :class="{ muted }"
    :style="{ padding: `${padding}px`, borderColor: borderColor }"
  >
    <slot />
  </div>
</template>

<style scoped>
.card {
  display: flex;
  flex-direction: column;
  background: var(--card-background);
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
}

.card.muted {
  background: var(--card-background-light);
}
</style>
