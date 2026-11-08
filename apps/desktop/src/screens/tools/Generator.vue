<script setup lang="ts">
// iOS `GeneratorView`: the full-page generator. "Save to my passwords" is a no-op on iOS
// (design.md §12.12); here it opens a new login with the password filled in.
import { ref } from 'vue'

import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { nav, selectTab } from '@/nav'
import { useT } from '@/prefs'
import GeneratorContent from './GeneratorContent.vue'

defineEmits<{ back: [] }>()
const t = useT()
const password = ref('')

function saveToVault() {
  nav.pendingPassword = password.value
  selectTab('passwords')
}
</script>

<template>
  <PageFrame :title="t('nav.generator')" back @back="$emit('back')">
    <GeneratorContent v-model="password" :compact="false" />
    <PrimaryButton class="save" :title="t('gen.saveToVault')" :disabled="!password" @click="saveToVault" />
  </PageFrame>
</template>

<style scoped>
.save {
  margin-top: 16px;
}
</style>
