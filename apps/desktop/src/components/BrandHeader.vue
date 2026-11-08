<script setup lang="ts">
// iOS `KasoBrandHeader`: 64pt, chrome surface, shield + wordmark in accent, no divider.
import { Shield } from 'lucide-vue-next'

import { useT } from '@/prefs'

const t = useT()
</script>

<template>
  <header class="brand-header">
    <div class="brand">
      <Shield class="shield" :size="16" fill="currentColor" aria-hidden="true" />
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

.shield {
  width: 16px;
  height: 20px;
}

/* iOS draws the wordmark in the system face, not Rubik. */
.wordmark {
  font-family: system-ui, -apple-system, 'Segoe UI', sans-serif;
  font-size: 24px;
  font-weight: 800;
}
</style>
