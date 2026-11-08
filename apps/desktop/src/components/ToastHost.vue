<script setup lang="ts">
import { toast } from '@/toast'
</script>

<template>
  <Transition name="toast">
    <div v-if="toast.visible" class="toast" role="status" aria-live="polite">{{ toast.message }}</div>
  </Transition>
</template>

<style scoped>
.toast {
  position: fixed;
  bottom: 28px;
  left: 50%;
  z-index: 60;
  transform: translateX(-50%);
  padding: 10px 18px;
  border-radius: 999px;
  background: var(--text-primary);
  color: var(--background);
  font-size: 14px;
  font-weight: 500;
  box-shadow: 0 6px 20px rgb(0 0 0 / 0.2);
  pointer-events: none;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--motion-standard),
    transform var(--motion-standard);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}
</style>
