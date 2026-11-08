<script setup lang="ts">
// iOS `TOTPCodeRow`: the live code (grouped, mono), a countdown ring that turns red in
// the last 5 seconds, and copy. Only codes are shown, never the 2FA secret.
import { Copy } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { useT } from '@/prefs'
import { toolsApi, type TotpCode } from '@/vault/tools'

const props = defineProps<{ itemId: string }>()
const emit = defineEmits<{ copy: [code: string, secondsLeft: number] }>()
const t = useT()
const code = ref<TotpCode | null>(null)
let timer: ReturnType<typeof setInterval> | undefined

async function tick() {
  try {
    code.value = await toolsApi.totpCode(props.itemId)
  } catch {
    code.value = null
  }
}

onMounted(() => {
  tick()
  timer = setInterval(tick, 1000)
})
onBeforeUnmount(() => clearInterval(timer))

const R = 12
const CIRC = 2 * Math.PI * R
</script>

<template>
  <div v-if="code" class="totp" role="button" tabindex="0" @click="emit('copy', code.code, code.secondsRemaining)" @keydown.enter="emit('copy', code.code, code.secondsRemaining)">
    <div class="head">
      <span class="label">{{ t('field.totp') }}</span>
      <Copy :size="15" class="accent" :aria-label="t('copy.totp')" />
    </div>
    <div class="body">
      <span class="code">{{ code.grouped }}</span>
      <svg class="ring" :class="{ urgent: code.secondsRemaining <= 5 }" width="28" height="28" viewBox="0 0 28 28" aria-hidden="true">
        <circle cx="14" cy="14" :r="R" class="track" />
        <circle
          cx="14"
          cy="14"
          :r="R"
          class="progress"
          :stroke-dasharray="CIRC"
          :stroke-dashoffset="CIRC * (1 - code.secondsRemaining / code.period)"
        />
        <text x="14" y="18" text-anchor="middle">{{ code.secondsRemaining }}</text>
      </svg>
    </div>
  </div>
</template>

<style scoped>
.totp {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 12px 0;
  cursor: pointer;
}

.head,
.body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.label {
  font-size: 13px;
  color: var(--text-secondary);
}

.accent {
  color: var(--accent);
}

.code {
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 600;
  direction: ltr;
}

.ring {
  transform: rotate(-90deg);
}

.ring text {
  transform: rotate(90deg);
  transform-origin: 14px 14px;
  font-size: 11px;
  font-weight: 600;
  fill: var(--text-secondary);
}

.track,
.progress {
  fill: none;
  stroke-width: 3;
}

.track {
  stroke: var(--stroke);
}

.progress {
  stroke: var(--accent);
  stroke-linecap: round;
  transition: stroke-dashoffset 1s linear;
}

.urgent .progress {
  stroke: var(--danger);
}

.urgent text {
  fill: var(--danger);
}
</style>
