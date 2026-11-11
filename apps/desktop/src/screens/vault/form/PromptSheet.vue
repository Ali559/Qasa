<script setup lang="ts">
// iOS `textPromptSheet` (`SelfSizingSheet` + `TextPromptSheetContent`): a bottom sheet with
// the content 20 from the top, 20 at the sides and 16 at the bottom; a centered 19 semibold
// title and optional 15 message 8 apart, then the field, then the confirm button over the
// cancel button (10 apart), 20 between the three blocks. Confirm stays disabled while the
// text is blank; Return confirms.
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'

defineProps<{
  open: boolean
  title: string
  message?: string
  placeholder: string
  confirmLabel: string
  cancelLabel: string
  mono?: boolean
}>()
const text = defineModel<string>({ required: true })
const emit = defineEmits<{ confirm: []; cancel: [] }>()

function confirm() {
  if (text.value.trim()) emit('confirm')
}
</script>

<template>
  <Sheet :open="open" :label="title" :width="420" placement="bottom" @close="emit('cancel')">
    <div class="prompt">
      <div class="text">
        <h2>{{ title }}</h2>
        <p v-if="message">{{ message }}</p>
      </div>
      <TextField v-model="text" :placeholder="placeholder" :mono="mono" autofocus @submit="confirm" />
      <div class="actions">
        <PrimaryButton :title="confirmLabel" :disabled="!text.trim()" @click="confirm" />
        <PrimaryButton kind="secondary" :title="cancelLabel" @click="emit('cancel')" />
      </div>
    </div>
  </Sheet>
</template>

<style scoped>
.prompt {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: var(--space-sheet-content-top) var(--space-screen-padding) var(--space-sheet-content-bottom);
}

.text {
  display: flex;
  flex-direction: column;
  gap: var(--space-field-label-gap);
  text-align: center;
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

.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-button-gap);
}
</style>
