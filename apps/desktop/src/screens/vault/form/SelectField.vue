<script setup lang="ts" generic="V extends string | null">
// iOS `FormPickerField`: a `SectionLabel` 8 above a menu `Picker` on `QasaFieldChrome`
// (48 tall, control radius, 1px stroke); the value and chevron in accent.
import { ChevronDown } from 'lucide-vue-next'

import FieldError from './FieldError.vue'

defineProps<{ label: string; options: Array<{ value: V; label: string }>; error?: string | null }>()
const model = defineModel<V>({ required: true })
const id = `s-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div class="select-field">
    <label :for="id" class="label">{{ label }}</label>
    <div class="qasa-field box" :class="{ invalid: !!error }">
      <select :id="id" v-model="model" :aria-invalid="!!error">
        <option v-for="o in options" :key="String(o.value)" :value="o.value">{{ o.label }}</option>
      </select>
      <ChevronDown :stroke-width="2.5" class="chevron" aria-hidden="true" />
      <slot name="trailing" />
    </div>
    <FieldError :message="error" />
  </div>
</template>

<style scoped>
.select-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
  min-width: 0;
}

.label {
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  color: var(--text-secondary);
}

.box {
  position: relative;
  gap: 8px;
}

select {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin: 0;
  padding: 0;
  padding-inline-end: calc(var(--size-row-chevron-glyph) + 8px);
  border: 0;
  outline: none;
  appearance: none;
  background: transparent;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
}

.chevron {
  position: absolute;
  inset-inline-end: var(--space-field-horizontal);
  width: var(--size-row-chevron-glyph);
  height: var(--size-row-chevron-glyph);
  color: var(--accent);
  pointer-events: none;
}

/* With a trailing `FieldIconButton` (32 wide) the box ends 8 from the stroke. */
.box:has(> :nth-child(3)) {
  padding-inline-end: calc(var(--space-field-horizontal) - (var(--size-field-icon-button) - var(--size-field-icon-glyph)) / 2);
}

.box:has(> :nth-child(3)) .chevron {
  inset-inline-end: calc(var(--size-field-icon-button) + 14px);
}
</style>
