<script setup lang="ts">
// iOS `CollectionsView` + `CollectionDetailView` ("Folders"): folder cards with an item
// count and an Edit / Delete menu, + to create, and each folder's items one level in.
// Deleting a folder moves its items out of it (they are never deleted).
import { EllipsisVertical, Folder, Inbox, Plus } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import Fab from '@/components/Fab.vue'
import Menu from '@/components/Menu.vue'
import PageFrame from '@/components/PageFrame.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { nav, selectTab } from '@/nav'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { loadVault, reloadCollections, session, sortByName } from '@/vault/session'
import type { Collection } from '@/vault/types'
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
      <span class="icon"><Folder :size="18" fill="currentColor" /></span>
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
        <EllipsisVertical :size="18" />
      </Menu>
    </div>

    <template #overlay>
      <div class="fab"><Fab :icon="Plus" :label="t('collections.createTitle')" @click="startCreate" /></div>
      <Sheet :open="!!nameSheet" :label="t('collections.createTitle')" :width="400" @close="nameSheet = null">
        <div class="name-sheet">
          <h2>{{ nameSheet?.mode === 'rename' ? t('collections.rename') : t('collections.createTitle') }}</h2>
          <TextField v-model="name" :placeholder="t('common.name')" autofocus @submit="submitName" />
          <div class="actions">
            <button type="button" @click="nameSheet = null">{{ t('common.cancel') }}</button>
            <button type="button" class="primary" :disabled="!name.trim()" @click="submitName">
              {{ nameSheet?.mode === 'rename' ? t('common.save') : t('common.create') }}
            </button>
          </div>
        </div>
      </Sheet>
      <ConfirmDialog
        :open="!!deleting"
        :title="t('collections.deleteTitle')"
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
.folder {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius);
  background: var(--card-background);
  cursor: pointer;
}

.folder:hover {
  border-color: color-mix(in srgb, var(--accent) 35%, var(--stroke));
}

.icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border: 1px solid var(--stroke-chrome);
  border-radius: 10px;
  background: var(--chrome);
  color: var(--accent);
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
  font-size: 13px;
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
  font-size: 17px;
}

.fab {
  position: absolute;
  bottom: 24px;
  inset-inline-end: var(--screen-padding);
}

.name-sheet {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.name-sheet h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.actions {
  display: flex;
  gap: 10px;
}

.actions button {
  flex: 1;
  padding: 12px;
  border: 1px solid var(--stroke);
  border-radius: 14px;
  background: var(--card-background);
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.actions .primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--on-accent);
}

.actions .primary:disabled {
  opacity: 0.45;
}
</style>
