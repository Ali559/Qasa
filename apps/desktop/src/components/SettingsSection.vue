<script setup lang="ts">
// iOS `SettingsSection`: an optional `SectionLabel` (4 in from the card's edge, with a
// `title-trailing` slot at the other end) 8 above a card of `SettingsRow`s. The card has no
// padding of its own besides the rows' 14 × 4 inset (`.settings-list`, base.css).
import CardContainer from './CardContainer.vue'
import SectionLabel from './SectionLabel.vue'

defineProps<{ title?: string }>()
</script>

<template>
  <section class="settings-section">
    <div v-if="title || $slots['title-trailing']" class="label-row">
      <SectionLabel v-if="title" :text="title" />
      <span class="spacer" />
      <slot name="title-trailing" />
    </div>
    <CardContainer class="settings-list">
      <slot />
    </CardContainer>
  </section>
</template>

<style scoped>
.settings-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-section-label-gap);
}

.label-row {
  display: flex;
  align-items: center;
  padding-inline: 4px;
}

.spacer {
  flex: 1;
}
</style>
