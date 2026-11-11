<script setup lang="ts">
// The bar that holds a page's or panel's primary action, pinned to the bottom of it: the
// chrome surface with a hairline above, buttons `--button-height` tall. Put it last in a
// column flex container whose content above scrolls (PageFrame's `actions` slot, AuthFrame,
// the item editor, the extension popup). As iOS `BottomActionBar`, its buttons (and any
// note or link) stack one under another, 10 apart; `stack` is kept for compatibility.
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
  padding: var(--space-bottom-bar-vertical) var(--space-screen-padding);
  border-top: var(--size-stroke) solid var(--stroke);
  background: var(--chrome);
}

/* iOS `BottomActionBar` is a `VStack(spacing: 10)`: buttons one under another, full width. */
.inner {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-bottom-bar-gap);
  width: 100%;
  margin-inline: auto;
}
</style>
