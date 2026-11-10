<script setup lang="ts">
// The shape shared by Welcome, Create vault and Unlock on iOS: brand header, a scrolling
// column, and the bottom action bar holding the main button. On desktop the column is
// centered and capped so it keeps phone proportions.
import BottomActionBar from './BottomActionBar.vue'
import BrandHeader from './BrandHeader.vue'

defineProps<{ wash?: boolean }>()
</script>

<template>
  <div class="frame" :class="{ wash }">
    <BrandHeader>
      <template #trailing><slot name="header-trailing" /></template>
    </BrandHeader>
    <main class="scroll">
      <div class="column">
        <slot />
      </div>
    </main>
    <BottomActionBar v-if="$slots.footer" stack :max-width="520">
      <slot name="footer" />
    </BottomActionBar>
  </div>
</template>

<style scoped>
.frame {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--background);
}

/* Welcome's accent wash (12% → 0 over 220pt). */
.frame.wash {
  background:
    linear-gradient(to bottom, color-mix(in srgb, var(--accent) 12%, transparent), transparent 220px),
    var(--background);
}

.frame.wash :deep(.brand-header) {
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
  display: flex;
  flex-direction: column;
  padding-top: 24px;
  padding-bottom: 40px;
}
</style>
