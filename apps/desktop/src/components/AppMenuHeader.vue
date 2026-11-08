<script setup lang="ts">
// iOS `AppMenuHeader`: 64pt, chrome surface, 20 bold title, optional 40pt back circle
// whose chevron flips in RTL, trailing slot.
import { ChevronLeft } from 'lucide-vue-next'

import { useT } from '@/prefs'

defineProps<{ title: string; back?: boolean }>()
const emit = defineEmits<{ back: [] }>()
const t = useT()
</script>

<template>
  <header class="menu-header">
    <button v-if="back" type="button" class="back" :aria-label="t('common.back')" @click="emit('back')">
      <ChevronLeft class="mirror-rtl" :size="20" :stroke-width="2.5" />
    </button>
    <h1 class="title">{{ title }}</h1>
    <span class="spacer" />
    <slot name="trailing" />
  </header>
</template>

<style scoped>
.menu-header {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding-inline: 16px;
  background: var(--chrome);
  flex-shrink: 0;
}

.back {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 0;
  border-radius: 50%;
  background: var(--card-background);
  color: var(--text-primary);
  cursor: pointer;
}

.back:hover {
  background: var(--card-muted);
}

.title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.spacer {
  flex: 1;
  min-width: 8px;
}
</style>
