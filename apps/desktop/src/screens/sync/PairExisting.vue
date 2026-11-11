<script setup lang="ts">
// "I already have Qasa on another device" (iOS `PairExistingVaultView`), now the real
// thing: this computer joins the other device's vault over Wi‑Fi — the vault and account
// arrive through the code-authenticated channel — then you unlock with the same main
// password.
import Chevron from '@/components/Chevron.vue'
import {ArrowRight, CheckCircle2} from 'lucide-vue-next'
import { ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { useT } from '@/prefs'
import type { SyncEvent } from '@/sync/api'
import JoinPanel from './JoinPanel.vue'

const emit = defineEmits<{ back: []; paired: [] }>()
const t = useT()
const done = ref<Extract<SyncEvent, { stage: 'done' }> | null>(null)
</script>

<template>
  <AuthFrame>
    <div class="content">
      <button v-if="!done" type="button" class="back" @click="emit('back')">
        <Chevron dir="back" />{{ t('common.back') }}
      </button>

      <template v-if="!done">
        <h1 class="title">{{ t('pair.headline') }}</h1>
        <p class="muted">{{ t('pair.desktopBody') }}</p>
        <JoinPanel @done="(e) => (done = e)" />
      </template>

      <template v-else>
        <CheckCircle2 :size="48" class="ok" />
        <h1 class="title">{{ t('sync.adoptedTitle', done.peerName) }}</h1>
        <p class="muted">{{ t('sync.adoptedBody', done.peerName) }}</p>
      </template>
    </div>

    <template #footer>
      <PrimaryButton v-if="done" :title="t('pair.done')" :icon="ArrowRight" mirror-icon @click="emit('paired')" />
    </template>
  </AuthFrame>
</template>

<style scoped>
.content {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
}

.back {
  display: inline-flex;
  align-self: flex-start;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: var(--type-body-size);
  font-weight: var(--type-button-weight);
  cursor: pointer;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.muted {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.ok {
  color: var(--success);
}
</style>
