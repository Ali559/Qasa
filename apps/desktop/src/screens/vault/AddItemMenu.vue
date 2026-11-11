<script setup lang="ts">
// iOS `AddItemMenuView`: on All, the five kinds (Payment, Personal Information and ID
// drill into their subtypes); on a kind's tab, that kind's subtypes directly. The rows are
// a `SettingsSection` card of `SettingsRow`s, 20 from the sheet's edges, under the shared
// sheet header (no Cancel). A drilled page keeps iOS's navigation back button at the start.
import { ChevronLeft, CreditCard, FileText, IdCard, KeyRound, User } from 'lucide-vue-next'
import { computed, ref, watch, type Component } from 'vue'

import SettingsRow from '@/components/SettingsRow.vue'
import Sheet from '@/components/Sheet.vue'
import SheetHeader from '@/components/SheetHeader.vue'
import { VARIANT_ICON } from '@/icons'
import { useT } from '@/prefs'
import { KIND_ADD_KEY, KIND_SUBTYPES, VARIANT_TYPE_KEY } from '@/vault/present'
import type { ItemKind, Variant } from '@/vault/types'

const props = defineProps<{ open: boolean; filter: 'all' | ItemKind }>()
const emit = defineEmits<{ pick: [variant: Variant]; close: [] }>()
const t = useT()

const KINDS: Array<{ kind: ItemKind; icon: Component; variant?: Variant }> = [
  { kind: 'login', icon: KeyRound, variant: 'login' },
  { kind: 'secureNote', icon: FileText, variant: 'secureNote' },
  { kind: 'payment', icon: CreditCard },
  { kind: 'personalInfo', icon: User },
  { kind: 'idDocument', icon: IdCard },
]

const drilled = ref<ItemKind | null>(null)
watch(
  () => props.open,
  () => (drilled.value = null),
)

const showingKind = computed<ItemKind | null>(() => (props.filter === 'all' ? drilled.value : props.filter))
const title = computed(() => (drilled.value ? t(KIND_ADD_KEY[drilled.value]) : t('vault.addNew')))

const rows = computed(() => {
  const kind = showingKind.value
  if (!kind) return null
  if (kind === 'login' || kind === 'secureNote') return [{ variant: kind as Variant, label: t(VARIANT_TYPE_KEY[kind as Variant]) }]
  return (KIND_SUBTYPES[kind] ?? []).map((s) => ({ variant: s.variant, label: t(s.key) }))
})
</script>

<template>
  <Sheet :open="open" :label="title" :width="460" placement="bottom" @close="emit('close')">
    <div class="head">
      <SheetHeader :title="title" />
      <button v-if="drilled" type="button" class="back" :aria-label="t('common.back')" @click="drilled = null">
        <ChevronLeft class="mirror-rtl" :stroke-width="2.75" />
      </button>
    </div>
    <div class="list">
      <div class="card settings-list">
        <template v-if="rows">
          <SettingsRow
            v-for="row in rows"
            :key="row.variant"
            class="no-chevron"
            :icon="VARIANT_ICON[row.variant]"
            :title="row.label"
            @click="emit('pick', row.variant)"
          />
        </template>
        <template v-else>
          <SettingsRow
            v-for="k in KINDS"
            :key="k.kind"
            :icon="k.icon"
            :title="t(KIND_ADD_KEY[k.kind])"
            :class="{ 'no-chevron': !!k.variant }"
            @click="k.variant ? emit('pick', k.variant) : (drilled = k.kind)"
          />
        </template>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.head {
  position: relative;
}

/* iOS navigation back chevron: 17 semibold accent, at the start, on the title's line. */
.back {
  position: absolute;
  inset-block-start: calc(var(--space-sheet-title-top) - (44px - var(--type-sheet-title-size) * var(--line-height)) / 2);
  inset-inline-start: var(--space-screen-padding);
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-inline-start: -12px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.back svg {
  width: 17px;
  height: 17px;
}

.list {
  overflow-y: auto;
  padding: 0 var(--space-screen-padding) var(--space-screen-padding);
}

.card {
  display: flex;
  flex-direction: column;
  padding: var(--card-padding);
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

/* Rows that open the editor straight away have no chevron (iOS `row(chevron: false)`). */
.no-chevron :deep(.chevron) {
  display: none;
}
</style>
