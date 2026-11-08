<script setup lang="ts">
// The iOS Language and Theme `Menu`s on Settings, as a small sheet with the Welcome
// `ChoiceList`. Picking an option applies it at once and closes, as a menu does.
import { Contrast, Moon, Sun } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import ChoiceList, { type Choice } from '@/components/ChoiceList.vue'
import Sheet from '@/components/Sheet.vue'
import { isRtl, LANGUAGE_NAMES, LANGUAGES, type Language } from '@/i18n'
import { prefs, useT, type ThemePreference } from '@/prefs'
import SheetHeader from './SheetHeader.vue'

const props = defineProps<{ kind: 'language' | 'theme' | null }>()
const emit = defineEmits<{ close: [] }>()
const t = useT()

// The last list shown, kept while the sheet animates out after `kind` returns to null.
const shown = ref<'language' | 'theme'>(props.kind ?? 'language')
watch(
  () => props.kind,
  (kind) => {
    if (kind) shown.value = kind
  },
)

const title = computed(() => (shown.value === 'theme' ? t('settings.theme') : t('settings.language')))

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

const language = computed<Language>({
  get: () => prefs.language,
  set: (value) => {
    prefs.language = value
    emit('close')
  },
})

const theme = computed<ThemePreference>({
  get: () => prefs.theme,
  set: (value) => {
    prefs.theme = value
    emit('close')
  },
})
</script>

<template>
  <Sheet :open="kind !== null" :label="title" :width="420" @close="emit('close')">
    <SheetHeader :title="title" :done-label="t('settings.done')" @done="emit('close')" />
    <div class="body">
      <ChoiceList v-if="shown === 'language'" v-model="language" :choices="languageChoices" :label="title" />
      <ChoiceList v-else v-model="theme" :choices="themeChoices" :label="title" />
    </div>
  </Sheet>
</template>

<style scoped>
.body {
  padding: 20px;
  overflow-y: auto;
}
</style>
