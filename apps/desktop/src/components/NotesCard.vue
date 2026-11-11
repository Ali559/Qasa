<script setup lang="ts">
// design/README.md rule 4, "Notes / multi-line": a multi-line field in its own card under
// the group — card fill, 1px stroke, `--radius-control` (10), the label inside at top
// `--space-group-notes-label-top` (9), the text under it; at least `--size-notes-min-height`
// (112) tall, growing with the text (`v-auto-grow`) up to 12 lines, then scrolling. Its
// message sits 6 under the card; with one the label turns danger and the 3px danger bar
// runs down the leading edge. A click anywhere in the card focuses the text.
import { vAutoGrow } from './autoGrow'
import { errorIdFor, rowId } from './fieldGroup'

defineProps<{
  label: string
  placeholder?: string
  maxlength?: number
  disabled?: boolean
  error?: string | null
}>()
const model = defineModel<string>({ required: true })
const id = rowId('notes')

function focusText(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('textarea, label')) return
  document.getElementById(id)?.focus()
}
</script>

<template>
  <div class="notes-field">
    <div class="notes-card" :class="{ invalid: !!error, disabled }" @click="focusText">
      <span v-if="error" class="stripe" aria-hidden="true" />
      <label :for="id" class="notes-label">{{ label }}</label>
      <textarea
        :id="id"
        v-model="model"
        v-auto-grow
        rows="1"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :disabled="disabled"
        :aria-invalid="!!error || undefined"
        :aria-describedby="error ? errorIdFor(id) : undefined"
      />
    </div>
    <span v-if="error" :id="errorIdFor(id)" class="field-error" role="alert">{{ error }}</span>
  </div>
</template>

<style scoped>
.notes-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-error-gap);
  min-width: 0;
}

.notes-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: var(--size-notes-min-height);
  padding: var(--space-group-notes-label-top) var(--space-group-row-horizontal) var(--space-group-row-vertical);
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  background: var(--card-background);
  cursor: text;
}

.notes-card.disabled {
  opacity: var(--motion-disabled-opacity);
}

.stripe {
  position: absolute;
  inset-block: 0;
  inset-inline-start: 0;
  width: var(--size-group-error-stripe);
  background: var(--danger);
}

.notes-label {
  font-size: var(--type-group-label-size);
  font-weight: var(--type-group-label-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
  transition: color var(--motion-fast);
}

.notes-card:focus-within .notes-label {
  color: var(--accent);
}

.notes-card.invalid .notes-label {
  color: var(--danger);
}

textarea {
  display: block;
  flex: 1;
  width: 100%;
  /* Grows to 12 lines, then scrolls (iOS `lineLimit(4...12)`). */
  max-height: 12lh;
  margin: 0;
  padding: 0;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--text-primary);
  font-family: var(--font-family);
  font-size: var(--type-group-value-size);
  font-weight: var(--type-group-value-weight);
  line-height: var(--line-height);
  caret-color: var(--accent);
  user-select: text;
}

textarea::placeholder {
  color: var(--text-placeholder);
}

.field-error {
  font-size: var(--type-field-error-size);
  font-weight: var(--type-field-error-weight);
  line-height: var(--line-height);
  color: var(--danger);
}
</style>
