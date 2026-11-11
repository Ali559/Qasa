<script setup lang="ts">
// iOS `SheetHeader` (design/README.md`, "Bottom sheets"): the sheet's title, `type.sheetTitle` (17
// semibold), centered, `space.sheetTitleTop` (20) from the sheet's top edge and
// `space.sheetTitleBottom` (16) above the content. No Cancel / Close / Done: a sheet closes
// with Esc or a click outside, and its own action stays at the bottom. `trailing` is for a
// rare extra (Mark all read), 20 from the end edge; the title keeps 44 clear on both sides.
defineProps<{ title: string }>()
</script>

<template>
  <header class="sheet-header">
    <h2 class="title">{{ title }}</h2>
    <span v-if="$slots.trailing" class="trailing"><slot name="trailing" /></span>
  </header>
</template>

<style scoped>
.sheet-header {
  position: relative;
  flex-shrink: 0;
  padding: var(--space-sheet-title-top) calc(var(--space-screen-padding) + 44px) var(--space-sheet-title-bottom);
  background: var(--background);
}

.title {
  margin: 0;
  font-size: var(--type-sheet-title-size);
  font-weight: var(--type-sheet-title-weight);
  line-height: var(--line-height);
  color: var(--text-primary);
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.trailing {
  position: absolute;
  inset-block-start: var(--space-sheet-title-top);
  inset-inline-end: var(--space-screen-padding);
  display: flex;
  align-items: center;
  height: calc(var(--type-sheet-title-size) * var(--line-height));
}
</style>
