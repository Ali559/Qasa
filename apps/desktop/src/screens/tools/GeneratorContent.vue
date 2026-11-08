<script setup lang="ts">
// iOS `PasswordGeneratorContent`, shared by the generator page and the editor's sheet:
// the generated password card (regenerate, copy), the strength bar and the options. The
// password comes from the Rust core (`core/qasa-vault/src/generator.rs`). iOS's disabled
// "Pronounceable" toggle is left out: it does nothing.
import { Check, Copy, RefreshCw } from 'lucide-vue-next'
import { onMounted, reactive, ref, watch } from 'vue'

import CardContainer from '@/components/CardContainer.vue'
import CheckToggle from '@/components/CheckToggle.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import StrengthBar from '@/components/StrengthBar.vue'
import { copySecret } from '@/clipboard'
import { useT } from '@/prefs'
import { GENERATOR_DEFAULT, GENERATOR_MAX, GENERATOR_MIN, toolsApi } from '@/vault/tools'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: true })
const password = defineModel<string>({ required: true })
const t = useT()

const options = reactive({ length: GENERATOR_DEFAULT, uppercase: true, lowercase: true, digits: true, symbols: true })
const strength = ref({ key: 'gen.strong', filled: 4 })
const copied = ref(false)

const STRENGTH_COLOR: Record<string, string> = {
  'gen.weak': 'var(--danger)',
  'gen.fair': 'var(--gold)',
  'gen.strong': 'var(--accent)',
  'gen.veryStrong': 'var(--accent)',
}

async function regenerate() {
  try {
    const g = await toolsApi.generate({ ...options })
    password.value = g.password
    strength.value = { key: g.strengthKey, filled: g.strengthFilled }
  } catch {
    // Every class switched off: iOS shows an empty password.
    password.value = ''
    strength.value = { key: 'gen.weak', filled: 1 }
  }
}

async function copy() {
  if (!password.value) return
  await copySecret(password.value)
  copied.value = true
  setTimeout(() => (copied.value = false), 1500)
}

onMounted(regenerate)
watch(options, regenerate)
</script>

<template>
  <div class="content" :class="{ compact }">
    <div class="password-card">
      <div class="card-head">
        <SectionLabel :text="t('gen.generatedPassword')" />
        <button type="button" class="regen" :aria-label="t('gen.regenerate')" :title="t('gen.regenerate')" @click="regenerate">
          <RefreshCw :size="18" />
        </button>
      </div>
      <output class="password" dir="ltr">{{ password || ' ' }}</output>
      <button type="button" class="copy" :disabled="!password" @click="copy">
        <Check v-if="copied" :size="16" />
        <Copy v-else :size="16" />
        {{ copied ? t('gen.copied') : t('gen.copyPassword') }}
      </button>
    </div>

    <CardContainer :padding="compact ? 14 : 17" class="large-radius">
      <div class="strength-head">
        <SectionLabel :text="t('gen.strength')" />
        <span class="strength-label">{{ t(strength.key) }}</span>
      </div>
      <StrengthBar class="strength-bar" :segments="5" :filled="strength.filled" :color="STRENGTH_COLOR[strength.key]" :height="8" />
    </CardContainer>

    <CardContainer :padding="compact ? 16 : 21" class="large-radius">
      <div class="settings">
        <div class="length">
          <div class="length-head">
            <label for="gen-length">{{ t('gen.length') }}</label>
            <span class="length-value">{{ options.length }}</span>
          </div>
          <input id="gen-length" v-model.number="options.length" type="range" :min="GENERATOR_MIN" :max="GENERATOR_MAX" step="1" />
        </div>
        <hr />
        <div class="toggles">
          <CheckToggle v-model="options.uppercase" :label="t('gen.uppercase')" />
          <CheckToggle v-model="options.lowercase" :label="t('gen.lowercase')" />
          <CheckToggle v-model="options.digits" :label="t('gen.numbers')" />
          <CheckToggle v-model="options.symbols" :label="t('gen.symbols')" />
        </div>
      </div>
    </CardContainer>
  </div>
</template>

<style scoped>
.content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.content.compact {
  gap: 16px;
}

.password-card {
  display: flex;
  flex-direction: column;
  padding: 25px;
  border: 1px solid var(--stroke);
  border-radius: var(--corner-radius-large);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 10%, transparent), transparent 70%),
    var(--card-background);
}

.compact .password-card {
  padding: 18px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.compact .card-head {
  margin-bottom: 12px;
}

.regen {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: var(--cyan);
  cursor: pointer;
}

.regen:hover {
  background: var(--card-muted);
}

.password {
  display: block;
  margin-bottom: 24px;
  overflow: hidden;
  font-family: var(--font-mono);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 2.8px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  user-select: all;
}

.compact .password {
  margin-bottom: 16px;
  font-size: 22px;
  letter-spacing: 1.5px;
}

.copy {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 13px;
  border: 1px solid color-mix(in srgb, var(--accent) 30%, transparent);
  border-radius: 8px;
  background: color-mix(in srgb, var(--accent-dark) 20%, transparent);
  color: var(--accent);
  font-size: 14px;
  cursor: pointer;
}

.large-radius {
  border-radius: var(--corner-radius-large);
}

.strength-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strength-label {
  font-size: 14px;
  color: var(--accent);
}

.strength-bar {
  margin-top: 8px;
}

.settings {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.compact .settings {
  gap: 16px;
}

.length {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.length-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
}

.length-value {
  padding: 5px 13px;
  border: 1px solid var(--stroke);
  border-radius: 8px;
  background: var(--card-muted);
  color: var(--accent);
  font-size: 20px;
  font-weight: 600;
}

input[type='range'] {
  width: 100%;
  accent-color: var(--accent);
}

hr {
  width: 100%;
  margin: 0;
  border: 0;
  border-top: 1px solid var(--stroke);
}

.toggles {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
