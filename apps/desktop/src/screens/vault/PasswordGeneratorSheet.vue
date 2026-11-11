<script setup lang="ts">
// iOS `PasswordGeneratorModal`: the shared sheet header ("Password generator", no Cancel),
// the generator 8 below it with 20 at the sides, and "Use Password" in a `BottomActionBar`.
import { ref } from 'vue'

import BottomActionBar from '@/components/BottomActionBar.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import { useT } from '@/prefs'
import GeneratorContent from '../tools/GeneratorContent.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ use: [password: string]; close: [] }>()
const t = useT()
const password = ref('')
</script>

<template>
  <Sheet :open="open" :label="t('gen.title')" :title="t('gen.title')" :width="480" placement="bottom" @close="emit('close')">
    <div class="scroll">
      <GeneratorContent v-if="open" v-model="password" />
    </div>
    <BottomActionBar>
      <PrimaryButton :title="t('gen.usePassword')" :disabled="!password" @click="emit('use', password)" />
    </BottomActionBar>
  </Sheet>
</template>

<style scoped>
/* iOS: padding 20 at the sides, 8 on top (under the header), 12 at the bottom. */
.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px var(--space-screen-padding) var(--space-bottom-bar-vertical);
  background: var(--background);
}
</style>
