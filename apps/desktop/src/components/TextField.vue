<script setup lang="ts">
// iOS `ThemedTextField` on `QasaFieldChrome`: optional leading icon (20 wide, 12 from the
// text), card fill, 1px stroke, control radius, `--size-field-height` tall, 14 horizontal
// padding, 16 regular. Secure fields get the 32pt eye button (`FieldIconButton`).
// `multiline` is `QasaNotesChrome`: at least 112 tall, padding 10 × 8, growing with the text.
import { Eye, EyeOff } from 'lucide-vue-next'
import { computed, ref, type Component } from 'vue'

import { vAutoGrow } from './autoGrow'
import { useT } from '@/prefs'

const props = withDefaults(
  defineProps<{
    icon?: Component
    placeholder?: string
    secure?: boolean
    /** Show the reveal button on a secure field. */
    revealable?: boolean
    autofocus?: boolean
    label?: string
    multiline?: boolean
    mono?: boolean
    disabled?: boolean
    invalid?: boolean
  }>(),
  { secure: false, revealable: false },
)

const model = defineModel<string>({ required: true })
const emit = defineEmits<{ submit: [] }>()
const t = useT()
const revealed = ref(false)
const inputType = computed(() => (props.secure && !revealed.value ? 'password' : 'text'))
</script>

<template>
  <textarea
    v-if="multiline"
    v-model="model"
    v-auto-grow
    class="qasa-notes notes"
    :class="{ invalid, disabled }"
    :placeholder="placeholder"
    :aria-label="label ?? placeholder"
    :aria-invalid="invalid || undefined"
    :disabled="disabled"
    rows="1"
  />
  <label v-else class="qasa-field field" :class="{ disabled, invalid, 'has-trailing': (secure && revealable) || !!$slots.trailing }">
    <component :is="icon" v-if="icon" class="icon" aria-hidden="true" />
    <input
      v-model="model"
      :type="inputType"
      :class="{ mono }"
      :placeholder="placeholder"
      :aria-label="label ?? placeholder"
      :aria-invalid="invalid || undefined"
      :autofocus="autofocus"
      :disabled="disabled"
      autocomplete="off"
      autocorrect="off"
      autocapitalize="off"
      spellcheck="false"
      @keydown.enter="emit('submit')"
    />
    <span v-if="$slots.trailing" class="trailing-slot"><slot name="trailing" /></span>
    <button
      v-if="secure && revealable"
      type="button"
      class="reveal"
      :aria-label="revealed ? t('a11y.hidePassword') : t('a11y.showPassword')"
      @click.prevent="revealed = !revealed"
    >
      <EyeOff v-if="revealed" :stroke-width="2.5" />
      <Eye v-else :stroke-width="2.5" />
    </button>
  </label>
</template>

<style scoped>
.field {
  gap: 12px;
  cursor: text;
}

/* The trailing icon buttons sit inside the padding: 32 wide, 8 from the stroke. */
.field.has-trailing {
  padding-inline-end: calc(var(--space-field-horizontal) - (var(--size-field-icon-button) - var(--size-field-icon-glyph)) / 2);
}

.field.disabled,
.notes.disabled {
  opacity: var(--motion-disabled-opacity);
}

.trailing-slot {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.icon {
  flex-shrink: 0;
  width: 20px;
  height: var(--size-field-icon-glyph);
  color: var(--text-secondary);
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

.notes {
  /* Grows to 12 lines, then scrolls (iOS `lineLimit(4...12)`). */
  max-height: calc(12lh + 2 * var(--space-notes-vertical) + 2 * var(--size-stroke));
  user-select: text;
}

input.mono {
  font-family: var(--font-mono);
}

input::placeholder {
  color: var(--text-placeholder);
}

.reveal {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.reveal svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.reveal:hover {
  color: var(--text-primary);
}

.reveal:focus-visible {
  outline-offset: -2px;
}
</style>
