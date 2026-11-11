<script setup lang="ts">
// iOS `WelcomePreferencesView`: language and theme, chosen before the vault is created.
import { ArrowRight, Contrast, Languages, Moon, Sun } from 'lucide-vue-next'
import { computed } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import { isRtl, LANGUAGE_NAMES, LANGUAGES, type Language } from '@/i18n'
import { prefs, useT, type ThemePreference } from '@/prefs'

const emit = defineEmits<{ continue: [] }>()
const t = useT()

const languageChoices = computed<Choice<Language>[]>(() =>
  LANGUAGES.map((language) => ({
    value: language,
    label: LANGUAGE_NAMES[language],
    icon: Languages,
    dir: isRtl(language) ? 'rtl' : 'ltr',
    lang: language,
  })),
)

const themeChoices = computed<Choice<ThemePreference>[]>(() => [
  { value: 'system', label: t('settings.theme.system'), icon: Contrast },
  { value: 'light', label: t('settings.theme.light'), icon: Sun },
  { value: 'dark', label: t('settings.theme.dark'), icon: Moon },
])

function finish() {
  prefs.hasCompletedWelcome = true
  emit('continue')
}
</script>

<template>
  <AuthFrame wash>
    <div class="content">
      <div class="intro">
        <h1 class="headline">{{ t('onboarding.headline') }}</h1>
        <p class="subtitle">{{ t('onboarding.subtitle') }}</p>
      </div>

      <section class="group">
        <span class="label"><SectionLabel :text="t('onboarding.language')" /></span>
        <ChoiceList v-model="prefs.language" :choices="languageChoices" :label="t('onboarding.language')" />
      </section>

      <section class="group">
        <span class="label"><SectionLabel :text="t('onboarding.theme')" /></span>
        <ChoiceList v-model="prefs.theme" :choices="themeChoices" :label="t('onboarding.theme')" />
      </section>
    </div>

    <template #footer>
      <PrimaryButton :title="t('onboarding.continue')" :icon="ArrowRight" mirror-icon @click="finish" />
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

/* iOS `SettingsSection`: label 4 in, 8 above the card. */
.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-section-label-gap);
}

.label {
  padding-inline: 4px;
}

/* iOS 28 heavy; 700 is the heaviest bundled weight. */
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
