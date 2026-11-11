<script setup lang="ts">
// A vault date (birth, issue, ID expiry) as a date row of the item's `FieldGroup`
// (design/README.md rule 4), stored as ISO `YYYY-MM-DD` (core `validate.rs`). Type it (the
// format hint shows while empty), or the trailing calendar button opens a calendar inline
// in the row, under the input, that writes ISO. While the typed value is a date, the date
// as Qasa reads it, in the UI language, is a 12 secondary caption inside the row. Reading is the core's
// `parse_date`, so a legacy stored form ("Nov 6, 2000") shows its date too until the
// next save rewrites it.
import { Calendar, CalendarCheck } from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'

import { prefs, useT } from '@/prefs'
import { fieldsApi, formatCivilDate, isoDate, type CivilDate } from '@/vault/fields'
import FieldIconButton from '@/components/FieldIconButton.vue'
import FieldRow from '@/components/FieldRow.vue'

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
  <FieldRow v-model="model" :label="label" :placeholder="t('field.datePlaceholder')" :error="error">
    <template #trailing>
      <FieldIconButton
        :icon="picking ? CalendarCheck : Calendar"
        :label="picking ? t('date.done') : t('date.pick')"
        :pressed="picking"
        accent
        @click="picking ? (picking = false) : openPicker()"
      />
    </template>
    <template #below>
      <span v-if="parsed && !error" class="reading">{{ formatCivilDate(parsed, prefs.language) }}</span>
      <input v-if="picking" v-model="picked" type="date" class="calendar" :aria-label="t('date.pick')" @change="onPick" />
    </template>
  </FieldRow>
</template>

<style scoped>
/* The date as read: 12 regular secondary, under the input. */
.reading {
  font-size: var(--type-caption-size);
  font-weight: var(--type-caption-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
}

.calendar {
  align-self: flex-start;
  margin-block-start: var(--space-group-row-vertical);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  accent-color: var(--accent);
}
</style>
