<script setup lang="ts">
// iOS `LegalPlaceholderView`: title, a "last updated" line and the placeholder body.
import PageFrame from '@/components/PageFrame.vue'
import { prefs, useT } from '@/prefs'
import { computed } from 'vue'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()
defineEmits<{ back: [] }>()
const t = useT()

/** When each document last changed (iOS `termsUpdated` / `privacyUpdated`). */
const LAST_UPDATED = { terms: new Date(2026, 7, 25), privacy: new Date(2026, 10, 9) }

const title = computed(() => t(props.kind === 'terms' ? 'nav.terms' : 'nav.privacy'))
const updated = computed(() =>
  t('legal.lastUpdated', LAST_UPDATED[props.kind].toLocaleDateString(prefs.language, { year: 'numeric', month: 'short', day: 'numeric' })),
)
</script>

<template>
  <PageFrame :title="title" back :gap="16" @back="$emit('back')">
    <h2 class="title">{{ title }}</h2>
    <p class="updated">{{ updated }}</p>
    <p class="body">{{ t(kind === 'terms' ? 'legal.termsBody' : 'legal.privacyBody') }}</p>
  </PageFrame>
</template>

<style scoped>
.title {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
}

.updated {
  margin: 0;
  font-size: 13px;
  color: var(--text-tertiary);
}

.body {
  margin: 0;
  font-size: 15px;
  line-height: 1.55;
  color: var(--text-secondary);
  white-space: pre-line;
  user-select: text;
}
</style>
