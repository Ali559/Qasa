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
  --ink-info: #9aa3ff;
  --ink-warn: #ffc53d;
  --ink-ok: #4be0a8;
  --ink-bad: #ff7a98;
  box-shadow:
    inset 0 1px 0 rgb(255 255 255 / 0.06),
    0 4px 0 #08071a;
}
</style>
