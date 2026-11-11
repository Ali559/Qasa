<script setup lang="ts">
// A full-height message card for the states before the vault list: app not running, not
// paired, locked, no vault. Same layout as the desktop's empty states: tinted icon disc,
// title, body, then the actions in the slot.
import CardContainer from '@/components/CardContainer.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

defineProps<{ icon: QasaIconName; title: string; body: string; tone?: 'accent' | 'gold' | 'danger' }>()
</script>

<template>
  <CardContainer :padding="21" class="state-card">
    <span class="disc" :class="tone ?? 'accent'">
      <QasaIcon :name="icon" :size="26" />
    </span>
    <h1 class="title">{{ title }}</h1>
    <p class="body">{{ body }}</p>
    <div class="actions"><slot /></div>
  </CardContainer>
</template>

<style scoped>
.state-card {
  align-items: center;
  gap: 12px;
  text-align: center;
}

.disc {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: 50%;
}

.disc.accent {
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
}

.disc.gold {
  background: color-mix(in srgb, var(--gold) 16%, transparent);
  color: var(--gold-muted);
}

.disc.danger {
  background: var(--danger-background);
  color: var(--danger);
}

.title {
  margin: 0;
  font-size: var(--type-page-title-size);
  font-weight: var(--type-sheet-title-weight);
}

.body {
  margin: 0;
  font-size: var(--type-body-size);
  font-weight: var(--type-body-weight);
  color: var(--text-secondary);
}

/* The actions stack 10 apart, as `BottomActionBar`. */
.actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-bottom-bar-gap);
  width: 100%;
  margin-top: 8px;
}
</style>
