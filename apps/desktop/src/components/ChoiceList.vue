<script setup lang="ts" generic="T extends string">
// A one-choice group as iOS draws it (Welcome's language and theme rows, the settings
// pickers): a `SettingsSection` card of `SettingsRow`s, the badge tinted accent when
// selected and secondary otherwise, a trailing 15 bold check that fades and scales in
// (0.6 → 1, `motion.fast`), `RowDivider`s between rows and the `RowButtonStyle` wash.
import QasaIcon from './QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

export interface Choice<V extends string> {
  value: V
  label: string
  icon?: QasaIconName
  /** Set for a language name, so it is laid out in its own direction. */
  dir?: 'ltr' | 'rtl'
  lang?: string
}

defineProps<{ choices: Choice<T>[]; label: string }>()
const model = defineModel<T>({ required: true })
</script>

<template>
  <div class="choice-list settings-list" role="radiogroup" :aria-label="label">
    <button
      v-for="choice in choices"
      :key="choice.value"
      class="choice"
      :class="{ selected: model === choice.value }"
      type="button"
      role="radio"
      :aria-checked="model === choice.value"
      @click="model = choice.value"
    >
      <span v-if="choice.icon" class="badge" aria-hidden="true">
        <QasaIcon :name="choice.icon" :size="16" />
      </span>
      <span class="choice-label" :dir="choice.dir" :lang="choice.lang">{{ choice.label }}</span>
      <span class="spacer" />
      <QasaIcon name="check" :size="15" class="check" />
    </button>
  </div>
</template>

<style scoped>
.choice-list {
  display: flex;
  flex-direction: column;
  padding: var(--card-padding);
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.choice {
  --badge-color: var(--text-secondary);
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-row-icon-gap);
  width: 100%;
  min-height: var(--size-row-min-height);
  padding: var(--space-row-vertical) 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  text-align: start;
  cursor: pointer;
}

.choice.selected {
  --badge-color: var(--accent);
}

.choice::after {
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

.choice:hover::after,
.choice:active::after {
  opacity: var(--motion-row-pressed-opacity);
}

.choice + .choice::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: calc(var(--size-icon-badge) + var(--space-row-icon-gap));
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
}

.choice:not(:has(.badge)) + .choice::before {
  inset-inline-start: 0;
}

.choice:focus-visible {
  outline-offset: -2px;
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
  transition:
    color var(--motion-fast),
    background-color var(--motion-fast);
}

.badge :deep(svg) {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

/* The label keeps its own width so a language name in its own direction still sits at
   the row's start; the spacer takes the rest. */
.choice-label {
  font-size: var(--type-row-title-size);
  font-weight: var(--type-row-title-weight);
}

.spacer {
  flex: 1;
  min-width: 8px;
}

.check {
  flex-shrink: 0;
  width: 15px;
  height: 15px;
  color: var(--accent);
  opacity: 0;
  transform: scale(0.6);
  transition:
    opacity var(--motion-fast),
    transform var(--motion-fast);
}

.selected .check {
  opacity: 1;
  transform: none;
}
</style>
