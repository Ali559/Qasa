// The account state screens share: plan, device slots, unread inbox count. Mirrors iOS
// `AppSession` (isPremium, displayName) + `NotificationService.unreadCount`.
import { reactive, watch } from 'vue'

import { prefs, useT } from '@/prefs'
import { showToast } from '@/toast'
import { accountApi } from './api'
import type { AccountStatus } from './types'

export const account = reactive({
  status: null as AccountStatus | null,
  unread: 0,
  bootstrapping: false,
  /** The "Replace your old device?" prompt (a restored backup hit the device limit). */
  replace: { open: false, busy: false, failed: false },
})

const t = useT()

/** A registration just took the backup device's place: say so once. */
function announceReplaced(status: AccountStatus): void {
  if (status.replacedDeviceName) showToast(t('backup.replace.doneFmt', status.replacedDeviceName), 5000)
}

/** Offers the replacement when the backup's key allows it (setup restore, Linked devices). */
export function offerReplace(): void {
  if (account.status?.restoreKeyStatus !== 'replaceable') return
  account.replace = { open: true, busy: false, failed: false }
}

export function dismissReplace(): void {
  if (!account.replace.busy) account.replace.open = false
}

/** Confirm → register again with the backup's key and `replace_device`; on success this
 * continues like any registration (license, Premium, devices list follow the status). */
export async function confirmReplace(): Promise<void> {
  if (account.replace.busy) return
  account.replace.busy = true
  account.replace.failed = false
  try {
    const status = await accountApi.replaceDevice(prefs.language)
    account.status = status
    account.replace.open = false
    announceReplaced(status)
    await refreshUnread()
  } catch (e) {
    console.error('Device replacement failed', e)
    account.replace.failed = true
    // The server may have answered with a new limit status (e.g. the key went stale).
    await refreshAccount().catch(() => {})
  } finally {
    account.replace.busy = false
  }
}

export function isPremium(): boolean {
  return account.status?.isPremium ?? false
}

/** iOS `AppBootstrap.onVaultUnlocked`, after create or unlock. Never blocks the vault.
 * `afterRestore`: right after a setup restore, offer replacing the backup's device when
 * the device limit is in the way. */
export async function bootstrapAccount(afterRestore = false): Promise<void> {
  account.bootstrapping = true
  try {
    const status = await accountApi.bootstrap(prefs.language)
    account.status = status
    announceReplaced(status)
    if (afterRestore) offerReplace()
    await refreshUnread()
  } catch (e) {
    console.error('Account bootstrap failed', e)
  } finally {
    account.bootstrapping = false
  }
}

export async function refreshAccount(): Promise<void> {
  account.status = await accountApi.status()
}

export async function refreshLicense(): Promise<void> {
  account.status = await accountApi.refreshLicense()
}

export async function refreshUnread(): Promise<void> {
  try {
    account.unread = await accountApi.unreadCount()
  } catch {
    // Offline: keep the last count.
  }
}

let polling: ReturnType<typeof setInterval> | undefined

/** The inbox has no push on desktop (plan §7): check every 5 minutes while unlocked. */
export function startInboxPolling(): void {
  stopInboxPolling()
  polling = setInterval(refreshUnread, 5 * 60_000)
}

export function stopInboxPolling(): void {
  clearInterval(polling)
}

export function forgetAccount(): void {
  stopInboxPolling()
  account.unread = 0
  account.replace = { open: false, busy: false, failed: false }
}

// iOS `AppPreferences.language didSet → syncLocaleToServer`.
watch(
  () => prefs.language,
  (language) => {
    if (account.status?.accountId) accountApi.syncLocale(language).catch(() => {})
  },
)
