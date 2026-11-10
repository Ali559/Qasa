<script setup lang="ts">
// iOS `AutoLockSettingsView`: a 1–60 minute stepper with its help footer, then the presets
// with a checkmark on the current value. Edits `prefs.autoLockMinutes`, which the Rust core
// reads for its timer.
import { Minus, Plus } from 'lucide-vue-next'
import { computed } from 'vue'

import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import Sheet from '@/components/Sheet.vue'
import { prefs, useT } from '@/prefs'
import SheetHeader from './SheetHeader.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()
const t = useT()

const MIN = 1
const MAX = 60
const PRESETS = [1, 2, 5, 10, 15, 30, 45, 60]

function setMinutes(value: number) {
  prefs.autoLockMinutes = Math.min(MAX, Math.max(MIN, Math.round(value)))
}

const presetChoices = computed<Choice<string>[]>(() =>
  PRESETS.map((minutes) => ({ value: String(minutes), label: t('settings.autoLockMinutesFmt', minutes) })),
)

const presetModel = computed<string>({
  get: () => String(prefs.autoLockMinutes),
  set: (value) => setMinutes(Number(value)),
})

function onStepperKey(e: KeyboardEvent) {
  const step: Record<string, number> = { ArrowUp: 1, ArrowRight: 1, ArrowDown: -1, ArrowLeft: -1 }
  if (e.key === 'Home') setMinutes(MIN)
  else if (e.key === 'End') setMinutes(MAX)
  else if (e.key in step) setMinutes(prefs.autoLockMinutes + step[e.key])
  else return
  e.preventDefault()
}
</script>

<template>
  <Sheet :open="open" :label="t('settings.autoLock')" :width="460" placement="bottom" @close="emit('close')">
    <SheetHeader :title="t('settings.autoLock')" :done-label="t('settings.done')" @done="emit('close')" />
    <div class="body">
      <section class="group">
        <div
          class="stepper"
          role="spinbutton"
          tabindex="0"
          :aria-label="t('settings.autoLock')"
          :aria-valuemin="MIN"
          :aria-valuemax="MAX"
          :aria-valuenow="prefs.autoLockMinutes"
          :aria-valuetext="t('settings.autoLockMinutesFmt', prefs.autoLockMinutes)"
          @keydown="onStepperKey"
        >
          <span class="value">{{ t('settings.autoLockMinutesFmt', prefs.autoLockMinutes) }}</span>
          <span class="buttons">
            <button
              type="button"
              tabindex="-1"
              :aria-label="t('common.decrease')"
              :disabled="prefs.autoLockMinutes <= MIN"
              @click="setMinutes(prefs.autoLockMinutes - 1)"
            >
              <Minus :size="16" :stroke-width="2.5" aria-hidden="true" />
            </button>
            <span class="separator" />
            <button
              type="button"
              tabindex="-1"
              :aria-label="t('common.increase')"
              :disabled="prefs.autoLockMinutes >= MAX"
              @click="setMinutes(prefs.autoLockMinutes + 1)"
            >
              <Plus :size="16" :stroke-width="2.5" aria-hidden="true" />
            </button>
          </span>
        </div>
        <p class="footer">{{ t('settings.autoLockHelp') }}</p>
      </section>

      <section class="group">
        <SectionLabel :text="t('settings.autoLockPresets')" />
        <ChoiceList v-model="presetModel" class="presets" :choices="presetChoices" :label="t('settings.autoLockPresets')" />
      </section>
    </div>
  </Sheet>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 20px;
  overflow-y: auto;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
}

.value {
  flex: 1;
  font-size: 16px;
  color: var(--text-primary);
}

.buttons {
  display: flex;
  align-items: center;
  border-radius: 9px;
  background: var(--card-muted);
}

.buttons button {
  display: grid;
  place-items: center;
  width: 46px;
  height: 32px;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
}

.buttons button:disabled {
  color: var(--text-tertiary);
  cursor: default;
}

.separator {
  width: 1px;
  height: 18px;
  background: var(--stroke);
}

/* iOS list rows: plain 16pt text, not the Welcome picker's 17 semibold. */
.presets :deep(.choice) {
  padding-block: 12px;
}

.presets :deep(.choice-label) {
  font-size: 16px;
  font-weight: 400;
}

.footer {
  margin: 0;
  padding-inline: 4px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
