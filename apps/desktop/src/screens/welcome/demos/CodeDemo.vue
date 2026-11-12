<script setup lang="ts">
// iOS tour page 3, `CodeDemo`: GitHub's two-step code next to its login, with a countdown
// ring; when the ring empties the code changes. Reduce motion: one code, the ring still.
import { onMounted, ref } from 'vue'

import { prefersReducedMotion, useTimers } from '../motion'
import TourDemoCard from './TourDemoCard.vue'
import TourDemoRow from './TourDemoRow.vue'

const CODES = ['482 913', '731 046', '205 588', '964 120']
const TICK_MS = 300
const RADIUS = 12
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

const { later } = useTimers()
const codeIndex = ref(0)
/** Time left on the code, 0…1. */
const left = ref(0.65)
/** Off for the one frame the ring jumps back to full, so it refills at once. */
const animateRing = ref(true)

function tick() {
  if (left.value - 0.05 <= 0) {
    codeIndex.value = (codeIndex.value + 1) % CODES.length
    animateRing.value = false
    left.value = 1
  } else {
    animateRing.value = true
    left.value -= 0.05
  }
  later(TICK_MS, tick)
}

onMounted(() => {
  if (!prefersReducedMotion()) later(400, tick)
})
</script>

<template>
  <TourDemoCard :order="0">
    <div class="top"><TourDemoRow icon="login" color="blue" title="GitHub" subtitle="you@example.com" ltr-subtitle /></div>
    <div class="code-row">
      <Transition name="code" mode="out-in">
        <span :key="codeIndex" class="code" dir="ltr">{{ CODES[codeIndex] }}</span>
      </Transition>
      <svg class="ring" viewBox="0 0 30 30" aria-hidden="true">
        <circle cx="15" cy="15" :r="RADIUS" class="track" />
        <circle
          cx="15"
          cy="15"
          :r="RADIUS"
          class="fill"
          :class="{ animate: animateRing }"
          :stroke-dasharray="CIRCUMFERENCE"
          :stroke-dashoffset="CIRCUMFERENCE * (1 - left)"
        />
      </svg>
    </div>
  </TourDemoCard>
</template>

<style scoped>
.top {
  padding-top: 4px;
}

.code-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px 16px;
}

.code {
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

.ring {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  /* Starts at 12 o'clock and runs clockwise, in RTL too. */
  transform: rotate(-90deg);
}

.ring circle {
  fill: none;
  stroke-width: 4;
}

.track {
  stroke: var(--stroke);
}

.fill {
  stroke: var(--accent);
  stroke-linecap: round;
}

.fill.animate {
  transition: stroke-dashoffset 300ms linear;
}

.code-enter-active,
.code-leave-active {
  transition:
    opacity var(--motion-standard),
    transform var(--motion-standard);
}

.code-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.code-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
