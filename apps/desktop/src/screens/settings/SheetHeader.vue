<script setup lang="ts">
// The inline navigation bar of an iOS settings sheet (`.navigationBarTitleDisplayMode(.inline)`):
// a centered title with a cancellation action at the start and a confirmation action at the end.
defineProps<{ title: string; cancelLabel?: string; doneLabel?: string }>()
const emit = defineEmits<{ cancel: []; done: [] }>()
</script>

<template>
  <header class="sheet-header">
    <span class="side start">
      <button v-if="cancelLabel" type="button" class="action" @click="emit('cancel')">{{ cancelLabel }}</button>
    </span>
    <h2 class="title">{{ title }}</h2>
    <span class="side end">
      <button v-if="doneLabel" type="button" class="action done" @click="emit('done')">{{ doneLabel }}</button>
    </span>
  </header>
</template>

<style scoped>
.sheet-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  padding-inline: 12px;
  border-bottom: 1px solid var(--stroke-chrome);
  flex-shrink: 0;
}

.side {
  display: flex;
}

.side.end {
  justify-content: flex-end;
}

.title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.action {
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--accent);
  font-size: 16px;
  cursor: pointer;
}

.action:hover {
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.action.done {
  font-weight: 600;
}
</style>
