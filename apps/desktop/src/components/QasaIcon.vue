<script setup lang="ts">
// One icon from the shared library (assets/icons, design/README.md "Icons"): Solar Linear,
// plus `<name>.fill` for the active navigation tab. iOS and Android draw the same files.
// `size` is the glyph's size in px against its `base` (24; the chevrons keep the iOS frames
// they were measured at, so 17 draws `chevron-back` at 13x18 and 13 `chevron-forward` at 11x14).
// Drawn in `currentColor`. Chevrons mirror in RTL.
import { computed } from 'vue'

import { QASA_ICONS, type QasaIconName } from '@/icons/qasaIcons'

const props = withDefaults(defineProps<{ name: QasaIconName; size?: number }>(), { size: 24 })

const glyph = computed(() => QASA_ICONS[props.name])
const scale = computed(() => props.size / glyph.value.base)
</script>

<template>
  <!-- The markup is our own generated SVG (assets/icons), never user input. -->
  <!-- eslint-disable vue/no-v-html -->
  <svg
    class="qasa-icon"
    :class="{ 'mirror-rtl': name.startsWith('chevron') }"
    :width="glyph.w * scale"
    :height="glyph.h * scale"
    :viewBox="`0 0 ${glyph.w} ${glyph.h}`"
    aria-hidden="true"
    focusable="false"
    v-html="glyph.body"
  />
</template>

<style scoped>
.qasa-icon {
  flex-shrink: 0;
  display: block;
}
</style>
