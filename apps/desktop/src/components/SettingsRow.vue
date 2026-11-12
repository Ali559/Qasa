<script setup lang="ts">
// iOS `SettingsRow` / `NavChevronRow` / `ToggleRow`: at least 60 tall, 10 vertical padding,
// a 36 icon badge (radius 10, 16 glyph, tint at 12%) 14 from the text; title 15 medium,
// subtitle 12 regular 2 below; then trailing value (13, secondary) and a 13 chevron 8 apart,
// or whatever the `trailing` slot holds (a switch). Rows sit in a `SettingsSection` card
// (14 × 4 inset) with an iOS `RowDivider` between them, inset to the text. Pressed (and,
// on desktop, hovered) = `RowButtonStyle`: a 6% text-primary wash, radius 10, 8 past the
// row on each side. With a `trailing` slot the row is not a button itself.
import { computed, useSlots } from 'vue'

import QasaIcon from './QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

const props = defineProps<{
  icon: QasaIconName
  title: string
  subtitle?: string
  trailingText?: string
  destructive?: boolean
  /** Tints the badge (any CSS colour, usually a token). */
  iconColor?: string
  disabled?: boolean
  /** An action-sheet row: a button with no chevron. */
  noChevron?: boolean
}>()
defineEmits<{ click: [] }>()
const slots = useSlots()
const interactive = computed(() => !slots.trailing)
const badgeStyle = computed(() => (props.iconColor ? { '--badge-color': props.iconColor } : undefined))
</script>

<template>
  <component
    :is="interactive ? 'button' : 'div'"
    :type="interactive ? 'button' : undefined"
    class="settings-row"
    :class="{ destructive, interactive }"
    :disabled="interactive ? disabled : undefined"
    :aria-disabled="!interactive && disabled ? true : undefined"
    @click="interactive && !disabled && $emit('click')"
  >
    <span class="badge" :style="badgeStyle" aria-hidden="true">
      <QasaIcon :name="icon" :size="16" />
    </span>
    <span class="text">
      <span class="title">{{ title }}</span>
      <span v-if="subtitle" class="subtitle">{{ subtitle }}</span>
    </span>
    <span v-if="trailingText" class="trailing-text">{{ trailingText }}</span>
    <slot name="trailing" />
    <QasaIcon v-if="interactive && !destructive && !noChevron" name="chevron-forward" :size="13" class="chevron" />
  </component>
</template>

<style scoped>
.settings-row {
  --badge-color: var(--accent);
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-row-icon-gap);
  width: 100%;
  min-height: var(--size-row-min-height);
  margin: 0;
  padding: var(--space-row-vertical) 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  text-align: start;
}

/* iOS `RowButtonStyle`'s wash, drawn 8 past the row on both sides. */
.settings-row::after {
  content: '';
  position: absolute;
  inset-block: 0;
  inset-inline: -8px;
  border-radius: var(--radius-row-press);
  background: var(--text-primary);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion-press);
}

/* iOS `RowDivider`: 1px stroke from the text's leading edge (badge 36 + gap 14). */
.settings-row + .settings-row::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: calc(var(--size-icon-badge) + var(--space-row-icon-gap));
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
}

.settings-row.interactive {
  cursor: pointer;
}

.settings-row.interactive:hover:not(:disabled)::after,
.settings-row.interactive:active:not(:disabled)::after {
  opacity: var(--motion-row-pressed-opacity);
}

.settings-row.interactive:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.settings-row:disabled,
.settings-row[aria-disabled='true'] {
  opacity: var(--motion-disabled-opacity);
  cursor: default;
}

.badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--badge-color) 12%, transparent);
  color: var(--badge-color);
}

.badge :deep(svg) {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.text {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: var(--space-row-text-gap);
  min-width: 0;
}

.title {
  font-size: var(--type-row-title-size);
  font-weight: var(--type-row-title-weight);
}

.subtitle {
  font-size: var(--type-row-subtitle-size);
  font-weight: var(--type-row-subtitle-weight);
  color: var(--text-secondary);
}

.trailing-text {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Value and chevron sit 8 apart (iOS `NavChevronRow`), 14 from the text. */
.trailing-text + .chevron {
  margin-inline-start: calc(var(--space-button-icon-gap) - var(--space-row-icon-gap));
}

.chevron {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

.destructive {
  --badge-color: var(--danger);
}

.destructive .title {
  color: var(--danger);
}
</style>
