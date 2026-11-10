// Account types as the Rust core and the Qasa API send them (snake_case from the server).

export interface License {
  accountId: string
  deviceId: string
  tier: string
  deviceLimit: number
  expiresAt: number
  entitlements: string[]
}

export interface AccountStatus {
  accountId: string | null
  deviceId: string | null
  isPremium: boolean
  tier: string
  deviceLimit: number
  devicesUsed: number
  license: License | null
  /** The server's 403 body when registration hit the device limit. */
  deviceLimitError: { message?: string; devices_used?: number; device_limit?: number; devices?: RemoteDevice[] } | null
  /** With `deviceLimitError` after restoring a backup: what the backup's restore key can do. */
  restoreKeyStatus: 'unknown' | 'not_enough' | 'replaceable' | null
  /** When replaceable: the name of the device that made the backup. */
  replaceableDeviceName: string | null
  /** Set once, right after this device took the backup device's place. */
  replacedDeviceName: string | null
  offline: boolean
  misconfigured: boolean
}

/** Errors from account commands: switch on `code`. */
export type ApiError =
  | { code: 'offline' }
  | { code: 'unauthorized' }
  | { code: 'deviceLimit'; body: unknown }
  | { code: 'server'; status: number; server: string | null }
  | { code: 'decode' }
  | { code: 'misconfigured' }

export function isApiError(e: unknown): e is ApiError {
  return typeof e === 'object' && e !== null && 'code' in e
}

/** iOS `RemoteDevice`. */
export interface RemoteDevice {
  id: string
  device_name: string
  platform: string
  location: string | null
  created_at: string | null
  last_seen_at: string | null
  is_primary: boolean | null
}

/** iOS `PricingPlanDTO`. */
export interface PricingPlan {
  id: string
  title: string
  interval: string
  amount_iqd: number
  days: number
  vpn_quota_gb: number | null
  active: boolean | null
  sort_order: number | null
  features_json: string[] | null
}

export interface Pricing {
  plans: PricingPlan[]
  /** Per wallet: "charge", "link" or "none" (what auto-renew will do). */
  auto_renew?: Record<string, string>
}

export interface PaymentIntent {
  payment_id: string
  gateway: string
  gateway_order_id: string
  deep_link: string
  amount_iqd: number
  plan_id: string | null
  auto_renew: boolean | null
  status: string
  kind: string | null
}

export interface PaymentStatus {
  payment_id: string
  status: string
  gateway: string
  gateway_order_id: string
  amount_iqd: number
  completed_at: string | null
}

export interface Renewal {
  mode: string | null
  will_renew: boolean
  gateway: string | null
  amount_iqd: number | null
  link: { payment_id: string; url: string; amount_iqd: number; pay_by: string } | null
}

/** iOS `SubscriptionDTO`. */
export interface Subscription {
  id: string
  status: string
  plan_id: string
  amount_iqd: number
  auto_renew: boolean
  started_at: string
  expires_at: string
  cancelled_at: string | null
  cancellation_reason: string | null
  renewal: Renewal | null
}

/** iOS `AppNotificationDTO`. */
export interface InboxNotification {
  id: string
  title_en: string
  title_ckb: string | null
  title_ar: string | null
  body_en: string
  body_ckb: string | null
  body_ar: string | null
  created_at: string
  read_at: string | null
}

export function localized(n: InboxNotification, language: string): { title: string; body: string } {
  if (language === 'ckb') return { title: n.title_ckb ?? n.title_en, body: n.body_ckb ?? n.body_en }
  if (language === 'ar') return { title: n.title_ar ?? n.title_en, body: n.body_ar ?? n.body_en }
  return { title: n.title_en, body: n.body_en }
}
