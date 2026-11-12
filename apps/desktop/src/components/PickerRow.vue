<script setup lang="ts" generic="V extends string | null">
// design/README.md rule 4, "Pickers, menus and dates": a menu as a `GroupRow` — the label
// on top, the chosen option under it (16 regular, primary), the 13 `RowChevron` trailing.
// A transparent button covers the row, so a click anywhere opens the `PickerSheet` (rule
// 6a, iOS `qasaPickerSheet`); `trailing` controls (a "new collection" button) sit beside
// the chevron, above it.
import { computed, ref } from 'vue'

import GroupRow from './GroupRow.vue'
import PickerSheet, { type PickerOption } from './PickerSheet.vue'
import QasaIcon from './QasaIcon.vue'
import { errorIdFor, rowId } from './fieldGroup'

const props = defineProps<{ label: string; options: Array<PickerOption<V>>; error?: string | null; disabled?: boolean }>()
const model = defineModel<V>({ required: true })
const id = rowId('picker')
const open = ref(false)
const chosen = computed(() => props.options.find((o) => o.value === model.value)?.label ?? '')
</script>

<template>
  <GroupRow :id="id" class="picker-row" :label="label" :error="error" :disabled="disabled">
    <span class="value">{{ chosen }}</span>
    <button
      :id="id"
      type="button"
      class="opener"
      :disabled="disabled"
      aria-haspopup="dialog"
      :aria-label="chosen ? `${label}, ${chosen}` : label"
      :aria-invalid="!!error || undefined"
      :aria-describedby="error ? errorIdFor(id) : undefined"
      @click="open = true"
    />
    <template #trailing>
      <slot name="trailing" />
      <QasaIcon name="chevron-forward" :size="13" class="chevron" />
    </template>
  </GroupRow>
  <PickerSheet v-model="model" :open="open" :title="label" :options="options" @close="open = false" />
</template>

<style scoped>
.picker-row {
  cursor: pointer;
}

/* The chevron ends the row, 14 from the stroke, with or without a button before it. */
.picker-row.has-trailing {
  padding-inline-end: var(--space-group-row-horizontal);
}

.value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.opener {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  opacity: 0;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

/* The opener is invisible: a keyboard user sees its row ringed instead. */
.picker-row:has(.opener:focus-visible) {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.picker-row:first-child:has(.opener:focus-visible) {
  border-start-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-start-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

.picker-row:last-child:has(.opener:focus-visible) {
  border-end-start-radius: calc(var(--radius-group) - var(--size-stroke));
  border-end-end-radius: calc(var(--radius-group) - var(--size-stroke));
}

/* Trailing buttons stay clickable above the opener. */
.picker-row :deep(.row-trailing) {
  position: relative;
  z-index: 1;
  gap: var(--space-button-icon-gap);
  pointer-events: none;
}

.picker-row :deep(.row-trailing button) {
  pointer-events: auto;
}

.chevron {
  flex-shrink: 0;
  color: var(--text-tertiary);
}

/* The opener is the row's control; its label never steals the click. */
.picker-row :deep(.row-label) {
  pointer-events: none;
}
</style>
