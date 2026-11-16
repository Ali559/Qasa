<script setup lang="ts">
// The app icon (assets/brand/qasa-icon.svg, and qasa-icon-dark.svg in the dark theme): the
// Qasa mark on its rounded tile, as the system shows it. Shapes come from brand/qasaMark.ts;
// the colours are the brand files' own (the tile is the icon, not a themed surface).
import { QASA_MARK_SHAPES } from '@/brand/qasaMark'

withDefaults(defineProps<{ size?: number }>(), { size: 96 })

/** The keyhole (the last two shapes) is coral in both icons; the rest is the mark colour. */
const KEYHOLE_FROM = QASA_MARK_SHAPES.length - 2

/** The colours go in `style`: WebKit ignores `var()` in SVG presentation attributes. */
function paint(attrs: Record<string, string>, i: number) {
  const color = i >= KEYHOLE_FROM ? 'var(--app-icon-keyhole)' : 'var(--app-icon-mark)'
  const { fill, stroke, ...rest } = attrs
  return { ...rest, style: { fill: fill === 'none' ? 'none' : color, stroke: stroke ? color : undefined } }
}
</script>

<template>
  <svg class="app-icon" viewBox="0 0 200 200" :width="size" :height="size" aria-hidden="true" focusable="false">
    <rect width="200" height="200" rx="44" style="fill: var(--app-icon-tile)" />
    <g transform="translate(100 100) scale(0.85) translate(-105.0 -104.5)">
      <component :is="shape.tag" v-for="(shape, i) in QASA_MARK_SHAPES" :key="i" v-bind="paint(shape.attrs, i)" />
    </g>
  </svg>
</template>

<style>
/* Not scoped: the dark icon follows the theme set on the root. */
.app-icon {
  --app-icon-tile: #722f99;
  --app-icon-mark: #fee7c8;
  --app-icon-keyhole: #ff9292;
  display: block;
  flex: none;
}

:root[data-theme='dark'] .app-icon {
  --app-icon-tile: #17111d;
  --app-icon-mark: #b67fdb;
}

@media (prefers-color-scheme: dark) {
  :root:not([data-theme='light']) .app-icon {
    --app-icon-tile: #17111d;
    --app-icon-mark: #b67fdb;
  }
}
</style>
