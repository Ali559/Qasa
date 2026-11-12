<script setup lang="ts">
// iOS tour page 4, `HealthDemo`: the password health gauge sweeps to 82 over `motion.gauge`
// with "2 weak" and "1 reused" chips. Reduce motion: the gauge at 82.
import { onMounted, ref } from 'vue'

import { useT } from '@/prefs'
import { gaugeValue } from '../welcome'
import { durationMs, prefersReducedMotion, useTimers } from '../motion'
import TourDemoCard from './TourDemoCard.vue'

const TARGET = 82
/** The upper half of a circle, r 70, in a 168 × 100 box. */
const LENGTH = Math.PI * 70

const t = useT()
const { later, frame } = useTimers()
const score = ref(0)
const swept = ref(false)

onMounted(() => {
  if (prefersReducedMotion()) {
    score.value = TARGET
    swept.value = true
    return
  }
  const duration = durationMs('--motion-gauge-duration', 1100)
  later(300, () => {
    swept.value = true
    let start = 0
    const step = (now: number) => {
      if (!start) start = now
      score.value = gaugeValue(TARGET, (now - start) / duration)
      if (score.value < TARGET) frame(step)
    }
    frame(step)
  })
})
</script>

<template>
  <TourDemoCard :order="0">
    <div class="health">
      <span class="heading">{{ t('nav.passwordHealth') }}</span>
      <svg class="gauge" viewBox="0 0 168 100" aria-hidden="true">
        <path d="M14 90 A70 70 0 0 1 154 90" class="track" />
        <path
          d="M14 90 A70 70 0 0 1 154 90"
          class="arc"
          :stroke-dasharray="LENGTH"
          :stroke-dashoffset="swept ? LENGTH * (1 - TARGET / 100) : LENGTH"
        />
        <text x="84" y="86" text-anchor="middle" class="number">{{ score }}</text>
      </svg>
      <div class="chips">
        <span class="chip weak">{{ t('onboarding.mockWeak', 2) }}</span>
        <span class="chip reused">{{ t('onboarding.mockReused', 1) }}</span>
      </div>
    </div>
  </TourDemoCard>
</template>

<style scoped>
.health {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 0;
}

.heading {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

/* A gauge reads left to right in RTL too (iOS forces LTR on it). */
.gauge {
  display: block;
  width: 168px;
  height: 100px;
  direction: ltr;
}

.gauge path {
  fill: none;
  stroke-width: 12;
  stroke-linecap: round;
}

.track {
  stroke: var(--stroke);
}

.arc {
  stroke: var(--success);
  transition: stroke-dashoffset var(--motion-gauge);
}

.number {
  font-family: var(--font-family);
  font-size: 30px;
  font-weight: 700;
  fill: var(--text-primary);
}

.chips {
  display: flex;
  gap: 8px;
}

.chip {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.weak {
  color: var(--note-gold);
  background: var(--note-gold-tile);
}

.reused {
  color: var(--note-purple);
  background: var(--note-purple-tile);
}

@media (prefers-reduced-motion: reduce) {
  .arc {
    transition: none;
  }
}
</style>
