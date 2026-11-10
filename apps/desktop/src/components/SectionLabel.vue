<script setup lang="ts">
// iOS `SectionLabel`: sentence case, 13 medium (12 semibold in Arabic and Kurdish), secondary text.
defineProps<{ text: string }>()
</script>

<template>
  <h2 class="section-label">{{ text }}</h2>
</template>

<style scoped>
.section-label {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

:root:lang(ar) .section-label,
:root:lang(ckb) .section-label {
  font-size: 12px;
  font-weight: 600;
}
</style>
