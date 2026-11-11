<script setup lang="ts">
// iOS `CollectionsView` + `CollectionDetailView` ("Folders"): folder cards with an item
// count and an Edit / Delete menu, + to create, and each folder's items one level in.
// Deleting a folder moves its items out of it (they are never deleted).
import { EllipsisVertical, Folder, Inbox, Plus, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Fab from '@/components/Fab.vue'
import Menu from '@/components/Menu.vue'
import PageFrame from '@/components/PageFrame.vue'
import { nav, selectTab } from '@/nav'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { loadVault, reloadCollections, session, sortByName } from '@/vault/session'
import type { Collection } from '@/vault/types'
import PromptSheet from '../vault/form/PromptSheet.vue'
import VaultRow from '../vault/VaultRow.vue'

defineEmits<{ back: [] }>()
const t = useT()

const open = ref<Collection | null>(null)
const nameSheet = ref<{ mode: 'create' | 'rename'; target?: Collection } | null>(null)
const name = ref('')
const deleting = ref<Collection | null>(null)

onMounted(reloadCollections)

const sorted = computed(() => [...session.collections].sort((a, b) => a.name.localeCompare(b.name)))
const items = computed(() => (open.value ? sortByName(session.items.filter((i) => i.collectionId === open.value!.id)) : []))

function countLabel(c: Collection) {
  const n = session.items.filter((i) => i.collectionId === c.id).length
  return t(n === 1 ? 'collections.itemCountOne' : 'collections.itemCount', n)
}

function startCreate() {
  name.value = ''
  nameSheet.value = { mode: 'create' }
}

function startRename(c: Collection) {
  name.value = c.name
  nameSheet.value = { mode: 'rename', target: c }
}

async function submitName() {
  const trimmed = name.value.trim()
  if (!trimmed || !nameSheet.value) return
  if (nameSheet.value.mode === 'create') await vaultApi.createCollection(trimmed)
  else if (nameSheet.value.target) await vaultApi.renameCollection(nameSheet.value.target.id, trimmed)
  nameSheet.value = null
  await reloadCollections()
}

async function confirmDelete() {
  if (!deleting.value) return
  await vaultApi.deleteCollection(deleting.value.id)
  deleting.value = null
  await loadVault()
}

function openItem(id: string) {
  nav.selectedItemId = id
  selectTab('passwords')
}
</script>

<template>
  <PageFrame v-if="open" :title="open.name" back :gap="12" @back="open = null">
    <div v-if="items.length === 0" class="empty">
      <Inbox :size="40" />
      <strong>{{ t('collections.emptyCollectionTitle') }}</strong>
      <span>{{ t('collections.assignHint', open.name) }}</span>
    </div>
    <VaultRow v-for="row in items" :key="row.id" :row="row" :selected="false" @select="openItem(row.id)" @edit="openItem(row.id)" />
  </PageFrame>

  <PageFrame v-else :title="t('nav.collections')" back :gap="12" @back="$emit('back')">
    <div v-if="sorted.length === 0" class="empty">
      <Folder :size="40" />
      <strong>{{ t('collections.noneTitle') }}</strong>
      <span>{{ t('collections.empty') }}</span>
    </div>
    <div v-for="c in sorted" :key="c.id" class="folder" role="button" tabindex="0" @click="open = c" @keydown.enter.self="open = c">
      <span class="icon" aria-hidden="true"><Folder fill="currentColor" :stroke-width="2.5" /></span>
      <span class="text">
        <span class="name">{{ c.name }}</span>
        <span class="count">{{ countLabel(c) }}</span>
      </span>
      <Menu
        :label="t('a11y.moreActions')"
        :items="[
          { label: t('common.edit'), action: () => startRename(c) },
          { label: t('common.delete'), destructive: true, action: () => (deleting = c) },
        ]"
      >
        <EllipsisVertical :size="17" />
      </Menu>
    </div>

    <template #overlay>
      <div class="fab"><Fab :icon="Plus" :label="t('collections.createTitle')" @click="startCreate" /></div>
      <PromptSheet
        v-model="name"
        :open="!!nameSheet"
        :title="nameSheet?.mode === 'rename' ? t('collections.rename') : t('collections.createTitle')"
        :placeholder="t('common.name')"
        :confirm-label="nameSheet?.mode === 'rename' ? t('common.save') : t('common.create')"
        :cancel-label="t('common.cancel')"
        @confirm="submitName"
        @cancel="nameSheet = null"
      />
      <ConfirmDialog
        :open="!!deleting"
        :title="t('collections.deleteTitle')"
        :icon="Trash2"
        :message="t('collections.deleteMessage')"
        :confirm-label="t('common.delete')"
        destructive
        @confirm="confirmDelete"
        @cancel="deleting = null"
      />
    </template>
  </PageFrame>
</template>

<style scoped>
/* iOS `collectionCard`: an `IconBadge` 14 from 16 semibold name and 13 count 4 apart; padding
   16 × 10, at least 60 tall, card fill, radius 20, 1px stroke. Cards are 12 apart. */
.folder {
  display: flex;
  align-items: center;
  gap: var(--space-row-icon-gap);
  min-height: var(--size-row-min-height);
  padding: 10px 16px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card);
  background: var(--card-background);
  cursor: pointer;
}

.folder:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--stroke));
}

.icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
}

.icon svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.name {
  font-size: 16px;
  font-weight: 600;
}

.count {
  font-size: var(--type-row-value-size);
  color: var(--text-secondary);
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding-top: 48px;
  color: var(--text-secondary);
  text-align: center;
}

.empty strong {
  color: var(--text-primary);
  font-size: var(--type-sheet-title-size);
  font-weight: 700;
}

.fab {
  position: absolute;
  bottom: 24px;
  inset-inline-end: var(--space-screen-padding);
}
</style>
