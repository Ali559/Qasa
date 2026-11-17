<script setup lang="ts">
// The row tile (docs/site-icons.md "What a tile shows"): a login shows its site's own icon
// (already shaped for the circle when it was cached) filling the tile on `siteIconTile`,
// cut to the circle, with a 1px `stroke` ring only for a glyph (`siteIconRing`), or the calm letter (accent on `cardMuted`)
// until one arrives or when there is none; other items their type symbol on a muted
// circle, or a note or card's own colour on its tile fill.
import { computed } from 'vue'

import QasaIcon from '@/components/QasaIcon.vue'
import { VARIANT_ICON } from '@/icons'
import { NOTE_COLOR_TILE, NOTE_COLOR_VALUE, siteLetter } from '@/vault/present'
import { iconHost, siteIconFor, siteIconRing } from '@/vault/siteIcon'
import type { NoteColor, Variant } from '@/vault/types'

const props = withDefaults(
  defineProps<{
    variant: Variant
    name: string
    website?: string | null
    color?: NoteColor | null
    size?: number
    /** The icon as a `data:image/png` URL, when the caller has it (the extension popup).
     *  Left out, the tile reads the desktop's site icon store by the website's host. */
    icon?: string | null
  }>(),
  { size: 48, website: null, color: null, icon: undefined },
)
const login = computed(() => props.variant === 'login')
const image = computed(() => {
  if (!login.value) return null
  if (props.icon !== undefined) return props.icon
  return siteIconFor(iconHost(props.website))
})
const ring = computed(() => !!image.value && siteIconRing(image.value))
const letter = computed(() => siteLetter(props.name))
</script>

<template>
  <span v-if="image" class="tile site" :class="{ ring }" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <img :src="image" :width="size" :height="size" alt="" draggable="false" />
  </span>
  <span
    v-else-if="login"
    class="tile letter"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${size >= 44 ? 18 : (size * 18) / 48}px` }"
    aria-hidden="true"
    >{{ letter }}</span
  >
  <span
    v-else
    class="tile icon"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
      ...(color ? { background: NOTE_COLOR_TILE[color], color: NOTE_COLOR_VALUE[color] } : {}),
    }"
    aria-hidden="true"
  >
    <!-- A glyph fills half its tile: itemTileGlyph 24 of 48, vaultTileGlyph 22 of 44. -->
    <QasaIcon :name="VARIANT_ICON[variant]" :size="size * 0.5" />
  </span>
</template>

<style scoped>
.tile {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  box-sizing: border-box;
  border-radius: 50%;
}

.letter {
  background: var(--card-muted);
  color: var(--accent);
  font-weight: 700;
  line-height: 1;
}

.icon {
  background: var(--card-muted);
  color: var(--accent);
}

.site {
  position: relative;
  overflow: hidden;
  background: var(--site-icon-tile);
}

.site img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* A glyph's ring, drawn over the icon's edge. A bleed or edge icon has none. */
.site.ring::after {
  content: '';
  position: absolute;
  inset: 0;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: 50%;
  pointer-events: none;
}
</style>
