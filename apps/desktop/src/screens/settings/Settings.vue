<script setup lang="ts">
// iOS `SettingsView`: profile, Security (with fingerprint / Hello unlock), Linked devices,
// Appearance, Data & Account,
// with the same rows, order, alerts and status line. The profile card, biometric unlock,
// Autofill, Linked Devices (pairing code, sync network) and Premium arrive in later phases.
import { computed, onMounted, ref } from 'vue'

import { accountApi } from '@/account/api'
import { account, forgetAccount } from '@/account/store'
import { forgetBackup } from '@/backup/store'
import CardContainer from '@/components/CardContainer.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import SettingsRow from '@/components/SettingsRow.vue'
import SettingsSection from '@/components/SettingsSection.vue'
import PageFrame from '@/components/PageFrame.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import ToggleSwitch from '@/components/ToggleSwitch.vue'
import { LANGUAGE_NAMES } from '@/i18n'
import { openSubPage } from '@/nav'
import { prefs, useT } from '@/prefs'
import { vaultApi } from '@/vault/api'
import { loadVault, session } from '@/vault/session'
import { toolsApi } from '@/vault/tools'
import { checkForUpdate, updater } from '@/updater'
import { isCommandError, type BiometricStatus } from '@/vault/types'
import AppearanceSheet from './AppearanceSheet.vue'
import AutoLockSheet from './AutoLockSheet.vue'
import ChangeMasterPassword from './ChangeMasterPassword.vue'

const emit = defineEmits<{ back: []; lock: [] }>()
const t = useT()

/** Under "Check for updates": the result of the last check the user asked for. */
const updateStatus = computed(() => {
  switch (updater.stage) {
    case 'checking':
      return t('update.checking')
    case 'upToDate':
      return t('update.upToDate')
    case 'failed':
      return t('update.failed')
    case 'available':
      return t('update.availableTitle', updater.next)
    default:
      return undefined
  }
})

function checkNow() {
  // An update already found reopens its prompt.
  if (updater.stage === 'available') updater.dismissed = false
  else checkForUpdate()
}

const showChangeMasterPassword = ref(false)
const showAutoLock = ref(false)
const appearanceSheet = ref<'language' | 'theme' | null>(null)
const showClearDataAlert = ref(false)
const showLogoutConfirm = ref(false)
const showDeleteAccount = ref(false)

// iOS biometric toggle: on means "sealed at the next main-password unlock"; the subtitle
// says which of the three states it is in.
const bio = ref<BiometricStatus | null>(null)
onMounted(async () => {
  bio.value = await vaultApi.biometricStatus().catch(() => null)
})
const bioOn = computed({
  get: () => bio.value?.enabled ?? false,
  set: async (on: boolean) => {
    try {
      bio.value = await vaultApi.setBiometric(on)
    } catch (e) {
      statusMessage.value = () => describe(e)
    }
  },
})
const bioSubtitle = computed(() => {
  if (!bio.value?.enabled) return ''
  if (bio.value.sealed) return t('settings.biometricSealed')
  if (bio.value.kind === 'none') return t('settings.biometricNoGate')
  return t('settings.biometricPending')
})
/** The iOS status line, re-read on render so it follows a language change. */
const statusMessage = ref<(() => string) | null>(null)
const busy = ref(false)

/** iOS `error.localizedDescription` for a core error that has no string of its own. */
function describe(e: unknown): string {
  return isCommandError(e) ? e.message : String(e)
}

// MARK: CSV backup. The core shows the native file dialogs and does the file I/O.

async function performExport() {
  if (busy.value) return
  // iOS checks before offering the file; skip the save dialog when nothing can be exported.
  if (session.items.length === 0) {
    statusMessage.value = () => t('settings.exportEmpty')
    return
  }
  busy.value = true
  try {
    const result = await toolsApi.exportCsv()
    if (result.cancelled) return
    if (result.exported === 0) statusMessage.value = () => t('settings.exportEmpty')
    // Do not imply a complete backup when some rows could not be decrypted.
    else if (result.failures > 0) statusMessage.value = () => t('settings.exportPartial', result.exported, result.failures)
    else statusMessage.value = () => t('settings.exportPrepared', result.exported)
  } catch (e) {
    statusMessage.value = () => t('settings.exportFailed', describe(e))
  } finally {
    busy.value = false
  }
}

async function performImport() {
  if (busy.value) return
  busy.value = true
  try {
    const result = await toolsApi.importCsv()
    if (result.cancelled) return
    // Items with fields to check are imported anyway; say how many (core `insert_many`).
    const flagged = () => (result.flagged > 0 ? ` ${t('validation.importFlaggedFmt', result.flagged)}` : '')
    if (result.imported === 0 && result.skipped === 0) {
      statusMessage.value = () => t('settings.importNothing')
    } else if (result.skipped > 0) {
      statusMessage.value = () => t('settings.importSkipped', result.imported, result.skipped) + flagged()
    } else {
      statusMessage.value = () => t('settings.parsedCsv', result.imported) + flagged()
    }
    if (result.imported > 0) await loadVault().catch((e) => console.error('Could not reload the vault', e))
  } catch (e) {
    // iOS wraps the parse error's own description in `importFailed`.
    const reason = () => {
      if (isCommandError(e) && e.code === 'invalid' && e.message === 'empty') return t('settings.importNothing')
      if (isCommandError(e) && e.code === 'invalid' && e.message === 'invalidHeader') {
        return t('settings.importBadHeader', "'type', 'title'")
      }
      return describe(e)
    }
    statusMessage.value = () => t('settings.importFailed', reason())
  } finally {
    busy.value = false
  }
}

async function clearLocalData() {
  showClearDataAlert.value = false
  try {
    // Unlinks this device on the server (best effort), forgets the account, wipes the vault.
    await accountApi.clearLocal()
    forgetAccount()
    forgetBackup()
    emit('lock')
  } catch (e) {
    statusMessage.value = () => describe(e)
  }
}

/** `DELETE /v1/account`, then the local wipe; the root then shows Create vault. */
async function deleteAccount() {
  showDeleteAccount.value = false
  busy.value = true
  try {
    await accountApi.deleteAccount()
    forgetAccount()
    forgetBackup()
    emit('lock')
  } catch {
    statusMessage.value = () => t('settings.deleteAccountFailed')
  } finally {
    busy.value = false
  }
}

function logout() {
  showLogoutConfirm.value = false
  emit('lock')
}
</script>

<template>
  <PageFrame :title="t('settings.title')" back @back="emit('back')">
    <!-- iOS profileCard: 56 avatar ring 12 from the name (16 bold) and the plan capsule (13
         medium, 3 × 8), then Upgrade (primary, crown) or Manage Subscription (secondary) 14 below. -->
    <CardContainer>
      <div class="profile">
        <span class="avatar"><QasaIcon name="person" :size="22" /></span>
        <span class="who">
          <span class="name">{{ t('session.qasaUser') }}</span>
          <span class="plan" :class="{ premium: account.status?.isPremium }">
            <QasaIcon :name="account.status?.isPremium ? 'best-value' : 'person'" :size="10" />
            {{ t(account.status?.isPremium ? 'settings.premiumAccount' : 'settings.freeAccount') }}
          </span>
        </span>
      </div>
      <PrimaryButton
        class="plan-button"
        :kind="account.status?.isPremium ? 'secondary' : 'primary'"
        :icon="account.status?.isPremium ? undefined : 'premium'"
        :title="t(account.status?.isPremium ? 'settings.manageSubscription' : 'settings.upgradePremium')"
        @click="openSubPage(account.status?.isPremium ? 'manageSubscription' : 'premiumUpgrade', 'settings')"
      />
    </CardContainer>

    <SettingsSection :title="t('settings.security')">
        <SettingsRow
          icon="login"
          :title="t('settings.changeMasterPassword')"
          @click="showChangeMasterPassword = true"
        />
        <template v-if="bio && (bio.kind !== 'none' || bio.enabled)">
          <SettingsRow
            :icon="bio.kind === 'windowsHello' ? 'face' : 'fingerprint'"
            :title="t('settings.biometricUnlock')"
            icon-color="var(--text-primary)"
            :subtitle="bioSubtitle || undefined"
          >
            <template #trailing>
              <ToggleSwitch v-model="bioOn" :label="t('settings.biometricUnlock')" :disabled="bio.kind === 'none' && !bio.enabled" />
            </template>
          </SettingsRow>
        </template>
        <SettingsRow
          icon="auto-lock"
          :title="t('settings.autoLock')"
          :trailing-text="t('settings.autoLockMinutesFmt', prefs.autoLockMinutes)"
          @click="showAutoLock = true"
        />
      </SettingsSection>

    <SettingsSection :title="t('settings.linkedDevices')">
        <template #title-trailing><span class="active-count">{{ t('settings.activeCount', account.status?.devicesUsed ?? 1) }}</span></template>
        <SettingsRow
          icon="device"
          :title="t('settings.thisComputer')"
          :subtitle="t('settings.thisDevice')"
          icon-color="var(--accent)"
          @click="openSubPage('linkedDevices', 'settings')"
        />
        <SettingsRow
          icon="wifi"
          :title="t('settings.syncNetwork')"
          :subtitle="account.status?.isPremium ? t('settings.premiumLanSync') : t('settings.requiresPremium')"
          @click="openSubPage('wifiSync', 'settings')"
        />
        <SettingsRow icon="link" :title="t('bridge.settingsRow')" @click="openSubPage('browserExtension', 'settings')" />
      </SettingsSection>

    <SettingsSection :title="t('settings.appearance')">
        <SettingsRow
          icon="website"
          :title="t('settings.language')"
          :trailing-text="LANGUAGE_NAMES[prefs.language]"
          aria-haspopup="dialog"
          @click="appearanceSheet = 'language'"
        />
        <SettingsRow
          icon="theme-dark"
          :title="t('settings.theme')"
          :trailing-text="t(`settings.theme.${prefs.theme}`)"
          aria-haspopup="dialog"
          @click="appearanceSheet = 'theme'"
        />
      </SettingsSection>

    <SettingsSection :title="t('settings.dataAccount')">
        <SettingsRow
          icon="backup-done"
          :title="t('backup.title')"
          :subtitle="t('backup.subtitle')"
          @click="openSubPage('backups', 'settings')"
        />
        <SettingsRow icon="import" :title="t('settings.importCsv')" :disabled="busy" @click="performImport" />
        <SettingsRow icon="export" :title="t('settings.exportCsv')" :disabled="busy" @click="performExport" />
        <SettingsRow
          icon="delete-forever"
          :title="t('settings.clearLocalData')"
          destructive
          @click="showClearDataAlert = true"
        />
        <SettingsRow icon="sign-out" :title="t('settings.logout')" @click="showLogoutConfirm = true" />
        <SettingsRow
          icon="device-remove"
          :title="t('settings.deleteAccount')"
          destructive
          :disabled="busy || !account.status?.accountId"
          @click="showDeleteAccount = true"
        />
      </SettingsSection>

    <SettingsSection :title="t('update.section')">
        <SettingsRow
          icon="refresh"
          :title="t('update.check')"
          :subtitle="updateStatus"
          :trailing-text="updater.current ? t('update.version', updater.current) : undefined"
          :disabled="updater.stage === 'checking' || updater.stage === 'downloading'"
          @click="checkNow"
        />
      </SettingsSection>

    <p v-if="statusMessage" class="status" role="status">{{ statusMessage() }}</p>

    <template #overlay>
      <ChangeMasterPassword :open="showChangeMasterPassword" @close="showChangeMasterPassword = false" />
      <AutoLockSheet :open="showAutoLock" @close="showAutoLock = false" />
      <AppearanceSheet :kind="appearanceSheet" @close="appearanceSheet = null" />
      <ConfirmDialog
        :open="showClearDataAlert"
        :title="t('settings.clearAlertTitle')"
        icon="delete-forever"
        :message="t('settings.clearAlertMessage')"
        :cancel-label="t('settings.cancel')"
        :confirm-label="t('settings.clear')"
        destructive
        @confirm="clearLocalData"
        @cancel="showClearDataAlert = false"
      />
      <ConfirmDialog
        :open="showDeleteAccount"
        :title="t('settings.deleteAccountTitle')"
        icon="device-remove"
        :message="t('settings.deleteAccountMessage')"
        :confirm-label="t('settings.deleteAccount')"
        destructive
        @confirm="deleteAccount"
        @cancel="showDeleteAccount = false"
      />
      <ConfirmDialog
        :open="showLogoutConfirm"
        :title="t('settings.lockAlertTitle')"
        icon="sign-out"
        :message="t('settings.lockAlertMessage')"
        :cancel-label="t('settings.cancel')"
        :confirm-label="t('settings.logout')"
        destructive
        @confirm="logout"
        @cancel="showLogoutConfirm = false"
      />
    </template>
  </PageFrame>
</template>

<style scoped>
.profile {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 56px;
  height: 56px;
  border: 2px solid var(--accent);
  border-radius: 50%;
  color: var(--text-secondary);
}

.avatar svg {
  width: 22px;
  height: 22px;
}

.who {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.name {
  font-size: var(--type-field-size);
  font-weight: 700;
}

.plan {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: var(--size-stroke) solid var(--stroke);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
}

.plan svg {
  width: 10px;
  height: 10px;
}

.plan.premium {
  border-color: var(--accent);
  color: var(--accent);
}

.plan-button {
  margin-top: 14px;
}

/* iOS: `Font.qasaLabel(12)` (13 medium), accent. */
.active-count {
  font-size: var(--type-pill-size);
  font-weight: var(--type-pill-weight);
  color: var(--accent);
}

.status {
  margin: 0;
  font-size: var(--type-caption-size);
  color: var(--accent);
}
</style>
