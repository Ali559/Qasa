<script setup lang="ts">
// iOS `textPromptSheet` (`TextPromptSheetContent`): a bottom sheet with the shared
// `SheetHeader` title, then an optional 15 message, the field and the confirm button, 20
// apart, 20 at the sides and 16 at the bottom. No Cancel: the sheet closes with Esc or a
// click outside (`cancel`). Confirm stays disabled while the text is blank; Return confirms.
import PrimaryButton from '@/components/PrimaryButton.vue'
import Sheet from '@/components/Sheet.vue'
import TextField from '@/components/TextField.vue'

defineProps<{
  open: boolean
  title: string
  message?: string
  placeholder: string
  confirmLabel: string
  mono?: boolean
}>()
const text = defineModel<string>({ required: true })
const emit = defineEmits<{ confirm: []; cancel: [] }>()

function confirm() {
  if (text.value.trim()) emit('confirm')
}
</script>

<template>
  <Sheet :open="open" :label="title" :title="title" :width="420" placement="bottom" @close="emit('cancel')">
    <div class="prompt">
      <p v-if="message">{{ message }}</p>
      <TextField v-model="text" :placeholder="placeholder" :mono="mono" autofocus @submit="confirm" />
      <PrimaryButton :title="confirmLabel" :disabled="!text.trim()" @click="confirm" />
    </div>
  </Sheet>
</template>

<style scoped>
.prompt {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-actions-gap);
  padding: 0 var(--space-screen-padding) var(--space-sheet-content-bottom);
}

p {
  margin: 0;
  text-align: center;
  font-size: var(--type-dialog-message-size);
  font-weight: var(--type-dialog-message-weight);
  color: var(--text-secondary);
}
</style>
