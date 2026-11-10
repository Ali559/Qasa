<script setup lang="ts">
// The one toast (src/toast.ts): an accent pill with a check (danger with an alert icon for
// `error`), bottom-centre just above the BottomActionBar, announced politely to screen
// readers. Centred by a full-width flex row (no left/translate), so it sits right in RTL
// too; under reduced motion it only fades.
import { Check, CircleAlert } from 'lucide-vue-next'

import { toast } from '@/toast'
</script>

<template>
  <!-- The live region stays mounted so screen readers notice each new message. -->
  <div class="toast-region" role="status" aria-live="polite" aria-atomic="true">
    <Transition name="toast">
      <div v-if="toast.visible" :key="toast.seq" class="toast" :class="toast.tone">
        <CircleAlert v-if="toast.tone === 'error'" class="toast-icon" :size="16" :stroke-width="2.5" aria-hidden="true" />
        <Check v-else class="toast-icon" :size="16" :stroke-width="2.5" aria-hidden="true" />
        <span class="toast-text">{{ toast.message }}</span>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.toast-region {
  position: fixed;
  inset-inline: 0;
  /* Clear of a BottomActionBar: its padding twice, a button, its hairline, then a gap. */
  bottom: calc(var(--action-bar-padding) * 2 + var(--button-height) + 1px + 12px);
  z-index: 60;
  display: flex;
  justify-content: center;
  padding-inline: var(--screen-padding, 16px);
  pointer-events: none;
}

.toast {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: min(440px, 100%);
  padding: 10px 16px;
  border-radius: var(--button-radius);
  background: var(--accent);
  color: var(--on-accent);
  font-family: var(--font-body);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.35;
  box-shadow:
    0 2px 6px var(--shadow-color),
    0 10px 28px var(--shadow-color);
}

.toast.error {
  background: var(--danger);
  color: var(--on-danger);
}

.toast-icon {
  flex-shrink: 0;
}

.toast-text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--motion-standard) ease-out,
    transform var(--motion-standard) ease-out;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active {
    transition: opacity 120ms linear;
  }

  .toast-enter-from,
  .toast-leave-to {
    transform: none;
  }
}
</style>
