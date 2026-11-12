<script setup lang="ts">
// iOS `WelcomeLanguageView`: the first screen, language only. The theme follows the system
// until it is changed in Settings.
import { computed } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { isRtl, LANGUAGE_NAMES, LANGUAGES, type Language } from '@/i18n'
import { prefs, useT } from '@/prefs'

const emit = defineEmits<{ continue: [] }>()
const t = useT()

const choices = computed<Choice<Language>[]>(() =>
  LANGUAGES.map((language) => ({
    value: language,
    label: LANGUAGE_NAMES[language],
    icon: 'language',
    dir: isRtl(language) ? 'rtl' : 'ltr',
    lang: language,
  })),
)
</script>

<template>
  <AuthFrame wash>
    <div class="content">
      <div class="intro">
        <h1 class="headline">{{ t('onboarding.welcomeTitle') }}</h1>
        <p class="subtitle">{{ t('onboarding.welcomeSubtitle') }}</p>
      </div>
      <ChoiceList v-model="prefs.language" :choices="choices" :label="t('onboarding.language')" />
    </div>

    <template #footer>
      <PrimaryButton :title="t('onboarding.getStarted')" @click="emit('continue')" />
    </template>
  </AuthFrame>
</template>

<style scoped>
/* iOS: VStack(spacing: 28), 28 from the header (AuthFrame's 24 + 4). */
.content {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding-top: 4px;
}

.intro {
  display: flex;
  flex-direction: column;
  gap: var(--space-sheet-text-gap);
}

/* iOS 28 bold. */
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
