<script setup lang="ts">
// iOS `ThemedTextField`: leading icon, card fill, 1pt stroke, `--field-height` tall (base.css).
// Secure fields get the eye toggle the screens add on iOS.
import { Eye, EyeOff } from 'lucide-vue-next'
import { computed, ref, type Component } from 'vue'

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
  <label class="field" :class="{ multiline, disabled, invalid, 'has-trailing': !multiline && ((secure && revealable) || !!$slots.trailing) }">
    <component :is="icon" v-if="icon" class="icon" :size="18" aria-hidden="true" />
    <textarea
      v-if="multiline"
      v-model="model"
      :placeholder="placeholder"
      :aria-label="label ?? placeholder"
      :disabled="disabled"
      rows="4"
    />
    <input
      v-else
      v-model="model"
      :type="inputType"
      :class="{ mono }"
      :placeholder="placeholder"
      :aria-label="label ?? placeholder"
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
      <EyeOff v-if="revealed" :size="17" />
      <Eye v-else :size="17" />
    </button>
  </label>
</template>

<style scoped>
.field {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--field-height);
  padding-block: 0;
  padding-inline: var(--field-padding-inline);
  background: var(--card-background);
  border: 1px solid var(--stroke);
  border-radius: var(--field-radius);
  cursor: text;
  transition: border-color var(--motion-fast) ease-in-out;
}

.field:focus-within {
  border-color: var(--accent);
}

.field.invalid {
  border-color: var(--danger);
}

.field.has-trailing {
  padding-inline-end: 4px;
}

.field.multiline {
  height: auto;
  align-items: flex-start;
  padding-block: 10px;
}

.trailing-slot {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 2px;
}

.field.disabled {
  opacity: 0.6;
}

.icon {
  flex-shrink: 0;
  width: 18px;
  color: var(--text-secondary);
}

input,
textarea {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  height: 100%;
  margin: 0;
  padding: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: var(--field-font-size);
  caret-color: var(--accent);
  user-select: text;
}

textarea {
  height: auto;
  resize: vertical;
  min-height: 88px;
  line-height: 1.4;
}

input.mono {
  font-family: var(--font-mono);
}

input::placeholder,
textarea::placeholder {
  color: var(--text-placeholder);
}

.reveal {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.reveal:hover {
  background: var(--card-muted);
  color: var(--text-primary);
}

.reveal:focus-visible {
  outline-offset: -2px;
}
</style>
