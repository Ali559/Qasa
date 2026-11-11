<script setup lang="ts">
// The inline message under a field that breaks a rule (core `validate.rs`), in the
// danger style: iOS `InlineFieldError`, 11 regular.
defineProps<{ message: string | null | undefined; id?: string }>()
</script>

<template>
  <span v-if="message" :id="id" class="field-error" role="alert">{{ message }}</span>
</template>

<style scoped>
.field-error {
  font-size: var(--type-field-error-size);
  font-weight: var(--type-field-error-weight);
  color: var(--danger);
}
</style>
