<script setup lang="ts">
// iOS `NoteColorPicker`: a `SectionLabel` 8 above six 28pt swatches (3 padding each, 12
// apart); the selected one gets a 2pt text-primary stroke on its edge.
import { useT } from '@/prefs'
import { NOTE_COLORS, NOTE_COLOR_VALUE } from '@/vault/present'
import type { NoteColor } from '@/vault/types'

const model = defineModel<NoteColor>({ required: true })
const t = useT()
</script>

<template>
  <div class="color-picker">
    <span class="label">{{ t('vault.color') }}</span>
    <div class="swatches" role="radiogroup" :aria-label="t('vault.color')">
      <button
        v-for="c in NOTE_COLORS"
        :key="c"
        type="button"
        role="radio"
        class="swatch"
        :class="{ selected: model === c }"
        :aria-checked="model === c"
        :aria-label="t(`color.${c}`)"
        :title="t(`color.${c}`)"
        :style="{ background: NOTE_COLOR_VALUE[c] }"
        @click="model = c"
      />
    </div>
  </div>
</template>

<style scoped>
.color-picker {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
}

.label {
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  color: var(--text-secondary);
}

.swatches {
  display: flex;
  gap: 12px;
}

.swatch {
  width: 28px;
  height: 28px;
  margin: 3px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

/* SwiftUI's `stroke` is centered on the edge: 1 outside, 1 inside. */
.swatch.selected {
  box-shadow:
    0 0 0 1px var(--text-primary),
    inset 0 0 0 1px var(--text-primary);
}
</style>
