<script setup lang="ts">
// design/README.md rule 4, "Row": one row of a `FieldGroup` card. At least
// `--size-group-row-min-height` (60), padding 8 × 14; the label (12 semibold, secondary,
// accent while the row is focused, danger with an error) on top, the value line (default
// slot) right under it, an optional caption (`below`) inside the row, and trailing controls
// (32, vertically centered) at the trailing edge, `--space-group-row-trailing-gap` (8) after
// the value. Rows after the first get a 1px divider
// inset 14 on the leading side. With an error a 3px danger bar runs down the leading edge,
// inside the card's rounded corners; the message itself is drawn by the group.
//
// `id` is the row's control (input, a picker's opener): the label names it, and a click anywhere in
// the row that doesn't land on another control focuses it. A row with no single control
// (`control: false`) labels itself as a group instead.
import { inject, onBeforeUnmount, ref } from 'vue'

import { FIELD_GROUP } from './fieldGroup'

const props = withDefaults(
  defineProps<{
    id: string
    label: string
    error?: string | null
    disabled?: boolean
    control?: boolean
  }>(),
  { control: true },
)

const el = ref<HTMLElement | null>(null)
const group = inject(FIELD_GROUP, null)
if (!group && import.meta.env.DEV) console.warn(`GroupRow "${props.label}" outside a FieldGroup`)
group?.register({ id: props.id, label: () => props.label, error: () => props.error, el: () => el.value })
onBeforeUnmount(() => group?.unregister(props.id))

const INTERACTIVE = 'button, a, input, select, textarea, label'

function focusControl(e: MouseEvent) {
  if (!props.control || props.disabled) return
  if ((e.target as HTMLElement).closest(INTERACTIVE)) return
  document.getElementById(props.id)?.focus()
}
</script>

<template>
  <div
    ref="el"
    class="group-row"
    :class="{ invalid: !!error, disabled, 'has-trailing': !!$slots.trailing, clickable: control }"
    :role="control ? undefined : 'group'"
    :aria-labelledby="control ? undefined : `${id}-label`"
    @click="focusControl"
  >
    <span class="stripe-clip" aria-hidden="true"><span v-if="error" class="stripe" /></span>
    <div class="row-main">
      <label v-if="control" :for="id" class="row-label">{{ label }}</label>
      <span v-else :id="`${id}-label`" class="row-label">{{ label }}</span>
      <div class="row-value"><slot /></div>
      <slot name="below" />
    </div>
    <div v-if="$slots.trailing" class="row-trailing"><slot name="trailing" /></div>
  </div>
</template>

<style scoped>
.group-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-group-row-trailing-gap);
  min-width: 0;
  min-height: var(--size-group-row-min-height);
  padding: var(--space-group-row-vertical) var(--space-group-row-horizontal);
  color: var(--text-primary);
}

.group-row.clickable {
  cursor: text;
}

/* Trailing 32-wide `FieldIconButton`s keep their glyph 14 from the stroke. */
.group-row.has-trailing {
  padding-inline-end: calc(var(--space-group-row-horizontal) - (var(--size-field-icon-button) - var(--size-field-icon-glyph)) / 2);
}

.group-row.disabled {
  opacity: var(--motion-disabled-opacity);
}

/* The divider: 1px stroke, inset 14 on the leading side, to the trailing edge. */
.group-row + .group-row::after {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: var(--space-group-divider-inset);
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
  pointer-events: none;
}

/* The error bar's clip follows the card's inner corners on the first and last rows, so a
   row's own overflow is never clipped. */
.stripe-clip {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.group-row:first-child > .stripe-clip {
  border-start-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-start-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

.group-row:last-child > .stripe-clip {
  border-end-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-end-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

.stripe {
  position: absolute;
  inset-block: 0;
  inset-inline-start: 0;
  width: var(--size-group-error-stripe);
  background: var(--danger);
}

.row-main {
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.row-label {
  font-size: var(--type-group-label-size);
  font-weight: var(--type-group-label-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
  transition: color var(--motion-fast);
}

.group-row:focus-within .row-label {
  color: var(--accent);
}

.group-row.invalid .row-label {
  color: var(--danger);
}

.row-value {
  display: flex;
  align-items: center;
  gap: var(--space-button-icon-gap);
  min-width: 0;
  font-family: var(--font-family);
  font-size: var(--type-group-value-size);
  font-weight: var(--type-group-value-weight);
  line-height: var(--line-height);
  color: var(--text-primary);
}

.row-trailing {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

/* The value line's input: no chrome of its own. */
.row-value :deep(input:not([type='date'])),
.row-value :deep(textarea) {
  flex: 1;
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: inherit;
  font: inherit;
  caret-color: var(--accent);
  user-select: text;
}

.row-value :deep(input::placeholder) {
  color: var(--text-placeholder);
}
</style>
