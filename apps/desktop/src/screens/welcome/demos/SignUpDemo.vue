<script setup lang="ts">
// iOS tour page 2, `SignUpDemo`: a sign-up form where the email types itself
// (`tourTypeStep` a character), "Use a strong password" appears, is clicked, and the
// password fills with dots. Reduce motion: the filled form.
import { onMounted, ref } from 'vue'

import QasaIcon from '@/components/QasaIcon.vue'
import { useT } from '@/prefs'
import { durationMs, prefersReducedMotion, tokenMs, useTimers } from '../motion'
import TourDemoCard from './TourDemoCard.vue'

const EMAIL = 'you@example.com'
const PASSWORD = 'Kx9#mT2v!qL7&wR4'

const t = useT()
const { later } = useTimers()

const typed = ref('')
const passwordFocused = ref(false)
const suggesting = ref(false)
const tapped = ref(false)
const filled = ref(false)

onMounted(() => {
  if (prefersReducedMotion()) {
    typed.value = EMAIL
    passwordFocused.value = true
    filled.value = true
    return
  }
  const step = tokenMs('--motion-tour-type-step', 0.055)
  ;[...EMAIL].forEach((ch, i) => later(500 + i * step, () => (typed.value += ch)))
  const shown = 500 + EMAIL.length * step + 250
  later(shown, () => {
    passwordFocused.value = true
    suggesting.value = true
  })
  later(shown + 900, () => (tapped.value = true))
  later(shown + 900 + durationMs('--motion-press-duration', 120) + 80, () => {
    tapped.value = false
    suggesting.value = false
    filled.value = true
  })
})
</script>

<template>
  <TourDemoCard :order="0">
    <span class="heading">{{ t('onboarding.mockSignup') }}</span>
    <div class="field" :class="{ focus: !passwordFocused && typed }">
      <span class="label">{{ t('field.email') }}</span>
      <span class="value" dir="ltr">{{ typed || '\u00a0' }}</span>
    </div>
    <div class="field" :class="{ focus: passwordFocused }">
      <span class="label">{{ t('field.password') }}</span>
      <span class="value" dir="ltr">{{ filled ? '•'.repeat(16) : '\u00a0' }}</span>
    </div>
    <div class="suggest" :class="{ shown: suggesting, tapped }" aria-hidden="true">
      <span class="badge"><QasaIcon name="generate" :size="16" /></span>
      <span class="suggest-text">
        <span class="suggest-title">{{ t('fill.strongPassword') }}</span>
        <span class="suggest-value" dir="ltr">{{ PASSWORD }}</span>
      </span>
    </div>
  </TourDemoCard>
</template>

<style scoped>
.heading {
  padding: 14px 14px 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.field {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin: 0 14px 8px;
  padding: 6px 10px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  transition: border-color var(--motion-fast);
}

.field.focus {
  border-color: var(--accent);
}

.label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-tertiary);
}

/* Left to right in RTL too, but at the field's start like the label. */
.value {
  min-height: 21px;
  font-size: 14px;
  color: var(--text-primary);
}

.suggest {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 14px 12px;
  padding: 10px;
  border-radius: 12px;
  background: var(--card-muted);
  opacity: 0;
  transform: translateY(8px);
  transition:
    opacity var(--motion-standard),
    transform var(--motion-tour-enter),
    background-color var(--motion-press);
}

.suggest.shown {
  opacity: 1;
  transform: none;
}

.suggest.tapped {
  background: var(--note-purple-tile);
  transform: scale(0.97);
}

.badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: var(--card-background);
  color: var(--accent);
}

.suggest-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  min-width: 0;
}

.suggest-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.suggest-value {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
}

@media (prefers-reduced-motion: reduce) {
  .suggest {
    transition: none;
  }
}
</style>
