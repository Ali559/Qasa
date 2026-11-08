<script setup lang="ts">
// iOS `CollectionPickerField`: None or a folder, plus a button to make a new one.
import { FolderPlus } from 'lucide-vue-next'
import { computed, ref } from 'vue'

import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { reloadCollections, session } from '@/vault/session'
import SelectField from './SelectField.vue'

const model = defineModel<string | undefined>()
const t = useT()
const showNew = ref(false)
const newName = ref('')

const selected = computed<string | null>({
  get: () => model.value ?? null,
  set: (v) => (model.value = v ?? undefined),
})

const options = computed(() => [
  { value: null, label: t('common.none') },
  ...session.collections.map((c) => ({ value: c.id as string | null, label: c.name })),
])

async function create() {
  const name = newName.value.trim()
  if (!name) return
  const c = await vaultApi.createCollection(name)
  await reloadCollections()
  model.value = c.id
  newName.value = ''
  showNew.value = false
}
</script>

<template>
  <SelectField v-model="selected" :label="t('vault.collection')" :options="options">
    <template #trailing>
      <button type="button" class="new" :aria-label="t('vault.newCollection')" :title="t('vault.newCollection')" @click="showNew = true">
        <FolderPlus :size="18" />
      </button>
    </template>
  </SelectField>
  <Sheet :open="showNew" :label="t('vault.newCollection')" :width="400" @close="showNew = false">
    <div class="new-sheet">
      <h2>{{ t('vault.newCollection') }}</h2>
      <TextField v-model="newName" :placeholder="t('common.name')" autofocus @submit="create" />
      <div class="actions">
        <button type="button" @click="showNew = false">{{ t('common.cancel') }}</button>
        <button type="button" class="primary" :disabled="!newName.trim()" @click="create">{{ t('common.create') }}</button>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.new {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.new-sheet {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

h2 {
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
