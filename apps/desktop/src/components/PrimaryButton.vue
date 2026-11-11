<script setup lang="ts">
// iOS `PrimaryButton` / `QasaButtonStyle`: full width, `--size-button-height` tall, control
// radius, 16 semibold, optional 15pt leading icon 8 from the title; pressed = opacity .85 +
// scale .98 over `--motion-press`, disabled = opacity .45 (`.qasa-button`, base.css).
import QasaIcon from './QasaIcon.vue'
import type { QasaIconName } from '@/icons/qasaIcons'

withDefaults(
  defineProps<{
    title: string
    icon?: QasaIconName
    /** Arrows and chevrons flip in RTL. */
    mirrorIcon?: boolean
    disabled?: boolean
    kind?: 'primary' | 'secondary' | 'destructive' | 'plain'
  }>(),
  { kind: 'primary' },
)

defineEmits<{ click: [] }>()
</script>

<template>
  <button class="qasa-button primary-button" :class="kind" type="button" :disabled="disabled" @click="$emit('click')">
    <QasaIcon v-if="icon" :name="icon" :size="15" :class="{ 'mirror-rtl': mirrorIcon }" />
    <span>{{ title }}</span>
  </button>
</template>
