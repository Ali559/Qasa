<script setup lang="ts">
// The iOS chevrons, redrawn to the SF Symbol's own frame, stroke and position (measured
// 2026-11-12), so the desktop matches iOS exactly; Android draws the same paths
// (`theme/QasaGlyphs.kt`). Lucide's chevrons were smaller and thinner. Don't size this with
// CSS: its width and height are the iOS frame. Mirrors in RTL through `.mirror-rtl`.
defineProps<{
  /** `back`: `chevron.backward` 17 semibold (13x18). `forward`: `chevron.forward` 13 semibold (11x14). */
  dir: 'back' | 'forward'
}>()
</script>

<template>
  <svg
    v-if="dir === 'back'"
    class="mirror-rtl"
    width="13"
    height="18"
    viewBox="0 0 13 18"
    fill="none"
    stroke="currentColor"
    stroke-width="2.32"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M8.79 2.26 2.16 9l6.63 6.74" />
  </svg>
  <svg
    v-else
    class="mirror-rtl"
    width="11"
    height="14"
    viewBox="0 0 11 14"
    fill="none"
    stroke="currentColor"
    stroke-width="1.81"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M3.11 1.91l5.79 5.065-5.79 5.065" />
  </svg>
</template>

<style scoped>
svg {
  flex-shrink: 0;
  display: block;
}
</style>
