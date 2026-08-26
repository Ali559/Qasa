<script setup lang="ts">
import { Motion } from 'motion-v'

defineProps<{
  delay?: number
}>()
</script>

<template>
  <Motion
    as="div"
    :initial="{ opacity: 0, y: 12 }"
    :animate="{ opacity: 1, y: 0 }"
    :transition="{ duration: 0.35, delay: delay ?? 0, ease: [0.22, 1, 0.36, 1] }"
    class="h-full"
  >
    <slot />
  </Motion>
</template>
