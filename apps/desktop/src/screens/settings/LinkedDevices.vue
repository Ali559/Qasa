<script setup lang="ts">
// iOS `LinkedDevicesView`: tier pill in the header, the Free upgrade hint, the
// device-limit banner, Active Devices (one card per device), Unlink all other devices
// (Premium), and the info note, with the same three alerts.
// Not here yet: the pairing code and "Sync on this network" rows (Wi‑Fi pairing, phase 4).
// Signing out or unlinking THIS computer is "Clear local data" on desktop (server unlink
// + local wipe), so it is handed to the parent as `logout` instead of done here.
import { Info, Link2Off, Star } from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'

import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageFrame from '@/components/PageFrame.vue'
import StatusPill from '@/components/StatusPill.vue'
import { accountApi } from '@/account/api'
import { account, isPremium, refreshAccount } from '@/account/store'
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
      <span class="star"><Star :size="18" fill="currentColor" /></span>
      <span class="hint-text">
        <strong>{{ t('devices.needMore') }}</strong>
        <span>{{ t('devices.upgradeHint') }}</span>
      </span>
    </button>

    <div v-if="limitError" class="limit-banner" role="alert">
      <strong>{{ t('devices.limitReached') }}</strong>
      <span v-if="limitError.message">{{ limitError.message }}</span>
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

      <button v-if="canUnlinkOthers" type="button" class="unlink-all" :disabled="isActing" @click="showUnlinkAllConfirm = true">
        <Link2Off :size="16" />{{ t('devices.unlinkAllOthers') }}
      </button>
    </section>

    <div class="info-note">
      <Info class="info-icon" :size="18" />
      <span>{{ t('devices.infoNote') }}</span>
    </div>

    <template #overlay>
      <ConfirmDialog
        :open="!!pendingUnlink"
        :title="t('devices.unlinkTitle')"
        :message="pendingUnlink ? t('devices.unlinkMessage', pendingUnlink.device_name) : undefined"
        :confirm-label="t('devices.unlink')"
        destructive
        @confirm="pendingUnlink && unlink(pendingUnlink)"
        @cancel="pendingUnlink = null"
      />
      <ConfirmDialog
        :open="!!pendingLogout"
        :title="t('devices.logoutTitle')"
        :message="pendingLogout ? t('devices.logoutMessage', pendingLogout.device_name) : undefined"
        :confirm-label="t('devices.logout')"
        destructive
        @confirm="pendingLogout && unlink(pendingLogout)"
        @cancel="pendingLogout = null"
      />
      <ConfirmDialog
        :open="showUnlinkAllConfirm"
        :title="t('devices.unlinkAllTitle')"
        :message="t('devices.unlinkAllMessage')"
        :confirm-label="t('devices.unlinkAllOthers')"
        destructive
        @confirm="unlinkOthers"
        @cancel="showUnlinkAllConfirm = false"
      />
    </template>
  </PageFrame>
</template>

<style scoped>
.upgrade-hint {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 21px;
  border: 1px solid color-mix(in srgb, var(--gold) 20%, transparent);
  border-radius: var(--corner-radius);
  background: linear-gradient(
    to bottom right,
    color-mix(in srgb, var(--gold) 10%, transparent),
    color-mix(in srgb, var(--gold) 5%, transparent)
  );
  text-align: start;
  cursor: pointer;
}

.star {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--gold) 20%, transparent);
  color: var(--gold);
}

.hint-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.hint-text strong {
  font-size: 16px;
  font-weight: 700;
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
  border: 1px solid color-mix(in srgb, var(--danger) 40%, transparent);
  border-radius: var(--corner-radius);
  background: color-mix(in srgb, var(--danger-background) 20%, transparent);
}

.limit-banner strong {
  font-size: 16px;
  font-weight: 700;
  color: var(--danger);
}

.devices {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.heading h2 {
  margin: 0;
  font-size: 20px;
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

.unlink-all {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--danger) 45%, transparent);
  border-radius: 8px;
  background: transparent;
  color: var(--danger);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.unlink-all:hover:not(:disabled) {
  background: color-mix(in srgb, var(--danger) 6%, transparent);
}

.unlink-all:disabled {
  opacity: 0.6;
  cursor: default;
}

.info-note {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 8px;
  padding: 17px;
  border: 1px solid var(--stroke-chrome);
  border-radius: 8px;
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
  border: 2px solid color-mix(in srgb, var(--accent) 25%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 900ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation-duration: 2.4s;
  }
}
</style>
