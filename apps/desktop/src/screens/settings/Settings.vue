<script setup lang="ts">
// iOS `SettingsView`: profile, Security (with fingerprint / Hello unlock), Linked devices,
// Appearance, Data & Account,
// with the same rows, order, alerts and status line. The profile card, biometric unlock,
// Autofill, Linked Devices (pairing code, sync network) and Premium arrive in later phases.
import {
  Clock,
  Crown,
  DatabaseBackup,
  Download,
  Ellipsis,
  Fingerprint,
  Globe,
  Laptop,
  LogOut,
  Moon,
  Puzzle,
  RefreshCw,
  ScanFace,
  Trash2,
  Upload,
  User,
  UserX,
  Wifi,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import { accountApi } from '@/account/api'
import { account, forgetAccount } from '@/account/store'
import { forgetBackup } from '@/backup/store'
import CardContainer from '@/components/CardContainer.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import NavChevronRow from '@/components/NavChevronRow.vue'
import PageFrame from '@/components/PageFrame.vue'
import SectionLabel from '@/components/SectionLabel.vue'
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
    <!-- iOS profileCard: plan badge, then Upgrade or Manage Subscription. -->
    <CardContainer>
      <div class="profile">
        <span class="avatar"><User :size="24" fill="currentColor" /></span>
        <span class="who">
          <span class="name">{{ t('session.qasaUser') }}</span>
          <span class="plan" :class="{ premium: account.status?.isPremium }">
            <Crown v-if="account.status?.isPremium" :size="11" />
            <User v-else :size="11" />
            {{ t(account.status?.isPremium ? 'settings.premiumAccount' : 'settings.freeAccount') }}
          </span>
        </span>
      </div>
      <button
        type="button"
        class="plan-button"
        @click="openSubPage(account.status?.isPremium ? 'manageSubscription' : 'premiumUpgrade', 'settings')"
      >
        {{ t(account.status?.isPremium ? 'settings.manageSubscription' : 'settings.upgradePremium') }}
      </button>
    </CardContainer>

    <section class="group">
      <SectionLabel :text="t('settings.security')" />
      <CardContainer>
        <NavChevronRow
          :icon="Ellipsis"
          :title="t('settings.changeMasterPassword')"
          @click="showChangeMasterPassword = true"
        />
        <template v-if="bio && (bio.kind !== 'none' || bio.enabled)">
          <hr class="divider" />
          <div class="toggle-row">
            <span class="toggle-icon">
              <ScanFace v-if="bio.kind === 'windowsHello'" :size="18" />
              <Fingerprint v-else :size="18" />
            </span>
            <span class="toggle-text">
              <span class="toggle-title">{{ t('settings.biometricUnlock') }}</span>
              <span v-if="bioSubtitle" class="toggle-subtitle">{{ bioSubtitle }}</span>
            </span>
            <ToggleSwitch v-model="bioOn" :label="t('settings.biometricUnlock')" :disabled="bio.kind === 'none' && !bio.enabled" />
          </div>
        </template>
        <hr class="divider" />
        <NavChevronRow
          :icon="Clock"
          :title="t('settings.autoLock')"
          :trailing-text="t('settings.autoLockMinutesFmt', prefs.autoLockMinutes)"
          @click="showAutoLock = true"
        />
      </CardContainer>
    </section>

    <section class="group">
      <div class="label-row">
        <SectionLabel :text="t('settings.linkedDevices')" />
        <span class="active-count">{{ t('settings.activeCount', account.status?.devicesUsed ?? 1) }}</span>
      </div>
      <CardContainer>
        <NavChevronRow
          :icon="Laptop"
          :title="t('settings.thisComputer')"
          :subtitle="t('settings.thisDevice')"
          icon-color="var(--accent)"
          @click="openSubPage('linkedDevices', 'settings')"
        />
        <hr class="divider" />
        <NavChevronRow
          :icon="Wifi"
          :title="t('settings.syncNetwork')"
          :subtitle="account.status?.isPremium ? t('settings.premiumLanSync') : t('settings.requiresPremium')"
          @click="openSubPage('wifiSync', 'settings')"
        />
        <hr class="divider" />
        <NavChevronRow :icon="Puzzle" :title="t('bridge.settingsRow')" @click="openSubPage('browserExtension', 'settings')" />
      </CardContainer>
    </section>

    <section class="group">
      <SectionLabel :text="t('settings.appearance')" />
      <CardContainer>
        <NavChevronRow
          :icon="Globe"
          :title="t('settings.language')"
          :trailing-text="LANGUAGE_NAMES[prefs.language]"
          aria-haspopup="dialog"
          @click="appearanceSheet = 'language'"
        />
        <hr class="divider" />
        <NavChevronRow
          :icon="Moon"
          :title="t('settings.theme')"
          :trailing-text="t(`settings.theme.${prefs.theme}`)"
          aria-haspopup="dialog"
          @click="appearanceSheet = 'theme'"
        />
      </CardContainer>
    </section>

    <section class="group">
      <SectionLabel :text="t('settings.dataAccount')" />
      <CardContainer>
        <NavChevronRow
          :icon="DatabaseBackup"
          :title="t('backup.title')"
          :subtitle="t('backup.subtitle')"
          @click="openSubPage('backups', 'settings')"
        />
        <hr class="divider" />
        <NavChevronRow :icon="Download" :title="t('settings.importCsv')" :disabled="busy" @click="performImport" />
        <hr class="divider" />
        <NavChevronRow :icon="Upload" :title="t('settings.exportCsv')" :disabled="busy" @click="performExport" />
        <hr class="divider" />
        <NavChevronRow
          :icon="Trash2"
          :title="t('settings.clearLocalData')"
          destructive
          @click="showClearDataAlert = true"
        />
        <hr class="divider" />
        <NavChevronRow :icon="LogOut" :title="t('settings.logout')" @click="showLogoutConfirm = true" />
        <hr class="divider" />
        <NavChevronRow
          :icon="UserX"
          :title="t('settings.deleteAccount')"
          destructive
          :disabled="busy || !account.status?.accountId"
          @click="showDeleteAccount = true"
        />
      </CardContainer>
    </section>

    <section class="group">
      <SectionLabel :text="t('update.section')" />
      <CardContainer>
        <NavChevronRow
          :icon="RefreshCw"
          :title="t('update.check')"
          :subtitle="updateStatus"
          :trailing-text="updater.current ? t('update.version', updater.current) : undefined"
          :disabled="updater.stage === 'checking' || updater.stage === 'downloading'"
          @click="checkNow"
        />
      </CardContainer>
    </section>

    <p v-if="statusMessage" class="status" role="status">{{ statusMessage() }}</p>

    <template #overlay>
      <ChangeMasterPassword :open="showChangeMasterPassword" @close="showChangeMasterPassword = false" />
      <AutoLockSheet :open="showAutoLock" @close="showAutoLock = false" />
      <AppearanceSheet :kind="appearanceSheet" @close="appearanceSheet = null" />
      <ConfirmDialog
        :open="showClearDataAlert"
        :title="t('settings.clearAlertTitle')"
        :message="t('settings.clearAlertMessage')"
        :confirm-label="t('settings.clear')"
        destructive
        @confirm="clearLocalData"
        @cancel="showClearDataAlert = false"
      />
      <ConfirmDialog
        :open="showDeleteAccount"
        :title="t('settings.deleteAccountTitle')"
        :message="t('settings.deleteAccountMessage')"
        :confirm-label="t('common.delete')"
        destructive
        @confirm="deleteAccount"
        @cancel="showDeleteAccount = false"
      />
      <ConfirmDialog
        :open="showLogoutConfirm"
        :title="t('settings.lockAlertTitle')"
        :message="t('settings.lockAlertMessage')"
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
  width: 56px;
  height: 56px;
  border: 2px solid var(--accent);
  border-radius: 50%;
  color: var(--text-secondary);
}

.who {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.name {
  font-size: 16px;
  font-weight: 700;
}

.plan {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border: 1px solid var(--stroke);
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.plan.premium {
  border-color: var(--accent);
  color: var(--accent);
}

.plan-button {
  margin-top: 12px;
  padding: 10px;
  border: 0;
  border-radius: 8px;
  background: var(--card-background-light);
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.toggle-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 20%, transparent);
  color: var(--accent);
}

.toggle-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.toggle-title {
  font-size: 16px;
}

.toggle-subtitle {
  font-size: 14px;
  color: var(--text-secondary);
}

.label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.active-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--accent);
}

.group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.divider {
  margin: 0;
  border: 0;
  border-top: 1px solid var(--stroke);
}

.status {
  margin: 0;
  font-size: 12px;
  color: var(--accent);
}
</style>
