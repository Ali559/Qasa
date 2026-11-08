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
  border: 1px solid;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
