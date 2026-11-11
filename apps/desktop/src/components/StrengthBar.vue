<script setup lang="ts">
// iOS `StrengthBar`: segmented capsules, 2pt gaps, 6pt tall.
withDefaults(defineProps<{ segments: number; filled: number; color?: string; height?: number }>(), { height: 6 })
</script>

<template>
  <div class="bar" role="presentation">
    <span
      v-for="i in segments"
      :key="i"
      class="segment"
      :style="{ height: `${height}px`, background: i <= filled ? (color ?? 'var(--accent)') : 'var(--stroke)' }"
    />
  </div>
</template>

<style scoped>
.bar {
  display: flex;
  gap: 2px;
}

.segment {
  flex: 1;
  border-radius: 999px;
  transition: background-color var(--motion-standard);
}
</style>
