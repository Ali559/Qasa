<script setup lang="ts">
// iOS `CollectionPickerField`: None or a folder, plus a `FieldIconButton` (accent) that opens
// the New collection `textPromptSheet`.
import { FolderPlus } from 'lucide-vue-next'
import { computed, ref } from 'vue'

import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { reloadCollections, session } from '@/vault/session'
import PromptSheet from './PromptSheet.vue'
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
        <FolderPlus :stroke-width="2.5" />
      </button>
    </template>
  </SelectField>
  <PromptSheet
    v-model="newName"
    :open="showNew"
    :title="t('vault.newCollection')"
    :placeholder="t('common.name')"
    :confirm-label="t('common.create')"
    :cancel-label="t('common.cancel')"
    @confirm="create"
    @cancel="showNew = false"
  />
</template>

<style scoped>
/* iOS `FieldIconButton`: 32 square, 16 semibold glyph. */
.new {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.new svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}
</style>
