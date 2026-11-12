<script setup lang="ts">
// iOS tour `DemoCard`: the app's card surface at `--radius-card`. The desktop's cards carry
// the 1px stroke instead of iOS's soft shadow. `order` rises the whole card in, as iOS
// `Staggered` on the code and health demos.
defineProps<{ order?: number }>()
</script>

<template>
  <div class="demo-card" :class="{ rise: order !== undefined }" :style="order !== undefined ? { '--order': order } : undefined">
    <slot />
  </div>
</template>

<style scoped>
.demo-card {
  display: flex;
  flex-direction: column;
  width: 100%;
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

@media (prefers-reduced-motion: no-preference) {
  .rise {
    animation: rise var(--motion-tour-enter) both;
    animation-delay: calc(0.05s + var(--order) * var(--motion-tour-stagger) * 1s);
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.96);
  }
}
</style>
