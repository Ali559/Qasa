<script setup lang="ts">
// iOS `KasoBrandHeader`: 64pt, chrome surface, Qasa mark + wordmark in accent, no divider.
import QasaMark from '@/components/QasaMark.vue'

import { useT } from '@/prefs'

const t = useT()
</script>

<template>
  <header class="brand-header">
    <div class="brand">
      <QasaMark class="mark" :size="22" />
      <span class="wordmark">{{ t('brand.name') }}</span>
    </div>
    <slot name="trailing" />
  </header>
</template>

<style scoped>
.brand-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 64px;
  padding-inline: 16px;
  background: var(--chrome);
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--accent);
}

.mark {
  flex: none;
}

/* The wordmark in the app face too, at its heaviest bundled weight. */
.wordmark {
  font-family: var(--font-body);
  font-size: 24px;
  font-weight: 700;
}
</style>
