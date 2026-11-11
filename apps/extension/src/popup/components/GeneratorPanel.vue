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
    showToast(t('ext.error'), { tone: 'error' })
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
        <RefreshCw :stroke-width="2.5" />
      </button>
    </div>
    <output class="password" dir="ltr" data-testid="generated">{{ password || ' ' }}</output>
    <button type="button" class="qasa-button secondary" :disabled="!password" @click="copy">
      <Check v-if="copied" :stroke-width="2.5" aria-hidden="true" />
      <Copy v-else :stroke-width="2.5" aria-hidden="true" />
      <span>{{ copied ? t('gen.copied') : t('gen.copyPassword') }}</span>
    </button>
  </div>
</template>

<style scoped>
/* iOS `generatedPasswordCard` (compact): large card radius, 1px stroke, an accent 10%
   wash from the top-leading corner, padding 18; the label row 12 above the password
   (22 bold mono, tracking 1.5, one line), 16 above the secondary Copy button. */
.password-card {
  display: flex;
  flex-direction: column;
  padding: 18px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card-large);
  background:
    linear-gradient(to bottom right, color-mix(in srgb, var(--accent) 10%, transparent), transparent),
    var(--card-background);
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

/* iOS `FieldIconButton` tinted accent. */
.regen {
  display: grid;
  place-items: center;
  width: var(--size-field-icon-button);
  height: var(--size-field-icon-button);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  cursor: pointer;
}

.regen svg {
  width: var(--size-field-icon-glyph);
  height: var(--size-field-icon-glyph);
}

.password {
  display: block;
  margin-bottom: 16px;
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1.5px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  user-select: text;
}
</style>
