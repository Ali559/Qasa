<script setup lang="ts">
// design/README.md rule 4: a single-line text field as a `GroupRow` of a `FieldGroup` —
// the label on top, the input under it (16 regular), an example hint in the placeholder
// colour while empty (never the label). `secure` hides the text; `revealable` adds the
// eye `FieldIconButton` after any `trailing` controls. `prefix` sits before the input on
// the value line (a phone number's dial code); `below` is a caption inside the row.
import { Eye, EyeOff } from 'lucide-vue-next'
import { computed, ref } from 'vue'

import FieldIconButton from './FieldIconButton.vue'
import GroupRow from './GroupRow.vue'
import { errorIdFor, rowId } from './fieldGroup'
import { useT } from '@/prefs'

const props = defineProps<{
  label: string
  placeholder?: string
  secure?: boolean
  /** The eye button that shows a secure field's text. */
  revealable?: boolean
  mono?: boolean
  inputmode?: 'text' | 'tel' | 'email' | 'url' | 'numeric'
  maxlength?: number
  autofocus?: boolean
  disabled?: boolean
  /** A localized message: the row turns danger and the group prints it under the card. */
  error?: string | null
}>()
const model = defineModel<string>({ required: true })
const emit = defineEmits<{ submit: [] }>()
const t = useT()
const id = rowId('field')
const revealed = ref(false)
const inputType = computed(() => (props.secure && !revealed.value ? 'password' : 'text'))
</script>

<template>
  <GroupRow :id="id" :label="label" :error="error" :disabled="disabled">
    <slot name="prefix" />
    <input
      :id="id"
      v-model="model"
      :type="inputType"
      :class="{ mono }"
      :inputmode="inputmode"
      :placeholder="placeholder"
      :maxlength="maxlength"
      :autofocus="autofocus"
      :disabled="disabled"
      :aria-invalid="!!error || undefined"
      :aria-describedby="error ? errorIdFor(id) : undefined"
      autocomplete="off"
      autocorrect="off"
      autocapitalize="off"
      spellcheck="false"
      @keydown.enter="emit('submit')"
    />
    <template #below><slot name="below" /></template>
    <template v-if="$slots.trailing || (secure && revealable)" #trailing>
      <slot name="trailing" />
      <FieldIconButton
        v-if="secure && revealable"
        :icon="revealed ? EyeOff : Eye"
        :label="revealed ? t('a11y.hidePassword') : t('a11y.showPassword')"
        @click="revealed = !revealed"
      />
    </template>
  </GroupRow>
</template>

<style scoped>
.mono {
  font-family: var(--font-mono);
}
</style>
