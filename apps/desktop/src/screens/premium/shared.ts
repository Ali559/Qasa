// What the two Premium screens share: iOS `PaymentService.pollUntilComplete`, the plan and
// gateway names, and locale formatting of amounts and dates.
import { accountApi } from '@/account/api'
import { isApiError } from '@/account/types'
import type { PricingPlan, Subscription } from '@/account/types'
import { prefs, useT } from '@/prefs'

/**
 * `t` with an English fallback for desktop strings that may not be in the catalog yet
 * (the runtime returns the key itself when a string is missing).
 */
export function useTr() {
  const t = useT()
  return (key: string, fallback: string, ...args: Array<string | number>) => {
    const text = t(key, ...args)
    return text === key ? fallback.replace(/\{(\d+)\}/g, (m, i: string) => String(args[Number(i)] ?? m)) : text
  }
}

/** iOS `amount.formatted(.number.grouping(.automatic).locale(…))`. */
export function formatIqd(amount: number): string {
  try {
    return new Intl.NumberFormat(prefs.language).format(amount)
  } catch {
    return new Intl.NumberFormat('en').format(amount)
  }
}

/** iOS `Date.FormatStyle(date: .abbreviated, time: .omitted)`; the raw value if unparsable. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  try {
    return new Intl.DateTimeFormat(prefs.language, { dateStyle: 'medium' }).format(date)
  } catch {
    return new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(date)
  }
}

export function isYearly(plan: Pick<PricingPlan, 'interval'>): boolean {
  return plan.interval.toLowerCase().startsWith('year')
}

/** iOS `PricingService.fallbackPlans`, shown until (or if) `/v1/pricing` answers. */
export const FALLBACK_PLANS: PricingPlan[] = [
  { id: 'premium_monthly', title: 'Premium Monthly', interval: 'monthly', amount_iqd: 5_000, days: 30, vpn_quota_gb: 100, active: true, sort_order: 1, features_json: null },
  { id: 'premium_yearly', title: 'Premium Yearly', interval: 'yearly', amount_iqd: 50_000, days: 365, vpn_quota_gb: 100, active: true, sort_order: 2, features_json: null },
]

/** iOS `ManageSubscriptionView.gatewayName`. */
export function gatewayName(t: ReturnType<typeof useT>, gateway: string | null | undefined): string {
  switch (gateway?.toLowerCase()) {
    case 'fib':
      return t('premium.fib')
    case 'fastpay':
      return t('premium.fastpay')
    case 'superqi':
      return t('premium.superqi')
    case 'wayl':
      return 'Wayl'
    default:
      return gateway ?? '—'
  }
}

/** The account error as a sentence (iOS `error.localizedDescription`). */
export function errorText(tr: ReturnType<typeof useTr>, e: unknown): string {
  if (isApiError(e)) {
    switch (e.code) {
      case 'offline':
        return tr('error.offline', "You're offline. Check your connection and try again.")
      case 'unauthorized':
        return tr('error.unauthorized', 'Session expired. Unlock your vault again.')
      case 'server':
        return e.server ?? tr('error.server', 'Server error ({0}).', e.status)
      case 'decode':
        return tr('error.decoding', 'Unexpected server response.')
      case 'misconfigured':
        return tr('error.apiHostMisconfigured', 'This build cannot reach the Qasa server ({0}). Online features are unavailable.', '—')
      default:
        return tr('premium.paymentFailed', 'Payment did not complete. Try again.')
    }
  }
  return e instanceof Error ? e.message : String(e)
}

export type PollResult = { outcome: 'success' } | { outcome: 'failed' | 'timeout'; error: string | null }

/**
 * iOS `PaymentService.pollUntilComplete`: check every `intervalMs` until the payment
 * succeeds or fails. The Rust core refreshes the license when it reports success.
 * `cancelled()` stops early when the screen goes away.
 */
export async function pollUntilComplete(
  paymentId: string,
  opts: { attempts?: number; intervalMs?: number; cancelled?: () => boolean; tr: ReturnType<typeof useTr> },
): Promise<PollResult> {
  const { attempts = 30, intervalMs = 2000, cancelled = () => false, tr } = opts
  let lastError: string | null = null
  for (let i = 0; i < attempts; i++) {
    if (cancelled()) return { outcome: 'timeout', error: null }
    try {
      const status = await accountApi.paymentStatus(paymentId)
      const s = status.status.toLowerCase()
      if (s === 'success') return { outcome: 'success' }
      if (s === 'failed') return { outcome: 'failed', error: null }
    } catch (e) {
      lastError = errorText(tr, e)
    }
    if (i < attempts - 1) await new Promise((r) => setTimeout(r, intervalMs))
  }
  return { outcome: 'timeout', error: lastError }
}

/** The subscription inside a `{ subscription }` response, or null when it isn't one. */
export function subscriptionFrom(value: unknown): Subscription | null {
  if (value && typeof value === 'object') {
    if ('subscription' in value) return (value as { subscription: Subscription | null }).subscription
    if ('id' in value && 'plan_id' in value) return value as Subscription
  }
  return null
}
