<script setup lang="ts">
// SF Symbol `checkmark.circle.fill` (Lucide has only the outlined circle).
import { Check } from 'lucide-vue-next'

withDefaults(defineProps<{ size?: number }>(), { size: 20 })
</script>

<template>
  <span class="filled" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <Check :size="Math.round(size * 0.6)" :stroke-width="3.5" />
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
