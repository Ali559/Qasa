<script setup lang="ts">
// iOS `CreateVaultView`. The biometrics card shows when the computer has Touch ID or
// Windows Hello; "I already have Qasa on another device" joins a vault over Wi‑Fi.
import { Fingerprint, KeyRound, Lock, ScanFace, Shield } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import AuthFrame from '@/components/AuthFrame.vue'
import CardContainer from '@/components/CardContainer.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import FieldGroup from '@/components/FieldGroup.vue'
import FieldRow from '@/components/FieldRow.vue'
import StatusPill from '@/components/StatusPill.vue'
import StrengthBar from '@/components/StrengthBar.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { isCommandError, type BiometricStatus } from '@/vault/types'

const emit = defineEmits<{ created: []; pair: []; restore: [] }>()
const t = useT()

const password = ref('')
const confirm = ref('')
/** Form-wide failures; a problem with one field goes on its row. */
const error = ref<string | null>(null)
const passwordError = ref<string | null>(null)
const confirmError = ref<string | null>(null)
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
  error.value = null
  passwordError.value = null
  confirmError.value = null
  if (!canCreate.value) {
    if (!password.value) passwordError.value = t('auth.create.enterPassword')
    else if (length.value < 8) passwordError.value = t('settings.weakMasterPassword')
    else confirmError.value = t('auth.create.mismatch')
    return
  }
  busy.value = true
  try {
    await vaultApi.create(password.value, biometrics.value && bio.value?.kind !== 'none' && !!bio.value)
    password.value = ''
    confirm.value = ''
    emit('created')
  } catch (e) {
    if (isCommandError(e) && e.code === 'weakPassword') passwordError.value = t('settings.weakMasterPassword')
    else error.value = t('error.vaultCreateTitle')
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
        <button type="button" class="pair restore" @click="emit('restore')">
          <span>{{ t('backup.restore') }}</span>
          <span class="pair-subtitle">{{ t('backup.restoreSubtitle') }}</span>
        </button>
      </div>

      <div class="group">
        <FieldGroup>
          <FieldRow v-model="password" :label="t('auth.unlock.masterPassword')" secure revealable autofocus :error="passwordError" @submit="create" />
          <FieldRow v-model="confirm" :label="t('auth.create.confirmPassword')" secure :error="confirmError" @submit="create" />
        </FieldGroup>
        <div v-if="password" class="strength">
          <div class="strength-row">
            <span>{{ t('auth.create.strength') }}</span>
            <span class="strength-label">{{ strength.label }}</span>
          </div>
          <StrengthBar :segments="3" :filled="strength.filled" :color="strength.color" />
        </div>
      </div>

      <p v-if="error" class="error" role="alert">{{ error }}</p>

      <CardContainer v-if="bio && bio.kind !== 'none'" class="biometric settings-list">
        <SettingsRow
          :icon="bio.kind === 'windowsHello' ? ScanFace : Fingerprint"
          :title="t('auth.create.enableBiometrics')"
          :subtitle="t('auth.create.biometricsHint')"
        >
          <template #trailing>
            <ToggleSwitch v-model="biometrics" :label="t('auth.create.enableBiometrics')" />
          </template>
        </SettingsRow>
      </CardContainer>

      <CardContainer muted class="zero-knowledge">
        <Shield class="watermark" :size="88" fill="currentColor" aria-hidden="true" />
        <div class="zk-title">
          <Lock :size="17" class="accent" aria-hidden="true" />
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
  font-weight: 400;
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

.restore {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--space-row-text-gap);
  text-align: start;
}

.pair-subtitle {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  color: var(--text-secondary);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
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
  font-weight: 400;
  color: var(--text-secondary);
}
</style>
