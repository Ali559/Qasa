<script setup lang="ts">
// iOS `CreateVaultView`. The biometrics card shows when the computer has Touch ID or
// Windows Hello; "I already have Qasa on another device" joins a vault over Wi‑Fi.
import { Fingerprint, KeyRound, Lock, LockKeyhole, ScanFace, Shield } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import SectionLabel from '@/components/SectionLabel.vue'
import StatusPill from '@/components/StatusPill.vue'
import StrengthBar from '@/components/StrengthBar.vue'
import TextField from '@/components/TextField.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { isCommandError, type BiometricStatus } from '@/vault/types'

const emit = defineEmits<{ created: []; pair: [] }>()
const t = useT()

const password = ref('')
const confirm = ref('')
const error = ref<string | null>(null)
const busy = ref(false)
const bio = ref<BiometricStatus | null>(null)
/** iOS defaults the toggle on. */
const biometrics = ref(true)

onMounted(async () => {
  bio.value = await vaultApi.biometricStatus().catch(() => null)
})

/** Characters, not UTF-16 units, as Swift's `String.count`. */
const length = computed(() => [...password.value].length)

const strength = computed(() => {
  const n = length.value
  if (n === 0) return { label: '', filled: 0, color: 'var(--stroke)' }
  if (n < 4) return { label: t('auth.create.weak'), filled: 1, color: 'var(--danger)' }
  if (n < 8) return { label: t('auth.create.fair'), filled: 2, color: 'var(--gold-muted)' }
  return { label: t('auth.create.strong'), filled: 3, color: 'var(--success)' }
})

const canCreate = computed(() => length.value >= 8 && password.value === confirm.value)

async function create() {
  if (!canCreate.value) {
    error.value = !password.value
      ? t('auth.create.enterPassword')
      : length.value < 8
        ? t('settings.weakMasterPassword')
        : t('auth.create.mismatch')
    return
  }
  error.value = null
  busy.value = true
  try {
    await vaultApi.create(password.value, biometrics.value && bio.value?.kind !== 'none' && !!bio.value)
    password.value = ''
    confirm.value = ''
    emit('created')
  } catch (e) {
    error.value = isCommandError(e) && e.code === 'weakPassword' ? t('settings.weakMasterPassword') : t('error.vaultCreateTitle')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <AuthFrame>
    <template #header-trailing><StatusPill :text="t('brand.localOffline')" /></template>

    <div class="content">
      <div class="intro">
        <h1 class="title">{{ t('auth.create.title') }}</h1>
        <p class="subtitle">{{ t('auth.create.subtitle') }}</p>
        <button type="button" class="pair" @click="emit('pair')">{{ t('auth.create.pairExisting') }}</button>
      </div>

      <div class="group">
        <SectionLabel :text="t('auth.unlock.masterPassword')" />
        <TextField
          v-model="password"
          :icon="Lock"
          :placeholder="t('auth.unlock.placeholder')"
          :label="t('auth.unlock.masterPassword')"
          secure
          revealable
          autofocus
          @submit="create"
        />
        <div v-if="password" class="strength">
          <div class="strength-row">
            <span>{{ t('auth.create.strength') }}</span>
            <span class="strength-label">{{ strength.label }}</span>
          </div>
          <StrengthBar :segments="3" :filled="strength.filled" :color="strength.color" />
        </div>
      </div>

      <div class="group confirm">
        <SectionLabel :text="t('auth.create.confirmPassword')" />
        <TextField
          v-model="confirm"
          :icon="LockKeyhole"
          :placeholder="t('auth.create.reenter')"
          :label="t('auth.create.confirmPassword')"
          secure
          @submit="create"
        />
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <CardContainer v-if="bio && bio.kind !== 'none'" class="biometric">
        <div class="bio-row">
          <span class="bio-icon">
            <ScanFace v-if="bio.kind === 'windowsHello'" :size="20" />
            <Fingerprint v-else :size="20" />
          </span>
          <span class="bio-text">
            <span class="bio-title">{{ t('auth.create.enableBiometrics') }}</span>
            <span class="bio-hint">{{ t('auth.create.biometricsHint') }}</span>
          </span>
          <ToggleSwitch v-model="biometrics" :label="t('auth.create.enableBiometrics')" />
        </div>
      </CardContainer>

      <CardContainer muted class="zero-knowledge">
        <Shield class="watermark" :size="88" fill="currentColor" aria-hidden="true" />
        <div class="zk-title">
          <Lock :size="16" class="accent" fill="currentColor" />
          <span>{{ t('auth.create.zeroKnowledge') }}</span>
        </div>
        <p class="zk-body">{{ t('auth.create.zeroKnowledgeBody') }}</p>
      </CardContainer>
    </div>

    <template #footer>
      <PrimaryButton
        :title="busy ? t('auth.securingVault') : t('auth.create.buttonLocal')"
        :icon="KeyRound"
        :disabled="!canCreate || busy"
        @click="create"
      />
    </template>
  </AuthFrame>
</template>

<style scoped>
.content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.intro {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 4px;
}

.title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}

.pair {
  align-self: flex-start;
  margin-top: 4px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--accent);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.confirm {
  padding-top: 4px;
}

.strength {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 4px;
}

.strength-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.strength-label {
  color: var(--accent);
}

.error {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--danger);
}

.biometric {
  margin-top: 12px;
}

.bio-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bio-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--card-background-light);
  color: var(--accent);
}

.bio-text {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.bio-title {
  font-size: 16px;
}

.bio-hint {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary);
}

.zero-knowledge {
  position: relative;
  gap: 12px;
  margin-top: 12px;
  overflow: hidden;
}

/* Pinned to the physical right edge, as on iOS, so RTL keeps the same composition. */
.watermark {
  position: absolute;
  top: -16px;
  right: -36px;
  color: color-mix(in srgb, var(--accent) 14%, transparent);
  pointer-events: none;
}

.zk-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
}

.accent {
  color: var(--accent);
}

.zk-body {
  position: relative;
  margin: 0;
  font-size: 14px;
  color: var(--text-secondary);
}
</style>
