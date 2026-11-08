<script setup lang="ts">
// iOS `PasswordGeneratorModal`: the generator in a sheet with "Use Password" pinned below.
import { ref } from 'vue'

import Sheet from '@/components/Sheet.vue'
import { useT } from '@/prefs'
import GeneratorContent from '../tools/GeneratorContent.vue'

defineProps<{ open: boolean }>()
const emit = defineEmits<{ use: [password: string]; close: [] }>()
const t = useT()
const password = ref('')
</script>

<template>
  <Sheet :open="open" :label="t('gen.title')" :width="480" @close="emit('close')">
    <div class="scroll">
      <GeneratorContent v-if="open" v-model="password" />
    </div>
    <footer class="bar">
      <button type="button" class="use" :disabled="!password" @click="emit('use', password)">{{ t('gen.usePassword') }}</button>
    </footer>
  </Sheet>
</template>

<style scoped>
.scroll {
  overflow-y: auto;
  padding: 20px var(--screen-padding) 12px;
  background: var(--background);
}

.bar {
  padding: 12px var(--screen-padding) 20px;
  border-top: 1px solid var(--stroke);
  background: var(--chrome);
}

.use {
  width: 100%;
  padding: 16px;
  border: 0;
  border-radius: var(--corner-radius);
  background: var(--accent);
  color: var(--on-accent);
  font-size: 17px;
  font-weight: 600;
  cursor: pointer;
}

.use:disabled {
  opacity: 0.45;
}
</style>
