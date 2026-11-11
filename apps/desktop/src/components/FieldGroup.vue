<script setup lang="ts">
// design/README.md rule 4, "Group": the fields of one form section share one card — card
// fill, 1px stroke, `--radius-group` (20). Rows (`GroupRow` and the rows built on it) go
// in the default slot; the dividers between them are theirs. The rows' messages sit
// `--space-field-error-gap` (6) under the card, 11 danger, prefixed with the row's label
// when the group has more than one row. Groups are `--space-form-gap` (16) apart: the
// parent's gap.
import { computed, provide, shallowReactive } from 'vue'

import { FIELD_GROUP, errorIdFor, type GroupRowEntry } from './fieldGroup'

const rows = shallowReactive(new Map<string, GroupRowEntry>())

provide(FIELD_GROUP, {
  register: (entry) => rows.set(entry.id, entry),
  unregister: (id) => rows.delete(id),
})

/** In the order the rows are drawn (a row behind `v-if` may register late). */
const messages = computed(() => {
  const many = rows.size > 1
  return [...rows.values()]
    .map((row) => ({ row, error: row.error() }))
    .filter((m): m is { row: GroupRowEntry; error: string } => !!m.error)
    .sort((a, b) => {
      const ea = a.row.el()
      const eb = b.row.el()
      if (!ea || !eb) return 0
      return ea.compareDocumentPosition(eb) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1
    })
    .map(({ row, error }) => ({ id: errorIdFor(row.id), text: many ? `${row.label()}: ${error}` : error }))
})
</script>

<template>
  <div class="field-group">
    <div class="group-card"><slot /></div>
    <div v-if="messages.length" class="group-errors">
      <span v-for="m in messages" :id="m.id" :key="m.id" class="field-error" role="alert">{{ m.text }}</span>
    </div>
  </div>
</template>

<style scoped>
.field-group {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-error-gap);
  min-width: 0;
}

.group-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-group);
  background: var(--card-background);
}

.group-errors {
  display: flex;
  flex-direction: column;
}

/* iOS `InlineFieldError`: 11 regular danger. */
.field-error {
  font-size: var(--type-field-error-size);
  font-weight: var(--type-field-error-weight);
  line-height: var(--line-height);
  color: var(--danger);
}
</style>
