// What the two Premium screens share: iOS `PaymentService.pollUntilComplete`, the plan and
// gateway names, and locale formatting of amounts and dates.
import { accountApi } from '@/account/api'
import { isApiError } from '@/account/types'
import type { PricingPlan, Subscription } from '@/account/types'
import fastpayLogo from '@/assets/payment-logos/fastpay-icon.png'
import fibLogo from '@/assets/payment-logos/fib-icon.png'
import mastercardLogo from '@/assets/payment-logos/mastercard.svg'
import superqiLogo from '@/assets/payment-logos/superqi-icon.png'
import visaLogo from '@/assets/payment-logos/visa.svg'
import { formatLocale, prefs, useT } from '@/prefs'

/** iOS `amount.formatted(.number.grouping(.automatic).locale(…))`. */
export function formatIqd(amount: number): string {
  try {
    return new Intl.NumberFormat(formatLocale()).format(amount)
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

export type PaymentMethodId = 'card' | 'fib' | 'fastpay' | 'superqi'

/** The upgrade page's payment rows, in order (the first is the default). The choice is
 *  UI only: every row pays through Wayl's hosted page, so the gateway is always "wayl". */
export const PAYMENT_METHODS: { id: PaymentMethodId; shortName: string; logos: string[]; gateway: string }[] = [
  { id: 'card', shortName: 'Visa / Mastercard', logos: [visaLogo, mastercardLogo], gateway: 'wayl' },
  { id: 'fib', shortName: 'FIB', logos: [fibLogo], gateway: 'wayl' },
  { id: 'fastpay', shortName: 'FastPay', logos: [fastpayLogo], gateway: 'wayl' },
  { id: 'superqi', shortName: 'SuperQi', logos: [superqiLogo], gateway: 'wayl' },
]

/** iOS `ManageSubscriptionView.gatewayName`. Old payments keep their wallet's name. */
export function gatewayName(t: ReturnType<typeof useT>, gateway: string | null | undefined): string {
  switch (gateway?.toLowerCase()) {
    case 'fib':
      return t('premium.fib')
    case 'fastpay':
      return t('premium.fastpay')
    case 'superqi':
      return t('premium.superqi')
    case 'wayl':
      return t('premium.wayl')
    default:
      return gateway ?? '—'
  }
}

/** The account error as a sentence (iOS `error.localizedDescription`). */
export function errorText(t: ReturnType<typeof useT>, e: unknown): string {
  if (isApiError(e)) {
    switch (e.code) {
      case 'offline':
        return t('error.offline')
      case 'unauthorized':
        return t('error.unauthorized')
      case 'server':
        return e.server ?? t('error.server', e.status)
      case 'decode':
        return t('error.decoding')
      case 'misconfigured':
        return t('error.apiHostMisconfigured', '—')
      default:
        return t('premium.paymentFailed')
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
  opts: { attempts?: number; intervalMs?: number; cancelled?: () => boolean; t: ReturnType<typeof useT> },
): Promise<PollResult> {
  const { attempts = 30, intervalMs = 2000, cancelled = () => false, t } = opts
  let lastError: string | null = null
  for (let i = 0; i < attempts; i++) {
    if (cancelled()) return { outcome: 'timeout', error: null }
    try {
      const status = await accountApi.paymentStatus(paymentId)
      const s = status.status.toLowerCase()
      if (s === 'success') return { outcome: 'success' }
      if (s === 'failed') return { outcome: 'failed', error: null }
    } catch (e) {
      lastError = errorText(t, e)
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
