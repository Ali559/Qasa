<script setup lang="ts">
// iOS tour `DemoRow`: an item-colour `IconBadge` 12 from a 15 semibold title over a 13
// secondary subtitle, 14 × 10 padding. `order` makes it rise in `--motion-tour-stagger`
// after the one before it (iOS `Staggered`); without it the row is just there.
import QasaIcon from '@/components/QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

export type DemoColor = 'purple' | 'gold' | 'mint' | 'blue'

defineProps<{
  icon: QasaIconName
  color: DemoColor
  title: string
  subtitle: string
  order?: number
  /** An email or a card number reads left to right in RTL too. */
  ltrSubtitle?: boolean
}>()
</script>

<template>
  <div class="demo-row" :class="{ rise: order !== undefined }" :style="order !== undefined ? { '--order': order } : undefined">
    <span class="badge" :style="{ color: `var(--note-${color})`, background: `var(--note-${color}-tile)` }" aria-hidden="true">
      <QasaIcon :name="icon" :size="16" />
    </span>
    <span class="text">
      <span class="title">{{ title }}</span>
      <span class="subtitle" :dir="ltrSubtitle ? 'ltr' : undefined">{{ subtitle }}</span>
    </span>
  </div>
</template>

<style scoped>
.demo-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
}

.demo-row + .demo-row {
  border-top: var(--size-stroke) solid var(--stroke);
}

.badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
}

.text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  min-width: 0;
}

.title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.subtitle {
  font-size: 13px;
  color: var(--text-secondary);
}

/* iOS `Staggered` on the `tourEnter` spring: 18 up, 0.96 scale, fade. */
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
