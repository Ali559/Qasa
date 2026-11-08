<script setup lang="ts">
// Desktop-only (plan §7): a wallet deep link (`fib://…`) can't open on a computer, so it is
// shown as a QR code to scan with the phone, with the link itself to copy as a fallback.
import { Copy, Smartphone } from 'lucide-vue-next'
import { computed } from 'vue'

import { copySecret } from '@/clipboard'
import { showToast } from '@/toast'
import { useT } from '@/prefs'
import { encodeQr, qrPath } from './qr'
import { useTr } from './shared'

const props = defineProps<{ link: string; wallet: string }>()
const t = useT()
const tr = useTr()

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
      <Smartphone :size="18" class="accent" aria-hidden="true" />
      <span class="title">{{ tr('premium.scanTitle', 'Scan with your phone') }}</span>
    </div>
    <p class="body">{{ tr('premium.scanBody', 'Open your phone camera and scan this code to pay in {0}.', wallet) }}</p>
    <div v-if="qr" class="code">
      <svg
        :viewBox="`0 0 ${qr.size} ${qr.size}`"
        shape-rendering="crispEdges"
        role="img"
        :aria-label="tr('premium.scanTitle', 'Scan with your phone')"
      >
        <rect :width="qr.size" :height="qr.size" fill="#fff" />
        <path :d="qr.path" fill="#000" />
      </svg>
    </div>
    <div class="link">
      <code>{{ link }}</code>
      <button type="button" class="copy" :aria-label="t('autofill.copy')" @click="copy">
        <Copy :size="16" aria-hidden="true" />
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
  border: 1px solid var(--accent);
  border-radius: 8px;
  background: var(--chrome);
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
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.body {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

.code {
  align-self: center;
  width: 200px;
  height: 200px;
  padding: 0;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--stroke);
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
  padding: 8px 8px 8px 12px;
  border: 1px solid var(--stroke);
  border-radius: 8px;
  background: var(--card-background);
}

code {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
  font-size: 12px;
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
  border-radius: 6px;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
</style>
