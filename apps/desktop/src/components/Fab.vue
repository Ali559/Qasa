<script setup lang="ts">
// iOS `MintFAB`: 56pt accent square with the large radius, 50% accent border, soft shadow.
import type { Component } from 'vue'

defineProps<{ icon: Component; label: string }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="fab" :aria-label="label" :title="label" @click="$emit('click')">
    <component :is="icon" :size="22" :stroke-width="2.75" />
  </button>
</template>

<style scoped>
.fab {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: 1px solid color-mix(in srgb, var(--accent) 50%, transparent);
  border-radius: var(--corner-radius-large);
  background: var(--accent);
  color: var(--on-accent);
  box-shadow: 0 4px 8px rgb(0 0 0 / 0.2);
  cursor: pointer;
  transition: transform var(--motion-press) ease-out;
}

.fab:active {
  transform: scale(0.95);
}
</style>
