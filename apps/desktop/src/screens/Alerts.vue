<script setup lang="ts">
// iOS `BreachedPasswordsView` (Alerts tab). The breached-password check is Premium; until
// accounts arrive (phase 2) everyone is on Free, so this shows the locked card and the
// privacy note. The upgrade button joins the card with the Premium page.
import { ShieldAlert } from 'lucide-vue-next'

import CardContainer from '@/components/CardContainer.vue'
import PageFrame from '@/components/PageFrame.vue'
import { useT } from '@/prefs'

const t = useT()
</script>

<template>
  <PageFrame :title="t('nav.breachedPasswords')" :gap="20">
    <CardContainer :padding="20">
      <div class="stack">
        <h2 class="locked-title">
          <ShieldAlert :size="20" aria-hidden="true" />
          {{ t('breached.lockedTitle') }}
        </h2>
        <p class="body">{{ t('breached.lockedBody') }}</p>
      </div>
    </CardContainer>

    <CardContainer :padding="20">
      <div class="stack tight">
        <h3 class="privacy-title">{{ t('breached.privacyTitle') }}</h3>
        <p class="body">{{ t('breached.privacyBody') }}</p>
      </div>
    </CardContainer>
  </PageFrame>
</template>

<style scoped>
.stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.stack.tight {
  gap: 8px;
}

.locked-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.privacy-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.body {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
