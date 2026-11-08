<script setup lang="ts">
// iOS `FeatureRow`: accent `checkmark.circle.fill` and a 14pt line.
import FilledCheck from './FilledCheck.vue'

defineProps<{ text: string }>()
</script>

<template>
  <div class="feature">
    <FilledCheck :size="18" />
    <span>{{ text }}</span>
  </div>
</template>

<style scoped>
.feature {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  color: var(--text-primary);
}
</style>
