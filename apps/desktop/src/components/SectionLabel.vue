<script setup lang="ts">
// iOS `SectionLabel`: sentence case, 12 semibold, secondary text, in every language.
defineProps<{ text: string }>()
</script>

<template>
  <h2 class="section-label">{{ text }}</h2>
</template>

<style scoped>
.section-label {
  margin: 0;
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
}
</style>
