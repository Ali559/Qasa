<script setup lang="ts">
// App state machine, as on iOS (docs/design.md §12.1):
// launching → welcome preferences → create vault | unlock → shell.
// Phase 0 has only the first step; the rest arrives in phase 1.
import { ref } from 'vue'

import BrandHeader from '@/components/BrandHeader.vue'
import { prefs } from '@/prefs'
import WelcomePreferences from '@/screens/WelcomePreferences.vue'

const showWelcome = ref(!prefs.hasCompletedWelcome)

function showWelcomeAgain() {
  prefs.hasCompletedWelcome = false
  showWelcome.value = true
}
</script>

<template>
  <WelcomePreferences v-if="showWelcome" @continue="showWelcome = false" />

  <!-- Development stand-in until phase 1 brings Create vault and Unlock. Not localized. -->
  <div v-else class="next-phase">
    <BrandHeader />
    <main>
      <p>Create vault and Unlock arrive in phase 1.</p>
      <button type="button" @click="showWelcomeAgain">Show Welcome again</button>
    </main>
  </div>
</template>

<style scoped>
.next-phase {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.next-phase main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  flex: 1;
  color: var(--text-secondary);
}

.next-phase button {
  padding: 8px 16px;
  border: 1px solid var(--stroke);
  border-radius: 999px;
  background: var(--card-background);
  cursor: pointer;
}
</style>
