<script setup lang="ts">
// Always-dark "terminal" surface for logs and raw payloads, in both themes.
</script>

<template>
  <div class="ink bg-sidebar text-sidebar-foreground overflow-hidden rounded-2xl">
    <slot />
  </div>
</template>

<style scoped>
.ink {
  /* Dark-scheme values from apps/desktop/src/theme/tokens.css, read with dark --ink text. */
  --ink-info: #C9A3E6; /* info */
  --ink-warn: #F0C48C; /* gold-muted */
  --ink-ok: #5CCB97; /* success */
  --ink-bad: #FF6B6B; /* danger */
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.06),
    0 4px 0 var(--dock-lip);
}
</style>
