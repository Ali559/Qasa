<script setup lang="ts">
// The iOS system toggle tinted with the accent: 51 × 31 track, white knob.
defineProps<{ label: string; disabled?: boolean }>()
const model = defineModel<boolean>({ required: true })
</script>

<template>
  <button
    type="button"
    role="switch"
    class="switch"
    :aria-checked="model"
    :aria-label="label"
    :disabled="disabled"
    @click="model = !model"
  >
    <span class="knob" />
  </button>
</template>

<style scoped>
.switch {
  position: relative;
  flex-shrink: 0;
  width: 51px;
  height: 31px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: var(--stroke);
  cursor: pointer;
  transition: background-color var(--motion-standard) ease-in-out;
}

.switch[aria-checked='true'] {
  background: var(--accent);
}

.switch:disabled {
  opacity: 0.5;
  cursor: default;
}

.knob {
  position: absolute;
  top: 2px;
  inset-inline-start: 2px;
  width: 27px;
  height: 27px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 4px rgb(0 0 0 / 0.2);
  transition: transform var(--motion-standard) ease-in-out;
}

.switch[aria-checked='true'] .knob {
  transform: translateX(20px);
}

:root[dir='rtl'] .switch[aria-checked='true'] .knob {
  transform: translateX(-20px);
}
</style>
