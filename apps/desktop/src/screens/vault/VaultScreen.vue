<script setup lang="ts">
// iOS `VaultListView` on desktop: list on the left, the selected item's detail on the
// right (above 900px; below that the detail replaces the list with a back button).
// Search, filter chips, Most Recent (filtered tab with over 10 items), letter sections,
// the + button and the tab-aware add flow match iOS. The Suggested section is left out:
// on iOS it comes from a mock context provider; on desktop the extension will supply it.
import { CircleX, Inbox, Plus, Search } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import AppMenuHeader from '@/components/AppMenuHeader.vue'
import Fab from '@/components/Fab.vue'
import FilterChip from '@/components/FilterChip.vue'
import { nav } from '@/nav'
import { useT } from '@/prefs'
import { KIND_TAB_KEY, letterSection } from '@/vault/present'
import { session } from '@/vault/session'
import type { ItemKind, ItemSummary, Variant } from '@/vault/types'
import AddItemMenu from './AddItemMenu.vue'
import ItemDetail from './ItemDetail.vue'
import ItemEditor from './ItemEditor.vue'
import ProtectedGateDialog from './ProtectedGateDialog.vue'
import VaultRow from './VaultRow.vue'

const t = useT()
type Filter = 'all' | ItemKind
const FILTERS: Filter[] = ['all', 'login', 'secureNote', 'payment', 'personalInfo', 'idDocument']

const filter = ref<Filter>('all')
const query = ref('')
const searchInput = ref<HTMLInputElement | null>(null)

const showAddMenu = ref(false)
const editor = ref<{ id?: string; variant?: Variant; password?: string } | null>(null)

// Generator → "Save to my passwords" opens a new login with that password.
watch(
  () => nav.pendingPassword,
  (password) => {
    if (!password) return
    editor.value = { variant: 'login', password }
    nav.pendingPassword = null
  },
  { immediate: true },
)

const filtered = computed(() => {
  const q = query.value.trim().toLocaleLowerCase()
  return session.items.filter(
    (r) =>
      (filter.value === 'all' || r.kind === filter.value) &&
      (!q || r.displayName.toLocaleLowerCase().includes(q) || r.subtitle.toLocaleLowerCase().includes(q)),
  )
})

const recent = computed<ItemSummary[]>(() =>
  filter.value === 'all' || filtered.value.length <= 10
    ? []
    : [...filtered.value].sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 8),
)

const sections = computed(() => {
  const groups = new Map<string, ItemSummary[]>()
  for (const row of filtered.value) {
    const key = letterSection(row.displayName)
    groups.set(key, [...(groups.get(key) ?? []), row])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === '#' ? 1 : b === '#' ? -1 : a.localeCompare(b)))
    .map(([letter, items]) => ({ letter, items }))
})

const selected = computed(() => session.items.find((i) => i.id === nav.selectedItemId) ?? null)

/** iOS `handleAddTapped`: Logins and Notes skip the type menu. */
function add() {
  if (filter.value === 'login') editor.value = { variant: 'login' }
  else if (filter.value === 'secureNote') editor.value = { variant: 'secureNote' }
  else showAddMenu.value = true
}

function picked(variant: Variant) {
  showAddMenu.value = false
  editor.value = { variant }
}

function saved(row: ItemSummary) {
  editor.value = null
  nav.selectedItemId = row.id
}

// ⌘/Ctrl+F search, ⌘/Ctrl+N new item (plan §5.2).
function onKey(e: KeyboardEvent) {
  if (!(e.metaKey || e.ctrlKey) || e.altKey) return
  const key = e.key.toLowerCase()
  if (key === 'f') {
    e.preventDefault()
    searchInput.value?.focus()
  } else if (key === 'n') {
    e.preventDefault()
    add()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="vault" :class="{ 'has-selection': !!selected }">
    <section class="list-pane">
      <AppMenuHeader :title="t('vault.titleYours')" />
      <div class="scroll">
        <div class="list">
          <label class="qasa-field search" :class="{ 'has-clear': !!query }">
            <Search class="search-icon" :stroke-width="2.5" aria-hidden="true" />
            <input ref="searchInput" v-model="query" type="search" :placeholder="t('vault.searchPlaceholder')" :aria-label="t('vault.search')" />
            <button v-if="query" type="button" class="clear" :aria-label="t('common.cancel')" @click.prevent="query = ''">
              <CircleX :stroke-width="2.5" />
            </button>
          </label>

          <div class="chips" role="toolbar">
            <FilterChip
              v-for="f in FILTERS"
              :key="f"
              :title="t(f === 'all' ? 'vault.tab.all' : KIND_TAB_KEY[f])"
              :selected="filter === f"
              @click="filter = f"
            />
          </div>

          <template v-if="recent.length">
            <h2 class="section">{{ t('vault.mostRecent') }}</h2>
            <VaultRow
              v-for="row in recent"
              :key="`recent-${row.id}`"
              :row="row"
              :selected="row.id === nav.selectedItemId"
              @select="nav.selectedItemId = row.id"
              @edit="editor = { id: row.id }"
              @deleted="nav.selectedItemId = null"
            />
          </template>

          <template v-for="section in sections" :key="section.letter">
            <h2 class="section">{{ section.letter }}</h2>
            <VaultRow
              v-for="row in section.items"
              :key="row.id"
              :row="row"
              :selected="row.id === nav.selectedItemId"
              @select="nav.selectedItemId = row.id"
              @edit="editor = { id: row.id }"
              @deleted="nav.selectedItemId = null"
            />
          </template>

          <div v-if="filtered.length === 0" class="empty">
            <Inbox :size="40" aria-hidden="true" />
            <strong>{{ t('vault.emptyTitle') }}</strong>
            <span>{{ query.trim() ? t('vault.noSearchMatches') : t('vault.emptyHint') }}</span>
          </div>
        </div>
      </div>
      <div class="fab">
        <Fab :icon="Plus" :label="t('a11y.addItem')" @click="add" />
      </div>
    </section>

    <section class="detail-pane">
      <div v-if="selected" class="narrow-back">
        <AppMenuHeader :title="selected.displayName" back @back="nav.selectedItemId = null" />
      </div>
      <ItemDetail
        v-if="selected"
        :key="selected.id"
        :item-id="selected.id"
        @edit="editor = { id: selected.id }"
        @deleted="nav.selectedItemId = null"
      />
      <div v-else class="placeholder">
        <span>{{ t('vault.selectItem') }}</span>
      </div>
    </section>

    <AddItemMenu :open="showAddMenu" :filter="filter" @pick="picked" @close="showAddMenu = false" />
    <ItemEditor
      v-if="editor"
      :item-id="editor.id"
      :variant="editor.variant"
      :password="editor.password"
      @saved="saved"
      @close="editor = null"
    />
    <ProtectedGateDialog />
  </div>
</template>

<style scoped>
.vault {
  display: flex;
  height: 100%;
}

.list-pane {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 400px;
  flex-shrink: 0;
  border-inline-end: var(--size-stroke) solid var(--stroke);
  background: var(--background);
}

.detail-pane {
  flex: 1;
  min-width: 0;
}

.narrow-back {
  display: none;
}

.scroll {
  flex: 1;
  overflow-y: auto;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px var(--space-screen-padding) 100px;
}

.search {
  flex-shrink: 0;
  gap: 10px;
}

.search.has-clear {
  padding-inline-end: calc(var(--space-field-horizontal) - (var(--size-field-icon-button) - var(--size-field-icon-glyph)) / 2);
}

.search-icon {
  flex-shrink: 0;
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
  color: var(--text-secondary);
}

/* iOS `FieldIconButton("xmark.circle.fill")`, tertiary. */
.clear {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
}

.clear svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.search input::-webkit-search-cancel-button {
  appearance: none;
}

.search input {
  flex: 1;
  min-width: 0;
  height: 100%;
  margin: 0;
  padding: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  caret-color: var(--accent);
  user-select: text;
}

.search input::-webkit-search-decoration {
  appearance: none;
}

.search input::placeholder {
  color: var(--text-placeholder);
}

.chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.section {
  margin: 8px 0 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary);
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 40px;
  color: var(--text-secondary);
  text-align: center;
}

.empty strong {
  color: var(--text-primary);
  font-size: 17px;
}

.fab {
  position: absolute;
  bottom: 24px;
  inset-inline-end: var(--space-screen-padding);
}

.placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  color: var(--text-tertiary);
  font-size: var(--type-body-size);
}

/* Narrow windows: one pane at a time, like the phone. */
@media (max-width: 899px) {
  .list-pane {
    width: 100%;
    border: 0;
  }

  .detail-pane {
    display: none;
  }

  .vault.has-selection .list-pane {
    display: none;
  }

  .vault.has-selection .detail-pane {
    display: flex;
    flex-direction: column;
  }

  .narrow-back {
    display: block;
  }

  .narrow-back + :deep(.detail) .bar {
    position: absolute;
    top: 0;
    inset-inline-end: 0;
  }
}
</style>
