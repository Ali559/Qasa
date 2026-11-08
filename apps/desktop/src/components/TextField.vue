<script setup lang="ts">
// iOS `ThemedTextField`: leading icon, card fill, 1pt stroke, 16 × 18 padding. Secure
// fields get the eye toggle the screens add on iOS.
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
  <label class="field" :class="{ multiline, disabled, invalid }">
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
    <slot name="trailing" />
    <button
      v-if="secure && revealable"
      type="button"
      class="reveal"
      :aria-label="revealed ? t('a11y.hidePassword') : t('a11y.showPassword')"
      @click.prevent="revealed = !revealed"
    >
      <EyeOff v-if="revealed" :size="18" />
      <Eye v-else :size="18" />
    </button>
  </label>
</template>

<style scoped>
.field {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  min-height: 58px;
  background: var(--card-background);
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  cursor: text;
  transition: border-color var(--motion-fast) ease-in-out;
}

.field:focus-within {
  border-color: var(--accent);
}

.field.invalid {
  border-color: var(--danger);
}

.field.multiline {
  align-items: flex-start;
  padding-block: 16px;
}

.field.disabled {
  opacity: 0.6;
}

.icon {
  flex-shrink: 0;
  width: 20px;
  color: var(--text-secondary);
}

input,
textarea {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 16px;
  caret-color: var(--accent);
  user-select: text;
}

textarea {
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
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.reveal:hover {
  background: var(--card-muted);
}
</style>
