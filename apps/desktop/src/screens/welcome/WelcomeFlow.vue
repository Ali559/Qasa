<script setup lang="ts">
// iOS `WelcomeFlowView` (design/README.md rule 6d): language, then the live tour of free
// features, then an optional name. Finishing marks the welcome done; App then shows
// Create vault. Steps slide in from the trailing edge, or fade with reduce motion.
import { ref } from 'vue'

import { prefs, setUserName } from '@/prefs'
import OnboardingTour from './OnboardingTour.vue'
import WelcomeLanguage from './WelcomeLanguage.vue'
import WelcomeName from './WelcomeName.vue'

const emit = defineEmits<{ done: [] }>()
const step = ref<'language' | 'tour' | 'name'>('language')

function finish(name: string) {
  setUserName(name)
  prefs.hasCompletedWelcome = true
  emit('done')
}
</script>

<template>
  <div class="flow">
    <Transition name="step">
      <WelcomeLanguage v-if="step === 'language'" class="step" @continue="step = 'tour'" />
      <OnboardingTour v-else-if="step === 'tour'" class="step" @skip="step = 'name'" @done="step = 'name'" />
      <WelcomeName v-else class="step" @done="finish" />
    </Transition>
  </div>
</template>

<style scoped>
.flow {
  position: relative;
  height: 100%;
  overflow: hidden;
  background: var(--background);
}

.step {
  position: absolute;
  inset: 0;
}

/* iOS: in from the trailing edge with a fade, out with a fade (`motion.standard`). */
.step-enter-active {
  transition:
    opacity var(--motion-standard),
    transform var(--motion-standard);
}

.step-leave-active {
  transition: opacity var(--motion-standard);
}

.step-enter-from {
  opacity: 0;
  transform: translateX(32px);
}

:dir(rtl).step-enter-from {
  transform: translateX(-32px);
}

.step-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .step-enter-active,
  .step-leave-active {
    transition: opacity var(--motion-fade);
  }

  .step-enter-from,
  :dir(rtl).step-enter-from {
    transform: none;
  }
}
</style>
