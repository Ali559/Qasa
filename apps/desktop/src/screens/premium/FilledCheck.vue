<script setup lang="ts">
// SF Symbol `checkmark.circle.fill`: the library's check on a filled accent disc.
import QasaIcon from '@/components/QasaIcon.vue'

withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <span class="filled" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <QasaIcon name="check" :size="Math.round(size * 0.6)" />
  </span>
</template>

<style scoped>
.filled {
  display: inline-grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--accent);
  color: var(--on-accent);
}
</style>
