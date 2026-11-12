<script setup lang="ts">
// iOS `CollectionPickerField` as a one-row `FieldGroup` (design/README.md rule 4): a picker
// row (None or a folder) with an accent `FieldIconButton` beside the chevron that opens the
// New collection `textPromptSheet`.
import { computed, ref } from 'vue'

import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { reloadCollections, session } from '@/vault/session'
import PromptSheet from './PromptSheet.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldIconButton from '@/components/FieldIconButton.vue'
import PickerRow from '@/components/PickerRow.vue'

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
  <FieldGroup>
    <PickerRow v-model="selected" :label="t('vault.collection')" :options="options">
      <template #trailing>
        <FieldIconButton icon="folder-add" :label="t('vault.newCollection')" accent @click="showNew = true" />
      </template>
    </PickerRow>
  </FieldGroup>
  <PromptSheet
    v-model="newName"
    :open="showNew"
    :title="t('vault.newCollection')"
    :placeholder="t('common.name')"
    :confirm-label="t('common.create')"
    @confirm="create"
    @cancel="showNew = false"
  />
</template>
