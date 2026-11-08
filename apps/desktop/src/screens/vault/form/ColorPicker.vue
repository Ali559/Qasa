<script setup lang="ts">
// iOS `NoteColorPicker`: six 28pt swatches; the selected one gets a ring.
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
  gap: 8px;
}

.label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.swatches {
  display: flex;
  gap: 12px;
}

.swatch {
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
  transition: box-shadow var(--motion-fast);
}

.swatch.selected {
  box-shadow:
    0 0 0 2px var(--background),
    0 0 0 4px var(--text-primary);
}
</style>
