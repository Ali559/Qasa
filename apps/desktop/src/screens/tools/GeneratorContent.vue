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
          <RefreshCw :stroke-width="2.5" />
        </button>
      </div>
      <output class="password" dir="ltr">{{ password || ' ' }}</output>
      <button type="button" class="qasa-button secondary" :disabled="!password" @click="copy">
        <Check v-if="copied" :stroke-width="2.5" />
        <Copy v-else :stroke-width="2.5" />
        <span>{{ copied ? t('gen.copied') : t('gen.copyPassword') }}</span>
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
  border: var(--size-stroke) solid var(--stroke);
  border-radius: var(--radius-card-large);
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

/* iOS `FieldIconButton("arrow.clockwise", tint: accent)`. */
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

.large-radius {
  border-radius: var(--radius-card-large);
}

.strength-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.strength-label {
  font-size: 14px;
  font-weight: 400;
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
  color: var(--text-primary);
  font-size: 14px;
}

.length-value {
  padding: 5px 13px;
  border: var(--size-stroke) solid var(--stroke);
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
  border-top: var(--size-stroke) solid var(--stroke);
}

.toggles {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
