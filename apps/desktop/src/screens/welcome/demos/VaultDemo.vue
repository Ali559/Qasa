<script setup lang="ts">
// iOS tour page 1, `VaultDemo`: three items rise in `tourStagger` apart, then the
// "Stays on this computer" pill.
import StatusPill from '@/components/StatusPill.vue'
import { useT } from '@/prefs'
import TourDemoCard from './TourDemoCard.vue'
import TourDemoRow from './TourDemoRow.vue'

const t = useT()
</script>

<template>
  <div class="vault-demo">
    <TourDemoCard>
      <TourDemoRow icon="login" color="purple" title="Gmail" subtitle="you@example.com" ltr-subtitle :order="0" />
      <TourDemoRow icon="card" color="gold" title="Visa" subtitle="•••• 4821" ltr-subtitle :order="1" />
      <TourDemoRow icon="note" color="mint" :title="t('onboarding.mockWifi')" :subtitle="t('field.note')" :order="2" />
    </TourDemoCard>
    <span class="pill-wrap rise">
      <StatusPill class="local-pill" :text="t('brand.localOffline')" dot-color="var(--success)" />
    </span>
  </div>
</template>

<style scoped>
.vault-demo {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.vault-demo .local-pill {
  color: var(--success);
}

@media (prefers-reduced-motion: no-preference) {
  .rise {
    animation: rise var(--motion-tour-enter) both;
    animation-delay: calc(0.05s + 3 * var(--motion-tour-stagger) * 1s);
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.96);
  }
}
</style>
