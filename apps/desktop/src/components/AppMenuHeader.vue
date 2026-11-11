<script setup lang="ts">
// iOS `AppMenuHeader`: 64pt, chrome surface, 20 bold title, optional 40pt back circle
// whose chevron flips in RTL, trailing slot.

import QasaIcon from '@/components/QasaIcon.vue'
import { useT } from '@/prefs'

defineProps<{ title: string; back?: boolean }>()
const emit = defineEmits<{ back: [] }>()
const t = useT()
</script>

<template>
  <header class="menu-header">
    <button v-if="back" type="button" class="back" :aria-label="t('common.back')" @click="emit('back')">
      <QasaIcon name="chevron-back" :size="17" />
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
  height: var(--size-header-height);
  padding-inline: var(--space-header-horizontal);
  background: var(--chrome);
  flex-shrink: 0;
}

/* iOS `HeaderBackButton`: a 40pt card-filled circle, 17 semibold chevron. */
.back {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: var(--card-background);
  color: var(--text-primary);
  cursor: pointer;
}


.title {
  margin: 0;
  font-size: var(--type-page-title-size);
  font-weight: var(--type-page-title-weight);
  line-height: var(--line-height);
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
