<script setup lang="ts">
// iOS `CheckToggleStyle` (generator options): a 48 × 24 capsule with a 24pt knob holding
// a check. iOS hard-codes the knob blue (a defect, design.md §2.2 rule 6); this uses
// accentDark.
import { Check } from 'lucide-vue-next'

defineProps<{ label: string; disabled?: boolean }>()
const model = defineModel<boolean>({ required: true })
</script>

<template>
  <button type="button" role="switch" class="row" :aria-checked="model" :disabled="disabled" @click="model = !model">
    <span class="title">{{ label }}</span>
    <span class="track" :class="{ on: model }">
      <span class="knob"><Check :size="11" :stroke-width="3.5" /></span>
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
  color: var(--text-primary);
}

.track {
  position: relative;
  flex-shrink: 0;
  width: 48px;
  height: 24px;
  border-radius: 999px;
  background: var(--stroke);
  transition: background-color var(--motion-standard);
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
  background: var(--stroke);
  color: transparent;
  transition:
    transform 250ms cubic-bezier(0.34, 1.3, 0.64, 1),
    background-color var(--motion-standard);
}

.track.on .knob {
  transform: translateX(24px);
  background: var(--accent-dark);
  color: #fff;
}

:root[dir='rtl'] .track.on .knob {
  transform: translateX(-24px);
}

@media (prefers-reduced-motion: reduce) {
  .knob {
    transition: none;
  }
}
</style>
