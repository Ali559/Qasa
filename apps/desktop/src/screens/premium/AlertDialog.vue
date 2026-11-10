<script setup lang="ts">
// The iOS `.alert` where the cancel button has its own label ("Keep Premium"): the shared
// confirm sheet with that label.
import ConfirmDialog from '@/components/ConfirmDialog.vue'

defineProps<{ open: boolean; title: string; message?: string; cancelLabel: string; confirmLabel: string; destructive?: boolean }>()
const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <ConfirmDialog
    :open="open"
    :title="title"
    :message="message"
    :cancel-label="cancelLabel"
    :confirm-label="confirmLabel"
    :destructive="destructive"
    @confirm="emit('confirm')"
    @cancel="emit('cancel')"
  />
</template>
