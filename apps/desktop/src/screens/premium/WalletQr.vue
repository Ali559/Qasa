<script setup lang="ts">
// Desktop-only (plan §7): a wallet deep link (`fib://…`) can't open on a computer, so it is
// shown as a QR code to scan with the phone, with the link itself to copy as a fallback.
import { computed } from 'vue'

import { copySecret } from '@/clipboard'
import QasaIcon from '@/components/QasaIcon.vue'
import { showToast } from '@/toast'
import { useT } from '@/prefs'
import { encodeQr, qrPath } from './qr'

const props = defineProps<{ link: string; wallet: string }>()
const t = useT()

const qr = computed(() => {
  try {
    return qrPath(encodeQr(props.link))
  } catch {
    return null
  }
})

async function copy() {
  try {
    await copySecret(props.link)
    showToast(t('autofill.copied'))
  } catch (e) {
    console.error('Could not copy the payment link', e)
  }
}
</script>

<template>
  <div class="qr-card">
    <div class="head">
      <QasaIcon name="device" :size="18" class="accent" />
      <span class="title">{{ t('premium.scanTitle') }}</span>
    </div>
    <p class="body">{{ t('premium.scanBody', wallet) }}</p>
    <div v-if="qr" class="code">
      <svg
        :viewBox="`0 0 ${qr.size} ${qr.size}`"
        shape-rendering="crispEdges"
        role="img"
        :aria-label="t('premium.scanTitle')"
      >
        <rect :width="qr.size" :height="qr.size" fill="#fff" />
        <path :d="qr.path" fill="#000" />
      </svg>
    </div>
    <div class="link">
      <code>{{ link }}</code>
      <button type="button" class="copy" :aria-label="t('autofill.copy')" @click="copy">
        <QasaIcon name="copy" :size="16" />
        <span>{{ t('autofill.copy') }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.qr-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: var(--size-stroke) solid var(--accent);
  border-radius: var(--radius-card);
  background: var(--card-background);
}

.head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.accent {
  color: var(--accent);
}

.title {
  font-size: var(--type-row-title-size);
  font-weight: 600;
  color: var(--text-primary);
}

.body {
  margin: 0;
  font-size: var(--type-row-value-size);
  color: var(--text-secondary);
}

.code {
  align-self: center;
  width: 200px;
  height: 200px;
  padding: 0;
  border-radius: var(--radius-control);
  overflow: hidden;
  border: var(--size-stroke) solid var(--stroke);
}

.code svg {
  display: block;
  width: 100%;
  height: 100%;
}

.link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-block: 8px;
  padding-inline: 12px 8px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-control);
  background: var(--card-background);
}

code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: var(--type-caption-size);
  color: var(--text-secondary);
  direction: ltr;
  user-select: text;
}

.copy {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding: 6px 10px;
  border: 0;
  border-radius: calc(var(--radius-control) - 4px);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
  font-size: var(--type-row-value-size);
  font-weight: 600;
  cursor: pointer;
}
</style>
