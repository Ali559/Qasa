<script setup lang="ts">
// iOS `BreachLockedCard`: what Free accounts see wherever breach results would be.
import { Crown, ShieldAlert } from 'lucide-vue-next'

import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { openPage } from '@/nav'
import { useT } from '@/prefs'

const t = useT()
</script>

<template>
  <CardContainer :padding="20">
    <div class="stack">
      <h2 class="title"><ShieldAlert :size="18" :stroke-width="2.5" aria-hidden="true" />{{ t('breached.lockedTitle') }}</h2>
      <p class="body">{{ t('breached.lockedBody') }}</p>
      <div class="button"><PrimaryButton :title="t('breached.unlock')" :icon="Crown" @click="openPage('premiumUpgrade')" /></div>
    </div>
  </CardContainer>
</template>

<style scoped>
.stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.body {
  margin: 0;
  font-size: 14px;
  font-weight: 400;
  color: var(--text-secondary);
}

.button {
  padding-top: 6px;
}
</style>
