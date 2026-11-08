<script setup lang="ts">
// The iOS `.alert` where the cancel button has its own label ("Keep Premium").
// `ConfirmDialog` always says "Cancel"; this is the same dialog with that label open.
import Sheet from '@/components/Sheet.vue'

defineProps<{ open: boolean; title: string; message?: string; cancelLabel: string; confirmLabel: string; destructive?: boolean }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <Sheet :open="open" :label="title" :width="400" @close="emit('cancel')">
    <div class="alert">
      <h2>{{ title }}</h2>
      <p v-if="message">{{ message }}</p>
      <div class="actions">
        <button type="button" class="cancel" @click="emit('cancel')">{{ cancelLabel }}</button>
        <button type="button" class="confirm" :class="{ destructive }" @click="emit('confirm')">{{ confirmLabel }}</button>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.alert {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px;
  text-align: center;
}

h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
}

p {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}

.actions button {
  flex: 1;
  padding: 12px;
  border: 1px solid var(--stroke);
  border-radius: 14px;
  background: var(--card-background);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.cancel {
  color: var(--text-primary);
}

.confirm {
  border-color: var(--accent) !important;
  background: var(--accent) !important;
  color: var(--on-accent);
}

.confirm.destructive {
  border-color: var(--danger) !important;
  background: var(--danger) !important;
  color: #fff;
}
</style>
