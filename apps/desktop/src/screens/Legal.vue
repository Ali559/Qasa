<script setup lang="ts">
// iOS `LegalPlaceholderView`: title, a "last updated" line and the placeholder body.
import PageFrame from '@/components/PageFrame.vue'
import { prefs, useT } from '@/prefs'
import { computed } from 'vue'

const props = defineProps<{ kind: 'terms' | 'privacy' }>()
defineEmits<{ back: [] }>()
const t = useT()

/** Placeholder publish date until the real documents ship (iOS uses the same). */
const LAST_UPDATED = new Date(2026, 7, 25)

const title = computed(() => t(props.kind === 'terms' ? 'nav.terms' : 'nav.privacy'))
const updated = computed(() =>
  t('legal.lastUpdated', LAST_UPDATED.toLocaleDateString(prefs.language, { year: 'numeric', month: 'short', day: 'numeric' })),
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
