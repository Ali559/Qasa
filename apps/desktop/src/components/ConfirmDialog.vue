<script setup lang="ts">
// iOS `confirmSheet` (`SelfSizingSheet` + `ConfirmSheetContent`): a bottom sheet with the
// content 20 from the top, 20 at the sides and 16 at the bottom; a centered 19 semibold
// title and 15 regular message 10 apart; 20 below them the confirm button (destructive in
// red) and under it, 10 apart, the secondary cancel button, both full width. `busy` disables both while the confirmed
// action runs; `error` shows why it failed. `noCancel` leaves only the confirm button (an
// alert with just "OK"); Esc and the scrim still close it with `cancel`.
import QasaIcon from './QasaIcon.vue'
import Sheet from './Sheet.vue'
import type { QasaIconName } from '@/icons/qasaIcons'
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
  noCancel?: boolean
  /** iOS `icon`: an icon badge (danger when destructive) scaled 1.2, 4 above the title. */
  icon?: QasaIconName
}>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
const t = useT()
</script>

<template>
  <Sheet :open="open" :label="title" :width="520" placement="bottom" @close="!busy && emit('cancel')">
    <div class="confirm-sheet" :aria-busy="busy || undefined">
      <div class="text">
        <span v-if="icon" class="badge" :class="{ destructive }" aria-hidden="true"><QasaIcon :name="icon" :size="19.2" /></span>
        <h2>{{ title }}</h2>
        <p v-if="message">{{ message }}</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </div>
      <div class="actions">
        <button type="button" class="qasa-button" :class="{ destructive }" :disabled="busy" @click="emit('confirm')">
          <span v-if="busy" class="spinner" aria-hidden="true" /><span>{{ confirmLabel }}</span>
        </button>
        <button v-if="!noCancel" type="button" class="qasa-button secondary" :disabled="busy" @click="emit('cancel')">
          <span>{{ cancelLabel ?? t('common.cancel') }}</span>
        </button>
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.confirm-sheet {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: var(--space-sheet-content-top) var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.text {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sheet-text-gap);
  text-align: center;
}

/* `IconBadge` at 1.2× (36 → 43.2), with 4 more below it. */
.badge {
  --badge-color: var(--accent);
  display: grid;
  place-items: center;
  width: calc(var(--size-icon-badge) * 1.2);
  height: calc(var(--size-icon-badge) * 1.2);
  margin-bottom: 4px;
  border-radius: calc(var(--radius-icon-badge) * 1.2);
  background: color-mix(in srgb, var(--badge-color) 12%, transparent);
  color: var(--badge-color);
}

.badge.destructive {
  --badge-color: var(--danger);
}

.badge :deep(svg) {
  width: calc(var(--size-icon-badge-glyph) * 1.2);
  height: calc(var(--size-icon-badge-glyph) * 1.2);
}

h2 {
  margin: 0;
  font-size: var(--type-dialog-title-size);
  font-weight: var(--type-dialog-title-weight);
  color: var(--text-primary);
}

p {
  margin: 0;
  font-size: var(--type-dialog-message-size);
  font-weight: var(--type-dialog-message-weight);
  color: var(--text-secondary);
}

p.error {
  color: var(--danger);
}

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}

.spinner {
  flex-shrink: 0;
  width: 12px;
  height: 12px;
  border: 2px solid currentColor;
  border-top-color: transparent;
  border-radius: 50%;
  animation: confirm-spin 900ms linear infinite;
}

@keyframes confirm-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
