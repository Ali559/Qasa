<script setup lang="ts">
// The one list row for settings-style cards (iOS `NavChevronRow` and the toggle rows): a
// fixed-height row with the icon in a rounded-square badge, title and optional subtitle,
// then trailing text and a chevron (mirrored in RTL), or whatever the `trailing` slot holds
// (a switch). Rows in the same card draw a hairline between them. With a `trailing` slot
// the row is not a button itself, so the switch stays the one control.
import { ChevronRight } from 'lucide-vue-next'
import { computed, useSlots, type Component } from 'vue'

const props = defineProps<{
  icon: Component
  title: string
  subtitle?: string
  trailingText?: string
  destructive?: boolean
  /** Tints the badge (any CSS colour, usually a token). */
  iconColor?: string
  disabled?: boolean
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
      <component :is="icon" :size="18" :stroke-width="2" />
    </span>
    <span class="text">
      <span class="title">{{ title }}</span>
      <span v-if="subtitle" class="subtitle">{{ subtitle }}</span>
    </span>
    <span v-if="trailingText" class="trailing-text">{{ trailingText }}</span>
    <slot name="trailing" />
    <ChevronRight v-if="interactive && !destructive" class="chevron mirror-rtl" :size="16" aria-hidden="true" />
  </component>
</template>

<style scoped>
.settings-row {
  --badge-color: var(--accent);
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: var(--row-height);
  margin: 0;
  padding-block: 8px;
  padding-inline: 16px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  text-align: start;
  transition: background-color var(--motion-fast) ease-out;
}

/* A hairline between rows in one card, starting under the text like iOS grouped lists. */
.settings-row + .settings-row::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: calc(16px + var(--row-badge-size) + 12px);
  inset-inline-end: 0;
  border-top: 1px solid var(--stroke);
}

.settings-row.interactive {
  cursor: pointer;
}

.settings-row.interactive:hover:not(:disabled) {
  background: var(--card-muted);
}

.settings-row.interactive:active:not(:disabled) {
  background: color-mix(in srgb, var(--accent) 12%, var(--card-muted));
}

.settings-row.interactive:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.settings-row:disabled,
.settings-row[aria-disabled='true'] {
  opacity: 0.5;
  cursor: default;
}

.badge {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--row-badge-size);
  height: var(--row-badge-size);
  border-radius: var(--row-badge-radius);
  background: color-mix(in srgb, var(--badge-color) 14%, transparent);
  color: var(--badge-color);
}

.text {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  min-width: 0;
}

.title {
  font-size: 15px;
  font-weight: 500;
  line-height: 1.3;
}

.subtitle {
  font-size: 12px;
  line-height: 1.35;
  color: var(--text-secondary);
}

.trailing-text {
  flex-shrink: 0;
  max-width: 40%;
  overflow: hidden;
  font-size: 13px;
  color: var(--text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
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
