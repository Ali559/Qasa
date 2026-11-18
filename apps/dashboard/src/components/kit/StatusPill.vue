<script setup lang="ts">
export type PillTone = 'ok' | 'warn' | 'bad' | 'info' | 'neutral'

withDefaults(defineProps<{ tone?: PillTone; dot?: boolean }>(), { tone: 'neutral', dot: true })

const toneClass: Record<PillTone, string> = {
  ok: 'bg-ok/12 text-ok',
  warn: 'bg-warn/14 text-warn',
  bad: 'bg-bad/12 text-bad',
  info: 'bg-primary/10 text-primary',
  neutral: 'bg-muted text-muted-foreground',
}
</script>

<template>
  <span
    class="inline-flex h-6 items-center gap-1.5 rounded-md px-2 text-xs font-medium whitespace-nowrap"
    :class="toneClass[tone]"
  >
    <span v-if="dot" class="size-1.5 rounded-full bg-current" aria-hidden="true" />
    <!-- Sentence case: raw statuses like "not processed" get only their first letter raised. -->
    <span class="inline-block first-letter:uppercase"><slot /></span>
  </span>
</template>
