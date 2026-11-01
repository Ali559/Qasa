<script setup lang="ts">
export type PillTone = 'ok' | 'warn' | 'bad' | 'info' | 'neutral'

withDefaults(defineProps<{ tone?: PillTone; dot?: boolean }>(), { tone: 'neutral', dot: true })

const toneClass: Record<PillTone, string> = {
  ok: 'bg-ok/14 text-ok',
  warn: 'bg-warn/16 text-warn',
  bad: 'bg-bad/14 text-bad',
  info: 'bg-primary/12 text-primary',
  neutral: 'bg-muted text-muted-foreground',
}
</script>

<template>
  <span
    class="inline-flex h-6 items-center gap-1.5 rounded-full px-2.5 text-xs font-semibold whitespace-nowrap capitalize"
    :class="toneClass[tone]"
  >
    <span v-if="dot" class="size-1.5 rounded-full bg-current" aria-hidden="true" />
    <slot />
  </span>
</template>
