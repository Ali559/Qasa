<script setup lang="ts">
// The iOS `.alert` with a cancel and a confirm button (destructive in red). `busy` disables
// both while the confirmed action runs; `error` shows why it failed.
import Sheet from './Sheet.vue'
import { useT } from '@/prefs'

defineProps<{
  open: boolean
  title: string
  message?: string
  confirmLabel: string
  cancelLabel?: string
  destructive?: boolean
  busy?: boolean
  error?: string | null
}>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
const t = useT()
</script>

<template>
  <Sheet :open="open" :label="title" :width="400" @close="!busy && emit('cancel')">
    <div class="alert" :aria-busy="busy || undefined">
      <h2>{{ title }}</h2>
      <p v-if="message">{{ message }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="actions">
        <button type="button" class="cancel" :disabled="busy" @click="emit('cancel')">{{ cancelLabel ?? t('common.cancel') }}</button>
        <button type="button" class="confirm" :class="{ destructive }" :disabled="busy" @click="emit('confirm')">
          <span v-if="busy" class="spinner" aria-hidden="true" />{{ confirmLabel }}
        </button>
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

.actions button:disabled {
  opacity: 0.6;
  cursor: default;
}

p.error {
  color: var(--danger);
  font-weight: 500;
}

.spinner {
  display: inline-block;
  width: 12px;
  height: 12px;
  margin-inline-end: 8px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  vertical-align: -1px;
  animation: confirm-spin 900ms linear infinite;
}

@keyframes confirm-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
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
