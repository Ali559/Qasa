// Account calls into the Rust core (src-tauri/src/account/commands.rs). The license token
// never reaches the webview.
import { invoke } from '@tauri-apps/api/core'

import type { AccountStatus, InboxNotification, PaymentIntent, PaymentStatus, Pricing, RemoteDevice, Subscription } from './types'

const tauriAccountApi = {
  status: () => invoke<AccountStatus>('account_status'),
  bootstrap: (locale: string) => invoke<AccountStatus>('account_bootstrap', { locale }),
  refreshLicense: () => invoke<AccountStatus>('account_refresh_license'),
  syncLocale: (locale: string) => invoke<void>('account_sync_locale', { locale }),

  devices: () => invoke<RemoteDevice[]>('devices_list'),
  unlinkDevice: (id: string) => invoke<void>('device_unlink', { id }),
  unlinkOthers: () => invoke<RemoteDevice[]>('devices_unlink_others'),
  clearLocal: () => invoke<void>('account_clear_local'),
  deleteAccount: () => invoke<unknown>('account_delete'),

  pricing: () => invoke<Pricing>('pricing_get'),
  createIntent: (gateway: string, planId: string, amountIqd: number, autoRenew: boolean) =>
    invoke<PaymentIntent>('payment_create_intent', { gateway, planId, amountIqd, autoRenew }),
  paymentStatus: (id: string) => invoke<PaymentStatus>('payment_status', { id }),
  simulateStub: (gateway: string, orderId: string) => invoke<void>('payment_simulate_stub', { gateway, orderId }),
  subscription: () => invoke<Subscription | null>('subscription_get'),
  setAutoRenew: (autoRenew: boolean, subscriptionId: string | null) =>
    invoke<unknown>('subscription_set_auto_renew', { autoRenew, subscriptionId }),
  cancelSubscription: (reason: string | null, subscriptionId: string | null) =>
    invoke<unknown>('subscription_cancel', { reason, subscriptionId }),
  removePaymentMethod: () => invoke<unknown>('payment_method_remove'),

  notifications: (before?: string) =>
    invoke<{ notifications: InboxNotification[]; unread_count?: number; next_before?: string | null }>('notifications_list', {
      before: before ?? null,
    }),
  unreadCount: () => invoke<number>('notifications_unread_count'),
  openNotification: (id: string) => invoke<InboxNotification>('notification_open', { id }),
  readAll: () => invoke<unknown>('notifications_read_all'),
  deleteNotification: (id: string) => invoke<void>('notification_delete', { id }),
}

// Browser-only stand-in (see vault/mock.ts): a Free account with a small inbox and the
// two production plans, so the account screens can be designed without the server.
const now = Date.now()
const mockNotifications: InboxNotification[] = [
  { id: 'n1', title_en: 'Welcome to Qasa', title_ckb: null, title_ar: null, body_en: 'Your vault is ready.', body_ckb: null, body_ar: null, created_at: new Date(now - 3_600_000).toISOString(), read_at: null },
  { id: 'n2', title_en: 'Premium ends in 3 days', title_ckb: null, title_ar: null, body_en: 'Renew to keep the breach check and VPN.', body_ckb: null, body_ar: null, created_at: new Date(now - 86_400_000 * 2).toISOString(), read_at: new Date().toISOString() },
]
let mockPremium = false
const mockStatus = (): AccountStatus => ({
  accountId: '6f1c2a5e-0000-4000-8000-000000000000', deviceId: 'd1', isPremium: mockPremium, tier: mockPremium ? 'premium' : 'free',
  deviceLimit: mockPremium ? 5 : 1, devicesUsed: 1, license: null, deviceLimitError: null, offline: false, misconfigured: false,
})
const mockAccountApi: typeof tauriAccountApi = {
  status: async () => mockStatus(),
  bootstrap: async () => mockStatus(),
  refreshLicense: async () => mockStatus(),
  syncLocale: async () => {},
  devices: async () => [
    { id: 'd1', device_name: 'MacBook Pro', platform: 'macos', location: null, created_at: new Date(now - 86_400_000 * 30).toISOString(), last_seen_at: new Date().toISOString(), is_primary: true },
    { id: 'd2', device_name: 'iPhone 17', platform: 'ios', location: 'Iraq', created_at: new Date(now - 86_400_000 * 10).toISOString(), last_seen_at: new Date(now - 3_600_000).toISOString(), is_primary: false },
  ],
  unlinkDevice: async () => {},
  unlinkOthers: async () => [],
  clearLocal: async () => {},
  deleteAccount: async () => ({}),
  pricing: async () => ({
    plans: [
      { id: 'premium_monthly', title: 'Premium Monthly', interval: 'monthly', amount_iqd: 5000, days: 30, vpn_quota_gb: 100, active: true, sort_order: 1, features_json: null },
      { id: 'premium_yearly', title: 'Premium Yearly', interval: 'yearly', amount_iqd: 50000, days: 365, vpn_quota_gb: 100, active: true, sort_order: 2, features_json: null },
    ],
    auto_renew: { fib: 'link', fastpay: 'link', superqi: 'link' },
  }),
  createIntent: async (gateway, planId, amount) => ({ payment_id: 'p1', gateway, gateway_order_id: 'o1', deep_link: 'https://example.invalid/pay', amount_iqd: amount, plan_id: planId, auto_renew: false, status: 'pending', kind: 'checkout' }),
  paymentStatus: async () => {
    mockPremium = true
    return { payment_id: 'p1', status: 'success', gateway: 'fib', gateway_order_id: 'o1', amount_iqd: 5000, completed_at: new Date().toISOString() }
  },
  simulateStub: async () => {},
  subscription: async () => null,
  setAutoRenew: async () => ({}),
  cancelSubscription: async () => ({}),
  removePaymentMethod: async () => ({}),
  notifications: async () => ({ notifications: mockNotifications, unread_count: 1, next_before: null }),
  unreadCount: async () => mockNotifications.filter((n) => !n.read_at).length,
  openNotification: async (id) => {
    const n = mockNotifications.find((x) => x.id === id)!
    n.read_at = new Date().toISOString()
    return n
  },
  readAll: async () => mockNotifications.forEach((n) => (n.read_at ??= new Date().toISOString())),
  deleteNotification: async (id) => {
    const i = mockNotifications.findIndex((n) => n.id === id)
    if (i >= 0) mockNotifications.splice(i, 1)
  },
}

export const accountApi: typeof tauriAccountApi = '__TAURI_INTERNALS__' in window ? tauriAccountApi : mockAccountApi
