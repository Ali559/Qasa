<script setup lang="ts">
// The row/detail tile: logins show the site's letter on its brand colour (iOS
// `SiteLogo`), other items their type symbol on a muted square.
import { computed } from 'vue'

import { VARIANT_ICON } from '@/icons'
import { siteLogo } from '@/vault/present'
import type { Variant } from '@/vault/types'

const props = withDefaults(defineProps<{ variant: Variant; name: string; size?: number }>(), { size: 48 })
const logo = computed(() => (props.variant === 'login' ? siteLogo(props.name) : null))
</script>

<template>
  <span
    v-if="logo"
    class="tile letter"
    :style="{ width: `${size}px`, height: `${size}px`, background: logo.color, fontSize: `${size * 0.375}px` }"
    aria-hidden="true"
    >{{ logo.letter }}</span
  >
  <span v-else class="tile icon" :style="{ width: `${size}px`, height: `${size}px` }" aria-hidden="true">
    <component :is="VARIANT_ICON[variant]" :size="size * 0.4" />
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
</style>
