<script setup lang="ts">
// A vault date (birth, issue, ID expiry), stored as ISO `YYYY-MM-DD` (core `validate.rs`).
// Type it, or "Pick date" for a calendar that writes ISO. Underneath: the date as Qasa
// reads it, in the UI language, or the field-rule message. Reading is the core's
// `parse_date`, so a legacy stored form ("Nov 6, 2000") shows its date too until the
// next save rewrites it.
import { Calendar } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

import { prefs, useT } from '@/prefs'
import { fieldsApi, formatCivilDate, isoDate, type CivilDate } from '@/vault/fields'
import FormField from './FormField.vue'

defineProps<{ label: string; error?: string | null }>()
const model = defineModel<string>({ required: true })
const t = useT()
const picking = ref(false)
const picked = ref('')
const parsed = ref<CivilDate | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
let seq = 0

async function read(raw: string) {
  const mine = ++seq
  const date = raw.trim() ? await fieldsApi.parseDate(raw).catch(() => null) : null
  if (mine === seq) parsed.value = date
}

// Debounced: one IPC call once typing pauses.
watch(
  model,
  (raw) => {
    clearTimeout(timer)
    timer = setTimeout(() => read(raw), 150)
  },
  { immediate: true },
)
onBeforeUnmount(() => clearTimeout(timer))

function openPicker() {
  const now = new Date()
  picked.value = isoDate(parsed.value ?? { year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() })
  picking.value = true
}

function onPick() {
  // `<input type="date">` already gives `YYYY-MM-DD`, the stored form.
  if (/^\d{4}-\d{2}-\d{2}$/.test(picked.value)) model.value = picked.value
}
</script>

<template>
  <FormField v-model="model" :label="label" :placeholder="t('field.datePlaceholder')" :error="error">
    <template #below>
      <div class="below">
        <button type="button" class="pick" @click="picking ? (picking = false) : openPicker()">
          <Calendar v-if="!picking" :stroke-width="2.5" />
          <span>{{ picking ? t('date.done') : t('date.pick') }}</span>
        </button>
        <span v-if="parsed && !error" class="reading">{{ formatCivilDate(parsed, prefs.language) }}</span>
      </div>
      <input v-if="picking" v-model="picked" type="date" class="qasa-field calendar" :aria-label="t('date.pick')" @change="onPick" />
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

/* iOS: `Label("Pick date", systemImage: "calendar")`, 13 semibold accent. */
.pick {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: var(--type-row-value-size);
  font-weight: 600;
  cursor: pointer;
}

.pick svg {
  width: var(--type-row-value-size);
  height: var(--type-row-value-size);
}

/* iOS: the date as read, 13 regular secondary. */
.reading {
  font-size: var(--type-row-value-size);
  font-weight: var(--type-row-value-weight);
  color: var(--text-secondary);
  text-align: end;
}

.calendar {
  align-self: flex-start;
  width: auto;
  accent-color: var(--accent);
}
</style>
