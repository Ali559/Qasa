<script setup lang="ts">
// iOS `NoteColorPicker`: a `SectionLabel` 8 above six 28pt swatches (3 padding each, 12
// apart). The selected one gets a check in the canvas colour and a 2pt text-primary ring
// 2 outside it (iOS strokes the 34 frame), so the pick reads in both themes.
import QasaIcon from '@/components/QasaIcon.vue'
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
      >
        <QasaIcon v-if="model === c" name="check" :size="16" />
      </button>
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
  display: grid;
  place-items: center;
  color: var(--background);
  width: 28px;
  height: 28px;
  margin: 3px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

/* iOS strokes the 34 frame: a 2pt ring from 2 to 4 outside the swatch. */
.swatch.selected {
  outline: 2px solid var(--text-primary);
  outline-offset: 2px;
}
</style>
