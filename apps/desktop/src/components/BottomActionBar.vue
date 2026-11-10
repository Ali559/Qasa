<script setup lang="ts">
// The bar that holds a page's or panel's primary action, pinned to the bottom of it: the
// chrome surface with a hairline above, buttons `--button-height` tall. Put it last in a
// column flex container whose content above scrolls (PageFrame's `actions` slot, AuthFrame,
// the item editor, the extension popup). Several buttons share the width evenly; `stack`
// puts them (and any note or link) one under another instead.
defineProps<{ maxWidth?: number; stack?: boolean }>()
</script>

<template>
  <div class="bottom-action-bar">
    <div class="inner" :class="{ stack }" :style="maxWidth ? { maxWidth: `${maxWidth}px` } : undefined">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.bottom-action-bar {
  flex-shrink: 0;
  padding: var(--action-bar-padding) var(--screen-padding);
  border-top: 1px solid var(--stroke);
  background: var(--chrome);
}

.inner {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  margin-inline: auto;
}

.inner:not(.stack) > :deep(*) {
  flex: 1;
  min-width: 0;
}

.inner.stack {
  flex-direction: column;
  align-items: stretch;
}
</style>
