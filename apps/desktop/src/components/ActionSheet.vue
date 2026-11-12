<script setup lang="ts">
// iOS `qasaActionSheet` (`OwnSheets.swift`, design/README.md rule 6a): an item's or folder's
// ⋯ menu as a bottom sheet, the shared header (the item's name or the action's title), then
// one `SettingsSection` of `SettingsRow`s, icon badge and label, no chevron, a destructive
// row in danger, last. No Cancel: Esc or a click outside closes it. A pick closes the sheet
// first and runs the action once it has slid away, so a follow-up sheet (the delete
// confirmation) opens cleanly after it.
//
// `v-model:open` drives it from the caller's own button; without it, the default slot is
// the ⋯ trigger (`label` names it) and the sheet keeps its own state.
import { ref } from 'vue'

import Sheet from './Sheet.vue'
import SettingsRow from './SettingsRow.vue'
import SettingsSection from './SettingsSection.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

export interface ActionItem {
  label: string
  icon: QasaIconName
  action: () => void
  destructive?: boolean
}

defineProps<{ title: string; items: ActionItem[]; label?: string }>()
const open = defineModel<boolean>('open', { default: false })
const list = ref<HTMLElement | null>(null)
let pending: (() => void) | null = null

function pick(item: ActionItem) {
  pending = item.action
  open.value = false
}

function onClosed() {
  const run = pending
  pending = null
  run?.()
}

/** Arrow keys move between the rows. */
function onKey(e: KeyboardEvent) {
  if (e.key !== 'ArrowDown' && e.key !== 'ArrowUp') return
  const rows = [...(list.value?.querySelectorAll<HTMLElement>('.settings-row') ?? [])]
  const i = rows.indexOf(document.activeElement as HTMLElement)
  e.preventDefault()
  rows[(i + (e.key === 'ArrowDown' ? 1 : -1) + rows.length) % rows.length]?.focus()
}
</script>

<template>
  <button v-if="$slots.default" type="button" class="trigger" :aria-label="label" :title="label" aria-haspopup="dialog" @click.stop="open = true">
    <slot />
  </button>
  <Sheet :open="open" :label="title" :title="title" :width="420" placement="bottom" @close="open = false" @closed="onClosed">
    <div ref="list" class="actions" @keydown="onKey">
      <SettingsSection>
        <SettingsRow
          v-for="item in items"
          :key="item.label"
          :icon="item.icon"
          :title="item.label"
          :destructive="item.destructive"
          no-chevron
          @click="pick(item)"
        />
      </SettingsSection>
    </div>
  </Sheet>
</template>

<style scoped>
.actions {
  overflow-y: auto;
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
}

/* The ⋯ button: 36, secondary, a muted wash on hover. */
.trigger {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.trigger:hover {
  background: var(--card-muted);
}
</style>
