<script setup lang="ts" generic="V extends string | null">
// design/README.md rule 4, "Pickers, menus and dates": a menu as a `GroupRow` — the label
// on top, the chosen option under it (16 regular, primary), the 13 `RowChevron` trailing.
// A native select covers the row, so a click anywhere opens the menu; `trailing` controls
// (a "new collection" button) sit beside the chevron, above the select.
import { ChevronRight } from 'lucide-vue-next'
import { computed } from 'vue'

import GroupRow from './GroupRow.vue'
import { errorIdFor, rowId } from './fieldGroup'

const props = defineProps<{ label: string; options: Array<{ value: V; label: string }>; error?: string | null; disabled?: boolean }>()
const model = defineModel<V>({ required: true })
const id = rowId('picker')
const chosen = computed(() => props.options.find((o) => o.value === model.value)?.label ?? '')
</script>

<template>
  <GroupRow :id="id" class="picker-row" :label="label" :error="error" :disabled="disabled">
    <span class="value">{{ chosen }}</span>
    <select
      :id="id"
      v-model="model"
      class="menu"
      :disabled="disabled"
      :aria-invalid="!!error || undefined"
      :aria-describedby="error ? errorIdFor(id) : undefined"
    >
      <option v-for="o in options" :key="String(o.value)" :value="o.value">{{ o.label }}</option>
    </select>
    <template #trailing>
      <slot name="trailing" />
      <ChevronRight class="chevron mirror-rtl" :stroke-width="2.5" aria-hidden="true" />
    </template>
  </GroupRow>
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

.menu {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  opacity: 0;
  font: inherit;
  cursor: pointer;
  appearance: none;
}

/* Trailing buttons stay clickable above the select. */
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
  width: var(--size-row-chevron-glyph);
  height: var(--size-row-chevron-glyph);
  color: var(--text-tertiary);
}

/* The select is the row's control; its label never steals the click. */
.picker-row :deep(.row-label) {
  pointer-events: none;
}
</style>
