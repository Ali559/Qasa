<script setup lang="ts">
// iOS `WelcomeNameView`: the last screen, one optional name kept on this computer and used
// to say hi. "Let's get started" keeps what was typed (trimmed by `setUserName`); Skip
// keeps none.
import { ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { useT } from '@/prefs'

const emit = defineEmits<{ done: [name: string] }>()
const t = useT()
const name = ref('')
</script>

<template>
  <AuthFrame>
    <div class="content">
      <div class="intro">
        <h1 class="headline">{{ t('onboarding.nameTitle') }}</h1>
        <p class="subtitle">{{ t('onboarding.nameBody') }}</p>
      </div>
      <FieldGroup>
        <FieldRow
          v-model="name"
          :label="t('common.name')"
          :placeholder="t('onboarding.namePlaceholder')"
          autofocus
          @submit="emit('done', name)"
        />
      </FieldGroup>
    </div>

    <template #footer>
      <PrimaryButton :title="t('onboarding.nameGo')" @click="emit('done', name)" />
      <PrimaryButton kind="secondary" :title="t('common.skip')" @click="emit('done', '')" />
    </template>
  </AuthFrame>
</template>

<style scoped>
/* iOS: VStack(spacing: 24), 28 from the header. */
.content {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding-top: 4px;
}

.intro {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-text-gap);
}

.headline {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary);
}

.subtitle {
  margin: 0;
  font-size: var(--type-body-size);
  font-weight: var(--type-body-weight);
  color: var(--text-secondary);
}
</style>
