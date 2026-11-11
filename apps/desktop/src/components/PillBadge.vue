<script setup lang="ts">
// iOS `PillBadge`: 4pt radius tag, tinted fill at 10% with a 20% border, or solid.
defineProps<{ text: string; color: string; solid?: boolean }>()
</script>

<template>
  <span
    class="badge"
    :style="
      solid
        ? { background: color, color: 'var(--on-accent-chip)', borderColor: 'transparent' }
        : {
            background: `color-mix(in srgb, ${color} 10%, transparent)`,
            color,
            borderColor: `color-mix(in srgb, ${color} 20%, transparent)`,
          }
    "
    >{{ text }}</span
  >
</template>

<style scoped>
.badge {
  display: inline-block;
  padding: 2px 8px;
  border: var(--size-stroke) solid;
  border-radius: 4px;
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  line-height: var(--line-height);
  white-space: nowrap;
}
</style>
