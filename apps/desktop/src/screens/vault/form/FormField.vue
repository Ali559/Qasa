<script setup lang="ts">
// iOS `FormTextField`: section label over a 14pt-padded card field.
import { ref } from 'vue'

defineProps<{ label: string; secure?: boolean; multiline?: boolean; mono?: boolean; inputmode?: 'text' | 'tel' | 'email' | 'url' | 'numeric' }>()
const model = defineModel<string>({ required: true })
const id = `f-${Math.random().toString(36).slice(2, 9)}`
const focused = ref(false)
</script>

<template>
  <div class="form-field">
    <label :for="id" class="label">{{ label }}</label>
    <div class="box" :class="{ focused }">
      <textarea v-if="multiline" :id="id" v-model="model" rows="3" @focus="focused = true" @blur="focused = false" />
      <input
        v-else
        :id="id"
        v-model="model"
        :type="secure ? 'password' : 'text'"
        :inputmode="inputmode"
        :class="{ mono }"
        autocomplete="off"
        spellcheck="false"
        @focus="focused = true"
        @blur="focused = false"
      />
      <slot name="trailing" />
    </div>
    <slot name="below" />
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

:root:lang(ar) .label,
:root:lang(ckb) .label {
  font-size: 12px;
  font-weight: 600;
}

.box {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
  transition: border-color var(--motion-fast);
}

.box.focused {
  border-color: var(--accent);
}

input,
textarea {
  flex: 1;
  min-width: 0;
  padding: 14px 0;
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
  min-height: 72px;
  line-height: 1.4;
}

.mono {
  font-family: var(--font-mono);
}
</style>
