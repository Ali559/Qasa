<script setup lang="ts">
// iOS `CheckToggleStyle` + `LocalizedToggleRow` (generator options): 14 regular title, 8
// vertical padding; a 48 × 24 capsule (accent on, stroke off) with a 24pt knob (card fill
// with an 11 bold accent check on, tertiary text off) moving 24 on the `toggle` spring.
// Disabled: opacity .7 and a tertiary title.
import QasaIcon from './QasaIcon.vue'

defineProps<{ label: string; disabled?: boolean }>()
const model = defineModel<boolean>({ required: true })
</script>

<template>
  <button type="button" role="switch" class="row" :aria-checked="model" :disabled="disabled" @click="model = !model">
    <span class="title">{{ label }}</span>
    <span class="track" :class="{ on: model }">
      <span class="knob"><QasaIcon name="check" :size="11" /></span>
    </span>
  </button>
</template>

<style scoped>
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 8px 0;
  border: 0;
  background: transparent;
  text-align: start;
  cursor: pointer;
}

.row:disabled {
  opacity: 0.7;
  cursor: default;
}

.title {
  font-size: 14px;
  font-weight: 400;
  color: var(--text-primary);
}

.row:disabled .title {
  color: var(--text-tertiary);
}

.track {
  position: relative;
  flex-shrink: 0;
  width: 48px;
  height: 24px;
  border-radius: 999px;
  background: var(--stroke);
}

.track.on {
  background: var(--accent);
}

.knob {
  position: absolute;
  top: 0;
  inset-inline-start: 0;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--text-tertiary);
  color: var(--accent);
  transition: transform var(--motion-toggle);
}

.knob svg {
  width: 11px;
  height: 11px;
  opacity: 0;
}

.track.on .knob {
  transform: translateX(24px);
  background: var(--card-background);
}

.track.on .knob svg {
  opacity: 1;
}

:root[dir='rtl'] .track.on .knob {
  transform: translateX(-24px);
}
</style>
