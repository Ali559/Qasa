<script setup lang="ts">
// iOS `FilterChip`: capsule, 14pt, accent outline when selected; light mode fills with
// accent at 16% and accentDark text, dark mode fills accentDark with onAccentChip text.
defineProps<{ title: string; selected: boolean }>()
defineEmits<{ click: [] }>()
</script>

<template>
  <button type="button" class="chip" :class="{ selected }" :aria-pressed="selected" @click="$emit('click')">
    {{ title }}
  </button>
</template>

<style scoped>
.chip {
  flex-shrink: 0;
  padding: 7px 17px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: 999px;
  background: var(--card-background);
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 400;
  line-height: var(--line-height);
  white-space: nowrap;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

/* iOS `PressableStyle`. */
.chip:active {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.chip.selected {
  border-color: var(--accent);
  background: color-mix(in srgb, var(--accent) 16%, transparent);
  color: var(--accent-dark);
  font-weight: 600;
}

:root[data-theme='dark'] .chip.selected {
  background: var(--accent-dark);
  color: var(--on-accent-chip);
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) .chip.selected {
    background: var(--accent-dark);
    color: var(--on-accent-chip);
  }
}
</style>
