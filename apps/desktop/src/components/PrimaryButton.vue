<script setup lang="ts">
// iOS `PrimaryButton`: full width, accent fill, 18 semibold, 16pt vertical padding,
// accent glow. Press state from design.md §8.3: scale to 0.97 over 120ms.
import type { Component } from 'vue'

defineProps<{
  title: string
  icon?: Component
  /** Arrows and chevrons flip in RTL. */
  mirrorIcon?: boolean
  disabled?: boolean
}>()

defineEmits<{ click: [] }>()
</script>

<template>
  <button class="primary-button" type="button" :disabled="disabled" @click="$emit('click')">
    <component
      :is="icon"
      v-if="icon"
      :size="16"
      :stroke-width="2.5"
      :class="{ 'mirror-rtl': mirrorIcon }"
      aria-hidden="true"
    />
    <span>{{ title }}</span>
  </button>
</template>

<style scoped>
.primary-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 16px;
  border: 0;
  border-radius: var(--corner-radius);
  background: var(--accent);
  color: var(--on-accent);
  font-size: 18px;
  font-weight: 600;
  box-shadow: 0 0 10px color-mix(in srgb, var(--accent) 30%, transparent);
  cursor: pointer;
  transition:
    transform var(--motion-press) ease-out,
    filter var(--motion-press) ease-out;
}

.primary-button:hover:not(:disabled) {
  filter: brightness(1.05);
}

.primary-button:active:not(:disabled) {
  transform: scale(0.97);
}

.primary-button:disabled {
  opacity: 0.45;
  cursor: default;
  box-shadow: none;
}
</style>
