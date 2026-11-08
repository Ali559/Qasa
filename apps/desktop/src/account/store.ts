// The account state screens share: plan, device slots, unread inbox count. Mirrors iOS
// `AppSession` (isPremium, displayName) + `NotificationService.unreadCount`.
import { reactive, watch } from 'vue'

import { prefs } from '@/prefs'
import { accountApi } from './api'
import type { AccountStatus } from './types'

export const account = reactive({
  status: null as AccountStatus | null,
  unread: 0,
  bootstrapping: false,
})

export function isPremium(): boolean {
  return account.status?.isPremium ?? false
}

/** iOS `AppBootstrap.onVaultUnlocked`, after create or unlock. Never blocks the vault. */
export async function bootstrapAccount(): Promise<void> {
  account.bootstrapping = true
  try {
    account.status = await accountApi.bootstrap(prefs.language)
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
}

// iOS `AppPreferences.language didSet → syncLocaleToServer`.
watch(
  () => prefs.language,
  (language) => {
    if (account.status?.accountId) accountApi.syncLocale(language).catch(() => {})
  },
)
