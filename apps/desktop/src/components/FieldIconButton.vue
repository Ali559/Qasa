<script setup lang="ts">
// iOS `FieldIconButton`: a 32-square trailing control in a field row (reveal, generate,
// copy, scan, calendar), 16 semibold glyph, secondary (accent for the row's action).
import type { Component } from 'vue'

defineProps<{ icon: Component; label: string; accent?: boolean; pressed?: boolean }>()
const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <button
    type="button"
    class="field-icon"
    :class="{ accent }"
    :aria-label="label"
    :title="label"
    :aria-pressed="pressed"
    @click.prevent="emit('click')"
  >
    <component :is="icon" :stroke-width="2.5" aria-hidden="true" />
  </button>
</template>

<style scoped>
.field-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.field-icon :deep(svg) {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.field-icon:hover {
  color: var(--text-primary);
}

.field-icon.accent,
.field-icon.accent:hover {
  color: var(--accent);
}

.field-icon:focus-visible {
  outline-offset: -2px;
}
</style>
