<script setup lang="ts">
// iOS `StatusPill`: capsule (1px strokeChrome) with an optional 8pt dot 8 from the label,
// padding 13 × 5, `Font.qasaLabel(12)` = 13 medium (`type.pill`), up to two lines.
defineProps<{ text: string; dotColor?: string }>()
</script>

<template>
  <span class="pill">
    <span v-if="dotColor" class="dot" :style="{ background: dotColor }" />
    <span class="text">{{ text }}</span>
  </span>
</template>

<style scoped>
.pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 13px;
  border: var(--size-stroke) solid var(--stroke-chrome);
  border-radius: 999px;
  background: var(--card-background-light);
  color: var(--text-on-chrome);
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  line-height: var(--line-height);
}

.dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.text {
  display: -webkit-box;
  overflow: hidden;
  text-align: start;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
</style>
