<script setup lang="ts">
// The one toast (src/toast.ts): an accent pill with a check (danger with an alert icon for
// `error`), bottom-centre just above the BottomActionBar, announced politely to screen
// readers. Centred by a full-width flex row (no left/translate), so it sits right in RTL
// too; under reduced motion it only fades.
import QasaIcon from './QasaIcon.vue'
import { toast } from '@/toast'
</script>

<template>
  <!-- The live region stays mounted so screen readers notice each new message. -->
  <div class="toast-region" role="status" aria-live="polite" aria-atomic="true">
    <Transition name="toast">
      <div v-if="toast.visible" :key="toast.seq" class="toast" :class="toast.tone" @click="toast.visible = false">
        <QasaIcon :name="toast.tone === 'error' ? 'error' : 'success'" :size="17" class="toast-icon" />
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
  bottom: calc(var(--space-bottom-bar-vertical) * 3 + var(--size-button-height) + var(--size-stroke));
  z-index: 60;
  display: flex;
  justify-content: center;
  padding-inline: var(--space-screen-padding);
  pointer-events: none;
}

/* iOS `ToastView`: accent fill, onAccent text, 17 semibold icon 10 from 14 semibold text (two
   lines at most), padding 16 × 12, at least 44 tall, control radius, `shadow.toast`. */
.toast {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  max-width: 100%;
  min-height: 44px;
  padding: var(--space-toast-vertical) var(--space-toast-horizontal);
  border-radius: var(--radius-control);
  background: var(--accent);
  color: var(--on-accent);
  font-family: var(--font-family);
  font-size: var(--type-toast-size);
  font-weight: var(--type-toast-weight);
  line-height: var(--line-height);
  box-shadow: var(--shadow-toast);
  pointer-events: auto;
}

.toast.error {
  background: var(--danger);
  color: var(--on-danger);
}

.toast-icon {
  flex-shrink: 0;
  width: 17px;
  height: 17px;
}

.toast-text {
  display: -webkit-box;
  min-width: 0;
  overflow: hidden;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* iOS: `.move(edge: .bottom)` + opacity on the `toast` spring. */
.toast-enter-active,
.toast-leave-active {
  transition:
    opacity var(--motion-toast),
    transform var(--motion-toast);
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translateY(calc(100% + var(--space-bottom-bar-vertical) * 3 + var(--size-button-height)));
}

/* Reduce motion: fade only (`motion.fade`). */
@media (prefers-reduced-motion: reduce) {
  .toast-enter-active,
  .toast-leave-active {
    transition: opacity var(--motion-fade);
  }

  .toast-enter-from,
  .toast-leave-to {
    transform: none;
  }
}
</style>
