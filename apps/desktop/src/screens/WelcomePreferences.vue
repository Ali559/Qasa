<script setup lang="ts">
// iOS `WelcomePreferencesView`: language and theme, chosen before the vault is created.
// Desktop: the same column, centered and capped so it keeps phone proportions.
import { ArrowRight, Contrast, Moon, Sun } from 'lucide-vue-next'
import { computed } from 'vue'

import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import BrandHeader from '@/components/BrandHeader.vue'
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
  <div class="welcome">
    <BrandHeader />

    <main class="scroll">
      <div class="column">
        <div class="intro">
          <h1 class="headline">{{ t('onboarding.headline') }}</h1>
          <p class="subtitle">{{ t('onboarding.subtitle') }}</p>
        </div>

        <section class="group">
          <SectionLabel :text="t('onboarding.language')" />
          <ChoiceList v-model="prefs.language" :choices="languageChoices" :label="t('onboarding.language')" />
        </section>

        <section class="group">
          <SectionLabel :text="t('onboarding.theme')" />
          <ChoiceList v-model="prefs.theme" :choices="themeChoices" :label="t('onboarding.theme')" />
        </section>
      </div>
    </main>

    <footer class="sticky-bar">
      <div class="column">
        <PrimaryButton :title="t('onboarding.continue')" :icon="ArrowRight" mirror-icon @click="finish" />
      </div>
    </footer>
  </div>
</template>

<style scoped>
.welcome {
  display: flex;
  flex-direction: column;
  height: 100%;
  /* The accent wash at the top, as on iOS (12% → 0 over 220pt). */
  background:
    linear-gradient(to bottom, color-mix(in srgb, var(--accent) 12%, transparent), transparent 220px),
    var(--background);
}

.welcome :deep(.brand-header) {
  background: transparent;
}

.scroll {
  flex: 1;
  overflow-y: auto;
}

.column {
  width: 100%;
  max-width: 560px;
  margin-inline: auto;
  padding-inline: var(--screen-padding);
}

.scroll .column {
  display: flex;
  flex-direction: column;
  gap: 28px;
  padding-top: 28px;
  padding-bottom: 24px;
}

.intro,
.group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.headline {
  margin: 0;
  font-size: 28px;
  font-weight: 800;
  color: var(--text-primary);
}

.subtitle {
  margin: 0;
  font-size: 15px;
  color: var(--text-secondary);
}

.sticky-bar {
  flex-shrink: 0;
  border-top: 1px solid var(--stroke);
  background: color-mix(in srgb, var(--chrome) 92%, transparent);
}

.sticky-bar .column {
  padding-top: 17px;
  padding-bottom: 32px;
}
</style>
