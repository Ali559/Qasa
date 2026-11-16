<script setup lang="ts">
// design/README.md rule 4, "Group": the fields of one form section share one card — card
// fill, 1px stroke, `--radius-group` (20). Rows (`GroupRow` and the rows built on it) go
// in the default slot; the dividers between them are theirs, and so are their messages
// (each under its own value, Phase 4). Groups are `--space-form-gap` (16) apart: the
// parent's gap.
</script>

<template>
  <div class="group-card"><slot /></div>
</template>

<style scoped>
.group-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-group);
  background: var(--card-background);
}
</style>
