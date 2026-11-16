<script setup lang="ts">
// iOS `MintFAB`: a `size.fab` (56) accent circle, 50% accent border, the plain plus at
// `size.fabGlyph` (26) and a black 20% shadow (radius 8, y 4). No press effect, as iOS.
import QasaIcon from './QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

defineProps<{ icon: QasaIconName; label: string }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="fab" :aria-label="label" :title="label" @click="$emit('click')">
    <QasaIcon :name="icon" :size="26" />
  </button>
</template>

<style scoped>
.fab {
  display: grid;
  place-items: center;
  width: var(--size-fab);
  height: var(--size-fab);
  border: var(--size-stroke) solid color-mix(in srgb, var(--accent) 50%, transparent);
  border-radius: 50%;
  background: var(--accent);
  color: var(--on-accent);
  box-shadow: 0 4px 8px rgb(0 0 0 / 20%);
  cursor: pointer;
}

.fab svg {
  width: var(--size-fab-glyph);
  height: var(--size-fab-glyph);
}
</style>
