<script setup lang="ts">
// The row/detail tile: logins show their site's bundled icon (`siteIcon.ts`) or else the
// site's letter on its brand colour (iOS `SiteLogo`), other items their type symbol on a
// muted square, or a note or card's own colour on its tile fill.
import { computed } from 'vue'

import QasaIcon from '@/components/QasaIcon.vue'
import { VARIANT_ICON } from '@/icons'
import { NOTE_COLOR_TILE, NOTE_COLOR_VALUE, siteLogo } from '@/vault/present'
import { siteIconPath } from '@/vault/siteIcon'
import type { NoteColor, Variant } from '@/vault/types'

const props = withDefaults(
  defineProps<{ variant: Variant; name: string; website?: string | null; color?: NoteColor | null; size?: number }>(),
  { size: 48, website: null, color: null },
)
const iconPath = computed(() => (props.variant === 'login' ? siteIconPath(props.website) : null))
const logo = computed(() => (props.variant === 'login' && !iconPath.value ? siteLogo(props.name) : null))
</script>

<template>
  <span v-if="iconPath" class="tile site" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <svg viewBox="0 0 24 24" :width="size * 0.5" :height="size * 0.5" fill="currentColor" focusable="false">
      <path :d="iconPath" />
    </svg>
  </span>
  <span
    v-else-if="logo"
    class="tile letter"
    :style="{ width: `${size}px`, height: `${size}px`, background: logo.color, fontSize: `${size * 0.375}px` }"
    aria-hidden="true"
    >{{ logo.letter }}</span
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
    <QasaIcon :name="VARIANT_ICON[variant]" :size="size * 0.375" />
  </span>
</template>

<style scoped>
.tile {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 12px;
}

.letter {
  color: #fff;
  font-weight: 700;
}

.icon {
  background: var(--card-muted);
  color: var(--accent);
}

.site {
  background: var(--card-muted);
  color: var(--text-primary);
}
</style>
