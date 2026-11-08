<script setup lang="ts">
// The generator tab: the desktop's generated-password card (GeneratorContent.vue), with the
// shared generator's defaults from the desktop (`generate` op). A fresh password isn't a
// stored secret, so the popup copies it itself.
import { Check, Copy, RefreshCw } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'

import SectionLabel from '@/components/SectionLabel.vue'
import { useT } from '@/prefs'
import { showToast } from '@/toast'
import { ask } from '../api'

const t = useT()
const password = ref('')
const copied = ref(false)

async function regenerate() {
  try {
    password.value = await ask<string>({ kind: 'generate' })
  } catch {
    showToast(t('ext.error'))
  }
}

async function copy() {
  if (!password.value) return
  await navigator.clipboard.writeText(password.value)
  copied.value = true
  showToast(t('gen.copied'))
  setTimeout(() => (copied.value = false), 1500)
}

onMounted(regenerate)
</script>

<template>
  <div class="password-card">
    <div class="card-head">
      <SectionLabel :text="t('gen.generatedPassword')" />
      <button type="button" class="regen" :aria-label="t('gen.regenerate')" :title="t('gen.regenerate')" @click="regenerate">
        <RefreshCw :size="18" />
      </button>
    </div>
    <output class="password" dir="ltr" data-testid="generated">{{ password || ' ' }}</output>
    <button type="button" class="copy" :disabled="!password" @click="copy">
      <Check v-if="copied" :size="16" />
      <Copy v-else :size="16" />
      {{ copied ? t('gen.copied') : t('gen.copyPassword') }}
    </button>
  </div>
</template>

<style scoped>
.password-card {
  display: flex;
  flex-direction: column;
  padding: 21px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius-large);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 70%),
    var(--card-background);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.regen {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 50%;
  background: var(--card-muted);
  color: var(--accent);
  cursor: pointer;
}

.password {
  display: block;
  margin: 16px 0 20px;
  font-family: ui-monospace, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 20px;
  font-weight: 600;
  word-break: break-all;
  user-select: text;
}

.copy {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: 0;
  border-radius: var(--corner-radius);
  background: var(--accent);
  color: var(--on-accent);
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}

.copy:disabled {
  opacity: 0.45;
}
</style>
