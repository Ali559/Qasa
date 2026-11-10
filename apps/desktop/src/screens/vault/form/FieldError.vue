<script setup lang="ts">
// The inline message under a field that breaks a rule (core `validate.rs`), in the
// danger style DateField always used.
defineProps<{ message: string | null | undefined; id?: string }>()
</script>

<template>
  <span v-if="message" :id="id" class="field-error" role="alert">{{ message }}</span>
</template>

<style scoped>
.field-error {
  font-size: 12px;
  line-height: 1.35;
  color: var(--danger);
}
</style>
