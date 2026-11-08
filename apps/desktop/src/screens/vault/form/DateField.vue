<script setup lang="ts">
// iOS `ValidatedDateField`: a typed date ("Nov 6 2000", "2000-11-06", "06/11/2000"…)
// validated strictly, plus "Pick date" for a calendar that writes the localized form.
import { Calendar } from 'lucide-vue-next'
import { computed, ref } from 'vue'

import { prefs, useT } from '@/prefs'
import { parseDate } from '@/vault/dates'
import FormField from './FormField.vue'

defineProps<{ label: string }>()
const model = defineModel<string>({ required: true })
const t = useT()
const picking = ref(false)
const picked = ref('')

const error = computed(() => (model.value.trim() && !parseDate(model.value) ? t('date.invalidFormat') : null))

function openPicker() {
  const d = parseDate(model.value) ?? new Date()
  picked.value = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
  picking.value = true
}

function onPick() {
  const [y, m, d] = picked.value.split('-').map(Number)
  if (!y) return
  // The localized medium form, as iOS's "MMMdyyyy" template writes it.
  model.value = new Date(y, m - 1, d).toLocaleDateString(prefs.language, { month: 'short', day: 'numeric', year: 'numeric' })
}
</script>

<template>
  <FormField v-model="model" :label="label">
    <template #below>
      <div class="below">
        <button type="button" class="pick" @click="picking ? (picking = false) : openPicker()">
          <Calendar :size="14" />
          {{ picking ? t('date.done') : t('date.pick') }}
        </button>
        <span v-if="error" class="error">{{ error }}</span>
      </div>
      <input v-if="picking" v-model="picked" type="date" class="calendar" :aria-label="t('date.pick')" @change="onPick" />
    </template>
  </FormField>
</template>

<style scoped>
.below {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pick {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.error {
  font-size: 11px;
  color: var(--danger);
  text-align: end;
}

.calendar {
  align-self: flex-start;
  padding: 10px 12px;
  border: 1px solid var(--stroke);
  border-radius: 12px;
  background: var(--card-background);
  color: var(--text-primary);
  font: inherit;
  accent-color: var(--accent);
}
</style>
