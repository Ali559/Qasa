<script setup lang="ts">
// iOS `FormTextField`: a `SectionLabel` 8 above a `QasaFieldChrome` field (`.qasa-field`,
// base.css), or for notes a `QasaNotesChrome` box that starts at 112 and grows
// (`.qasa-notes`), with the field-rule message (if any) underneath.
import { ref } from 'vue'

import FieldError from './FieldError.vue'
import { vAutoGrow } from '@/components/autoGrow'

defineProps<{
  label: string
  secure?: boolean
  multiline?: boolean
  mono?: boolean
  inputmode?: 'text' | 'tel' | 'email' | 'url' | 'numeric'
  placeholder?: string
  maxlength?: number
  /** A localized field-rule message; shown under the field and marks it invalid. */
  error?: string | null
}>()
const model = defineModel<string>({ required: true })
const id = `f-${Math.random().toString(36).slice(2, 9)}`
const errorId = `${id}-error`
const focused = ref(false)
</script>

<template>
  <div class="form-field">
    <label :for="id" class="label">{{ label }}</label>
    <textarea
      v-if="multiline"
      :id="id"
      v-model="model"
      v-auto-grow
      class="qasa-notes notes"
      :class="{ invalid: !!error }"
      rows="1"
      :placeholder="placeholder ?? label"
      :maxlength="maxlength"
      :aria-invalid="!!error"
      :aria-describedby="error ? errorId : undefined"
    />
    <div v-else class="qasa-field box" :class="{ focused, invalid: !!error, 'has-trailing': !!$slots.trailing }">
      <input
        :id="id"
        v-model="model"
        :type="secure ? 'password' : 'text'"
        :inputmode="inputmode"
        :class="{ mono }"
        :placeholder="placeholder ?? label"
        :maxlength="maxlength"
        :aria-invalid="!!error"
        :aria-describedby="error ? errorId : undefined"
        autocomplete="off"
        spellcheck="false"
        @focus="focused = true"
        @blur="focused = false"
      />
      <slot name="trailing" />
    </div>
    <FieldError :id="errorId" :message="error" />
    <slot name="below" />
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

/* iOS `FormFieldLabel` = `SectionLabel`: 12 semibold, secondary. */
.label {
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  color: var(--text-secondary);
}

.box {
  gap: var(--space-button-icon-gap);
}

/* Trailing `FieldIconButton`s (32 wide) sit 8 from the stroke. */
.box.has-trailing {
  padding-inline-end: calc(var(--space-field-horizontal) - (var(--size-field-icon-button) - var(--size-field-icon-glyph)) / 2);
}

input {
  flex: 1;
  min-width: 0;
  height: 100%;
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

input::placeholder {
  color: var(--text-placeholder);
}

/* iOS adds 6 inside the notes chrome's 8 (`FormTextField`), and stops growing at 12 lines. */
.notes {
  padding-block: calc(var(--space-notes-vertical) + 6px);
  max-height: calc(12lh + 2 * (var(--space-notes-vertical) + 6px) + 2 * var(--size-stroke));
  user-select: text;
}

.mono {
  font-family: var(--font-mono);
}
</style>
