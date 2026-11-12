<script setup lang="ts" generic="V extends string | null">
// iOS `qasaPickerSheet` (`OwnSheets.swift`, design/README.md rule 6a): a dropdown as a bottom
// sheet, the field's label as the shared header, then one card of rows: the option's label
// (`type.rowTitle`), at least `size.rowMinHeight` (60), a `textPrimary` 16 `check` trailing
// on the current choice, a 1px stroke divider inset 14 between rows. A pick closes it.
// More than 8 options (countries) add the plain search field under the header, matching the
// label or the hidden `search` text (a dial code), and open at full height with the list
// scrolling; with no match, `common.noMatches`. Arrow keys move between the rows.
import { computed, nextTick, ref, watch } from 'vue'

import QasaIcon from './QasaIcon.vue'
import Sheet from './Sheet.vue'
import TextField from './TextField.vue'
import { useT } from '@/prefs'

export interface PickerOption<V> {
  value: V
  label: string
  /** Matched by the search without being shown (a country's dial code). */
  search?: string
  /** Set for a language name, so it is laid out in its own direction. */
  dir?: 'ltr' | 'rtl'
  lang?: string
}

/** iOS `pickerSheetSearchThreshold`. */
const SEARCH_THRESHOLD = 8

const props = defineProps<{ open: boolean; title: string; options: Array<PickerOption<V>> }>()
const model = defineModel<V>({ required: true })
const emit = defineEmits<{ close: [] }>()
const t = useT()
const query = ref('')
const list = ref<HTMLElement | null>(null)
const isLong = computed(() => props.options.length > SEARCH_THRESHOLD)

const shown = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  if (!q) return props.options
  return props.options.filter((o) => o.label.toLocaleLowerCase().includes(q) || !!o.search?.toLocaleLowerCase().includes(q))
})

watch(
  () => props.open,
  async (open) => {
    if (!open) return
    query.value = ''
    await nextTick()
    list.value?.querySelector<HTMLElement>('.option.selected')?.scrollIntoView({ block: 'center' })
  },
)

function pick(value: V) {
  model.value = value
  emit('close')
}

/** Return in the search picks the first match. */
function pickFirst() {
  const first = shown.value[0]
  if (first) pick(first.value)
}

function onKey(e: KeyboardEvent) {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
  const rows = [...(list.value?.querySelectorAll<HTMLElement>('.option') ?? [])]
  if (!rows.length) return
  e.preventDefault()
  const i = rows.indexOf(document.activeElement as HTMLElement)
  if (i === -1) rows[e.key === 'ArrowDown' ? 0 : rows.length - 1].focus()
  else if (e.key === 'ArrowUp' && i === 0 && isLong.value) list.value?.parentElement?.querySelector<HTMLElement>('input')?.focus()
  else rows[Math.min(Math.max(i + (e.key === 'ArrowDown' ? 1 : -1), 0), rows.length - 1)].focus()
}
</script>

<template>
  <Sheet :open="open" :label="title" :title="title" :width="420" placement="bottom" :fill="isLong" @close="emit('close')">
    <div class="picker" @keydown="onKey">
      <div v-if="isLong" class="search">
        <TextField v-model="query" :placeholder="t('common.search')" autofocus @submit="pickFirst" />
      </div>
      <div ref="list" class="scroll">
        <p v-if="!shown.length" class="empty">{{ t('common.noMatches') }}</p>
        <div v-else class="card" role="listbox" :aria-label="title">
          <button
            v-for="o in shown"
            :key="String(o.value)"
            type="button"
            role="option"
            class="option"
            :class="{ selected: o.value === model }"
            :aria-selected="o.value === model"
            :autofocus="!isLong && o.value === model"
            @click="pick(o.value)"
          >
            <span class="label" :dir="o.dir" :lang="o.lang">{{ o.label }}</span>
            <QasaIcon v-if="o.value === model" name="check" :size="16" class="check" />
          </button>
        </div>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.picker {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.search {
  flex-shrink: 0;
  padding: 0 var(--space-screen-padding) var(--space-form-gap);
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.empty {
  margin: 0;
  padding: 24px 0;
  text-align: center;
  font-size: var(--type-body-size);
  color: var(--text-secondary);
}

/* `CardContainer(padding: 0)`: rows run edge to edge, the corners clip their wash. */
.card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.option {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: var(--size-row-min-height);
  margin: 0;
  padding: 0 var(--space-group-divider-inset);
  border: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  text-align: start;
  cursor: pointer;
}

/* The divider: 1px stroke, inset 14 on the leading side, to the trailing edge. */
.option + .option::before {
  content: '';
  position: absolute;
  top: 0;
  inset-inline-start: var(--space-group-divider-inset);
  inset-inline-end: 0;
  border-top: var(--size-stroke) solid var(--stroke);
}

/* iOS `RowButtonStyle`'s wash. */
.option::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--text-primary);
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--motion-press);
}

.option:hover::after,
.option:active::after {
  opacity: var(--motion-row-pressed-opacity);
}

.option:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.label {
  flex: 1;
  min-width: 0;
  font-size: var(--type-row-title-size);
  font-weight: var(--type-row-title-weight);
}

.check {
  flex-shrink: 0;
  color: var(--text-primary);
}
</style>
