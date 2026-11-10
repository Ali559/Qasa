<script setup lang="ts" generic="V extends string | null">
// iOS menu `Picker` in a card: label above, native select styled like a field.
import { ChevronDown } from 'lucide-vue-next'

import FieldError from './FieldError.vue'

defineProps<{ label: string; options: Array<{ value: V; label: string }>; error?: string | null }>()
const model = defineModel<V>({ required: true })
const id = `s-${Math.random().toString(36).slice(2, 9)}`
</script>

<template>
  <div class="select-field">
    <label :for="id" class="label">{{ label }}</label>
    <div class="box" :class="{ invalid: !!error }">
      <select :id="id" v-model="model" :aria-invalid="!!error">
        <option v-for="o in options" :key="String(o.value)" :value="o.value">{{ o.label }}</option>
      </select>
      <ChevronDown :size="16" class="chevron" aria-hidden="true" />
      <slot name="trailing" />
    </div>
    <FieldError :message="error" />
  </div>
</template>

<style scoped>
.select-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.box {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: var(--field-height);
  padding-inline: var(--field-padding-inline);
  border: 1px solid var(--stroke);
  border-radius: var(--field-radius);
  background: var(--card-background);
}

.box:focus-within {
  border-color: var(--accent);
}

.box.invalid {
  border-color: var(--danger);
}

select {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin: 0;
  padding: 0;
  padding-inline-end: 20px;
  border: 0;
  outline: none;
  appearance: none;
  background: transparent;
  color: var(--accent);
  font: inherit;
  font-size: var(--field-font-size);
  cursor: pointer;
}

.chevron {
  position: absolute;
  inset-inline-end: var(--field-padding-inline);
  color: var(--accent);
  pointer-events: none;
}

.box:has(> :nth-child(3)) {
  padding-inline-end: 4px;
}

.box:has(> :nth-child(3)) .chevron {
  inset-inline-end: 44px;
}
</style>
