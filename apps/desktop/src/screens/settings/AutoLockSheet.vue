<script setup lang="ts">
// iOS `AutoLockSettingsView`: a `SettingsSection` row (clock badge, "N minutes") with the
// 1–60 stepper and its 12pt help footer, then the presets (timer badges, a check on the
// current value). A pick applies at once; the sheet has no Done (design/README.md rule 4). Edits `prefs.autoLockMinutes`, which the Rust core
// reads for its timer.
import { Clock, Minus, Plus, Timer } from 'lucide-vue-next'
import { computed } from 'vue'

import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import Sheet from '@/components/Sheet.vue'
import { prefs, useT } from '@/prefs'

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
  PRESETS.map((minutes) => ({ value: String(minutes), label: t('settings.autoLockMinutesFmt', minutes), icon: Timer })),
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
  <Sheet :open="open" :label="t('settings.autoLock')" :title="t('settings.autoLock')" :width="460" placement="bottom" @close="emit('close')">
    <div class="body">
      <section class="group">
        <SettingsSection>
          <SettingsRow :icon="Clock" :title="t('settings.autoLockMinutesFmt', prefs.autoLockMinutes)">
            <template #trailing>
              <span
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
                <button
                  type="button"
                  tabindex="-1"
                  :aria-label="t('common.decrease')"
                  :disabled="prefs.autoLockMinutes <= MIN"
                  @click="setMinutes(prefs.autoLockMinutes - 1)"
                >
                  <Minus :stroke-width="2.5" aria-hidden="true" />
                </button>
                <span class="separator" />
                <button
                  type="button"
                  tabindex="-1"
                  :aria-label="t('common.increase')"
                  :disabled="prefs.autoLockMinutes >= MAX"
                  @click="setMinutes(prefs.autoLockMinutes + 1)"
                >
                  <Plus :stroke-width="2.5" aria-hidden="true" />
                </button>
              </span>
            </template>
          </SettingsRow>
        </SettingsSection>
        <p class="footer">{{ t('settings.autoLockHelp') }}</p>
      </section>

      <section class="group">
        <span class="label"><SectionLabel :text="t('settings.autoLockPresets')" /></span>
        <ChoiceList v-model="presetModel" :choices="presetChoices" :label="t('settings.autoLockPresets')" />
      </section>
    </div>
  </Sheet>
</template>

<style scoped>
.body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
  overflow-y: auto;
}

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-section-label-gap);
}

.label {
  padding-inline: 4px;
}

/* The iOS `Stepper`: 94 × 32, two halves on a muted fill, a hairline between them. */
.stepper {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  border-radius: 8px;
  background: var(--card-muted);
}

.stepper button {
  display: grid;
  place-items: center;
  width: 47px;
  height: 32px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  cursor: pointer;
}

.stepper button svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.stepper button:disabled {
  color: var(--text-tertiary);
  cursor: default;
}

.separator {
  width: var(--size-stroke);
  height: 18px;
  background: var(--stroke);
}

.footer {
  margin: 0;
  padding-inline: 4px;
  font-size: var(--type-caption-size);
  font-weight: var(--type-caption-weight);
  color: var(--text-secondary);
}
</style>
