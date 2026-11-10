<script setup lang="ts">
// iOS `AddItemMenuView`: on All, the five kinds (Payment, Personal Information and ID
// drill into their subtypes); on a kind's tab, that kind's subtypes directly.
import { ChevronLeft, ChevronRight, CreditCard, FileText, IdCard, KeyRound, User } from 'lucide-vue-next'
import { computed, ref, watch, type Component } from 'vue'

import Sheet from '@/components/Sheet.vue'
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
    <header class="sheet-head">
      <button v-if="drilled" type="button" class="nav" :aria-label="t('common.back')" @click="drilled = null">
        <ChevronLeft class="mirror-rtl" :size="20" />
      </button>
      <h2>{{ title }}</h2>
      <button type="button" class="nav text" @click="emit('close')">{{ t('common.cancel') }}</button>
    </header>
    <div class="list">
      <template v-if="rows">
        <button v-for="row in rows" :key="row.variant" type="button" class="row" @click="emit('pick', row.variant)">
          <span>{{ row.label }}</span>
        </button>
      </template>
      <template v-else>
        <button
          v-for="k in KINDS"
          :key="k.kind"
          type="button"
          class="row"
          @click="k.variant ? emit('pick', k.variant) : (drilled = k.kind)"
        >
          <component :is="k.icon" :size="18" class="icon" />
          <span>{{ t(KIND_ADD_KEY[k.kind]) }}</span>
          <ChevronRight v-if="!k.variant" class="chevron mirror-rtl" :size="16" />
        </button>
      </template>
    </div>
  </Sheet>
</template>

<style scoped>
.sheet-head {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  height: 56px;
  padding-inline: 12px;
  border-bottom: 1px solid var(--stroke);
}

.sheet-head h2 {
  margin: 0;
  grid-column: 2;
  font-size: 17px;
  font-weight: 600;
}

.nav {
  grid-column: 1;
  justify-self: start;
  display: grid;
  place-items: center;
  height: 36px;
  min-width: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.nav.text {
  grid-column: 3;
  justify-self: end;
  padding-inline: 8px;
  font-size: 16px;
}

.list {
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 12px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--text-primary);
  font-size: 16px;
  text-align: start;
  cursor: pointer;
}

.row:hover,
.row:focus-visible {
  background: var(--card-muted);
}

.icon {
  color: var(--accent);
}

.chevron {
  margin-inline-start: auto;
  color: var(--text-tertiary);
}
</style>
