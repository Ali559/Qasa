<script setup lang="ts">
// A shell page or tab root: `AppMenuHeader` over a scrolling column. On desktop the
// column is capped (720px) so cards keep phone proportions instead of stretching. The
// `actions` slot is the page's primary action, pinned under the scroll (BottomActionBar).
import AppMenuHeader from './AppMenuHeader.vue'
import BottomActionBar from './BottomActionBar.vue'

withDefaults(defineProps<{ title: string; back?: boolean; maxWidth?: number; gap?: number }>(), {
  maxWidth: 720,
  gap: 24,
})
defineEmits<{ back: [] }>()
</script>

<template>
  <div class="page">
    <AppMenuHeader :title="title" :back="back" @back="$emit('back')">
      <template #trailing><slot name="header-trailing" /></template>
    </AppMenuHeader>
    <div class="scroll">
      <div class="column" :style="{ maxWidth: `${maxWidth}px`, gap: `${gap}px` }">
        <slot />
      </div>
    </div>
    <BottomActionBar v-if="$slots.actions" :max-width="maxWidth - 40">
      <slot name="actions" />
    </BottomActionBar>
    <slot name="overlay" />
  </div>
</template>

<style scoped>
.page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  background: var(--background);
}

.scroll {
  flex: 1;
  overflow-y: auto;
}

.column {
  display: flex;
  flex-direction: column;
  width: 100%;
  margin-inline: auto;
  padding: 16px var(--screen-padding) 40px;
}
</style>
