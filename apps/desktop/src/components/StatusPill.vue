<script setup lang="ts">
// iOS `StatusPill`: capsule with an optional dot, 13 medium label.
defineProps<{ text: string; dotColor?: string }>()
</script>

<template>
  <span class="pill">
    <span v-if="dotColor" class="dot" :style="{ background: dotColor }" />
    {{ text }}
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 13px;
  border: 1px solid var(--stroke-chrome);
  border-radius: 999px;
  background: var(--card-background-light);
  color: var(--text-on-chrome);
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
</style>
