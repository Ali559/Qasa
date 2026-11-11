<script setup lang="ts">
// iOS `LinkedDevicesView`: tier pill in the header, the Free upgrade hint, the
// device-limit banner, Active Devices (one card per device), Unlink all other devices
// (Premium), and the info note, with the same three alerts.
// Not here yet: the pairing code and "Sync on this network" rows (Wi‑Fi pairing, phase 4).
// Signing out or unlinking THIS computer is "Clear local data" on desktop (server unlink
// + local wipe), so it is handed to the parent as `logout` instead of done here.
import { computed, onMounted, ref, watch } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageFrame from '@/components/PageFrame.vue'
import QasaIcon from '@/components/QasaIcon.vue'
import StatusPill from '@/components/StatusPill.vue'
import { accountApi } from '@/account/api'
import { account, isPremium, offerReplace, refreshAccount } from '@/account/store'
import { isApiError, type RemoteDevice } from '@/account/types'
import { openPage } from '@/nav'
import { useT } from '@/prefs'
import DeviceCard from './DeviceCard.vue'

const emit = defineEmits<{ back: []; logout: [] }>()
const t = useT()

const devices = ref<RemoteDevice[]>([])
const isLoading = ref(false)
const isActing = ref(false)
/** Re-read on render so it follows a language change. */
const errorMessage = ref<(() => string) | null>(null)
const pendingUnlink = ref<RemoteDevice | null>(null)
const pendingLogout = ref<RemoteDevice | null>(null)
const showUnlinkAllConfirm = ref(false)

const premium = computed(() => isPremium())
const currentDeviceId = computed(() => account.status?.deviceId ?? null)
const limitError = computed(() => account.status?.deviceLimitError ?? null)
/** A restored backup's key: replace its device, or why it can't (`null`: no key sent). */
const restoreKeyStatus = computed(() => (limitError.value ? (account.status?.restoreKeyStatus ?? null) : null))

/** iOS `tierLabel`: "Free: 1/1 Used", with the tier capitalized as `LicenseManager.tierLabel`. */
const tierLabel = computed(() => {
  const tier = account.status?.tier || 'free'
  const label = tier.charAt(0).toUpperCase() + tier.slice(1)
  const used = Math.max(devices.value.length, account.status?.devicesUsed ?? 0)
  return t('devices.tierUsed', label, used, account.status?.deviceLimit ?? 1)
})

const canUnlinkOthers = computed(() => premium.value && devices.value.some((d) => !d.is_primary))

function isPrimaryBlocked(d: RemoteDevice): boolean {
  return d.is_primary === true && devices.value.length > 1
}

/** iOS `error.localizedDescription` for the account errors. */
function describe(e: unknown): () => string {
  if (!isApiError(e)) return () => String(e)
  switch (e.code) {
    case 'unauthorized':
      return () => t('devices.sessionExpired')
    case 'offline':
      return () => t('error.offline')
    case 'deviceLimit':
      return () => t('devices.limitReached')
    case 'server':
      return () => e.server ?? t('error.server', e.status)
    case 'decode':
      return () => t('error.decoding')
    case 'misconfigured':
      return () => t('error.invalidURL')
  }
}

async function loadDevices() {
  isLoading.value = true
  try {
    devices.value = await accountApi.devices()
    errorMessage.value = null
  } catch (e) {
    // Not registered (device limit): the 403 body still lists the devices to unlink.
    if (limitError.value?.devices) devices.value = limitError.value.devices
    errorMessage.value = describe(e)
  } finally {
    isLoading.value = false
  }
}

onMounted(loadDevices)

// "Unlink and continue" registered this computer: show the new device list.
watch(limitError, (now, before) => {
  if (before && !now) loadDevices()
})

function requestUnlink(d: RemoteDevice) {
  if (d.id === currentDeviceId.value) emit('logout')
  else pendingUnlink.value = d
}

function requestLogout(d: RemoteDevice) {
  if (d.id === currentDeviceId.value) emit('logout')
  else pendingLogout.value = d
}

/** Unlink and remote sign-out are the same server call (the device's license is revoked). */
async function unlink(d: RemoteDevice) {
  pendingUnlink.value = null
  pendingLogout.value = null
  isActing.value = true
  try {
    await accountApi.unlinkDevice(d.id)
    await loadDevices()
    // A slot was freed: retry registration / pick up the new count.
    await refreshAccount().catch(() => {})
  } catch (e) {
    errorMessage.value = describe(e)
  } finally {
    isActing.value = false
  }
}

async function unlinkOthers() {
  showUnlinkAllConfirm.value = false
  isActing.value = true
  try {
    devices.value = await accountApi.unlinkOthers()
    errorMessage.value = null
    await refreshAccount().catch(() => {})
  } catch (e) {
    errorMessage.value = describe(e)
  } finally {
    isActing.value = false
  }
}

function upgrade() {
  openPage('premiumUpgrade')
}
</script>

<template>
  <PageFrame :title="t('devices.title')" back @back="emit('back')">
    <template #header-trailing>
      <StatusPill :text="tierLabel" :dot-color="premium ? 'var(--success)' : 'var(--gold-muted)'" />
    </template>

    <button v-if="!premium" type="button" class="upgrade-hint" @click="upgrade">
      <span class="star"><QasaIcon name="favorite" :size="16" /></span>
      <span class="hint-text">
        <strong>{{ t('devices.needMore') }}</strong>
        <span>{{ t('devices.upgradeHint') }}</span>
      </span>
    </button>

    <div v-if="limitError" class="limit-banner" role="alert">
      <strong>{{ t('devices.limitReached') }}</strong>
      <span v-if="limitError.message">{{ limitError.message }}</span>
      <span v-if="restoreKeyStatus === 'unknown'">{{ t('backup.replace.unknown') }}</span>
      <span v-else-if="restoreKeyStatus === 'not_enough'">{{ t('backup.replace.notEnough') }}</span>
      <button
        v-else-if="restoreKeyStatus === 'replaceable'"
        type="button"
        class="qasa-button secondary replace"
        :disabled="isActing || account.replace.busy"
        @click="offerReplace"
      >
        <QasaIcon name="sync" :size="15" /><span>{{ t('backup.replace.button') }}</span>
      </button>
    </div>

    <section class="devices">
      <div class="heading">
        <h2>{{ t('devices.activeDevices') }}</h2>
        <span v-if="isLoading || isActing" class="spinner" aria-hidden="true" />
      </div>

      <p v-if="errorMessage" class="error" role="alert">{{ errorMessage() }}</p>
      <p v-if="devices.length === 0 && !isLoading" class="none">{{ t('devices.noneLinked') }}</p>

      <DeviceCard
        v-for="d in devices"
        :key="d.id"
        :device="d"
        :is-current="d.id === currentDeviceId"
        :premium="premium"
        :primary-blocked="isPrimaryBlocked(d)"
        :busy="isActing"
        @upgrade="upgrade"
        @logout="requestLogout(d)"
        @unlink="requestUnlink(d)"
        @blocked="errorMessage = () => t('devices.cannotUnlinkPrimary')"
      />

      <button v-if="canUnlinkOthers" type="button" class="qasa-button destructive" :disabled="isActing" @click="showUnlinkAllConfirm = true">
        <QasaIcon name="unlink" :size="15" /><span>{{ t('devices.unlinkAllOthers') }}</span>
      </button>
    </section>

    <div class="info-note">
      <QasaIcon name="info" :size="18" class="info-icon" />
      <span>{{ t('devices.infoNote') }}</span>
    </div>

    <template #overlay>
      <ConfirmDialog
        :open="!!pendingUnlink"
        :title="t('devices.unlinkTitle')"
        icon="link"
        :message="pendingUnlink ? t('devices.unlinkMessage', pendingUnlink.device_name) : undefined"
        :confirm-label="t('devices.unlink')"
        :cancel-label="t('settings.cancel')"
        destructive
        @confirm="pendingUnlink && unlink(pendingUnlink)"
        @cancel="pendingUnlink = null"
      />
      <ConfirmDialog
        :open="!!pendingLogout"
        :title="t('devices.logoutTitle')"
        icon="sign-out"
        :message="pendingLogout ? t('devices.logoutMessage', pendingLogout.device_name) : undefined"
        :confirm-label="t('devices.logout')"
        :cancel-label="t('settings.cancel')"
        destructive
        @confirm="pendingLogout && unlink(pendingLogout)"
        @cancel="pendingLogout = null"
      />
      <ConfirmDialog
        :open="showUnlinkAllConfirm"
        :title="t('devices.unlinkAllTitle')"
        icon="unlink"
        :message="t('devices.unlinkAllMessage')"
        :confirm-label="t('devices.unlinkAllOthers')"
        :cancel-label="t('settings.cancel')"
        destructive
        @confirm="unlinkOthers"
        @cancel="showUnlinkAllConfirm = false"
      />
    </template>
  </PageFrame>
</template>

<style scoped>
/* iOS `upgradeHintBanner`: gold wash, 21 padding, star `IconBadge` 16 from the text. */
.upgrade-hint {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 21px;
  border: var(--size-stroke) solid color-mix(in srgb, var(--gold) 20%, transparent);
  border-radius: var(--radius-card);
  background: linear-gradient(
    to bottom right,
    color-mix(in srgb, var(--gold) 10%, transparent),
    color-mix(in srgb, var(--gold) 5%, transparent)
  );
  text-align: start;
  cursor: pointer;
  transition:
    opacity var(--motion-press),
    transform var(--motion-press);
}

.upgrade-hint:active {
  opacity: var(--motion-pressed-opacity);
  transform: scale(var(--motion-press-scale));
}

.star {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: var(--size-icon-badge);
  height: var(--size-icon-badge);
  border-radius: var(--radius-icon-badge);
  background: color-mix(in srgb, var(--gold) 20%, transparent);
  color: var(--gold);
}

.star svg {
  width: var(--size-icon-badge-glyph);
  height: var(--size-icon-badge-glyph);
}

.hint-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.hint-text strong,
.limit-banner strong {
  font-size: var(--type-field-size);
  font-weight: 700;
}

.hint-text strong {
  color: var(--gold);
}

.hint-text span,
.limit-banner span {
  font-size: 14px;
  color: var(--text-secondary);
}

.limit-banner {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 17px;
  border: var(--size-stroke) solid color-mix(in srgb, var(--danger) 40%, transparent);
  border-radius: var(--radius-card);
  background: color-mix(in srgb, var(--danger-background) 20%, transparent);
}

.replace {
  margin-top: 4px;
}

.limit-banner strong {
  color: var(--danger);
}

.devices {
  display: flex;
  flex-direction: column;
  gap: var(--space-form-gap);
}

.heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.heading h2 {
  margin: 0;
  font-size: var(--type-page-title-size);
  font-weight: 600;
  color: var(--text-primary);
}

.error,
.none {
  margin: 0;
  font-size: 14px;
}

.error {
  color: var(--danger);
}

.none {
  color: var(--text-secondary);
}

/* iOS `infoNote`: muted fill, strokeChrome, control radius, 17 padding, 8 above. */
.info-note {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 8px;
  padding: 17px;
  border: var(--size-stroke) solid var(--stroke-chrome);
  border-radius: var(--radius-control);
  background: var(--card-muted);
  font-size: 14px;
  color: var(--text-secondary);
}

.info-icon {
  flex-shrink: 0;
  color: var(--info);
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid var(--text-tertiary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
