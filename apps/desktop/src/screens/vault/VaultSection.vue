<script setup lang="ts">
// design/README.md rule 6e: one section of a vault list (Suggested, Most recent, a letter,
// a collection's items) is one card (cardBackground, 1px stroke, radius.group 20) holding
// its `VaultRow`s, with its label above (sectionLabel 12 semibold, textSecondary, indented
// 4, 8 below). Sections that follow one another are vaultSectionTop (20) apart; the first
// one's top gap is the list's own (formGap), so keep them together in one gap-less box.
defineProps<{ label?: string }>()
</script>

<template>
  <section class="vault-section">
    <h2 v-if="label" class="label">{{ label }}</h2>
    <div class="card"><slot /></div>
  </section>
</template>

<style scoped>
.vault-section {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.vault-section + .vault-section {
  margin-top: var(--space-vault-section-top);
}

.label {
  margin: 0 0 var(--space-section-label-gap);
  padding-inline: 4px;
  font-size: var(--type-section-label-size);
  font-weight: var(--type-section-label-weight);
  line-height: var(--line-height);
  color: var(--text-secondary);
}

/* overflow: hidden clips a row's tint to the card's corners. */
.card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-group);
  background: var(--card-background);
}
</style>
