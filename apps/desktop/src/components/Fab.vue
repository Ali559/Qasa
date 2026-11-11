<script setup lang="ts">
// iOS `MintFAB`: 56pt accent square with the large radius (24), 50% accent border, an 18 bold
// glyph and a black 20% shadow (radius 8, y 4). No press effect, as iOS (default button).
import type { Component } from 'vue'

defineProps<{ icon: Component; label: string }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="fab" :aria-label="label" :title="label" @click="$emit('click')">
    <component :is="icon" :stroke-width="3" />
  </button>
</template>

<style scoped>
.fab {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: var(--size-stroke) solid color-mix(in srgb, var(--accent) 50%, transparent);
  border-radius: var(--radius-card-large);
  background: var(--accent);
  color: var(--on-accent);
  box-shadow: 0 4px 8px rgb(0 0 0 / 20%);
  cursor: pointer;
}

.fab svg {
  width: 18px;
  height: 18px;
}
</style>
