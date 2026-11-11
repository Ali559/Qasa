<script setup lang="ts">
// iOS `PrimaryButton` / `QasaButtonStyle`: full width, `--size-button-height` tall, control
// radius, 16 semibold, optional 15pt leading icon 8 from the title; pressed = opacity .85 +
// scale .98 over `--motion-press`, disabled = opacity .45 (`.qasa-button`, base.css).
import type { Component } from 'vue'

withDefaults(
  defineProps<{
    title: string
    icon?: Component
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
    <component :is="icon" v-if="icon" :stroke-width="2.5" :class="{ 'mirror-rtl': mirrorIcon }" aria-hidden="true" />
    <span>{{ title }}</span>
  </button>
</template>
