import { apiFetch } from './client'
import type { PaymentRow, UserRow } from '@/data/mock'

export async function adminLogin(email: string, password: string) {
  return apiFetch<{ token: string; email: string; role: string }>('/v1/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export type OverviewKpi = {
  label: string
  value: string
  unit?: string
  meta?: string
  tone?: string
  progress?: number
}

export async function fetchOverview() {
  return apiFetch<{ kpis: OverviewKpi[] }>('/v1/admin/overview')
}

export async function fetchUsers() {
  return apiFetch<UserRow[]>('/v1/admin/users')
}

export async function fetchPayments() {
  return apiFetch<{ payments: PaymentRow[] }>('/v1/admin/payments')
}

export type CancellationRow = {
  id: string
  accountId: string
  planId: string
  amountIqd: number
  reason: string | null
  cancelledAt: string | null
  expiresAt: string
}

export async function fetchCancellations() {
  return apiFetch<{ cancellations: CancellationRow[] }>('/v1/admin/cancellations')
}

export type PlanRow = {
  id: string
  title: string
  interval: string
  amount_iqd: number
  days: number
  vpn_quota_gb: number
  active: boolean
  sort_order: number
  features_json?: string[]
}

export async function fetchPlans() {
  return apiFetch<{ plans: PlanRow[] }>('/v1/admin/plans')
}

export async function upsertPlan(body: {
  id: string
  title: string
  interval: string
  amount_iqd: number
  days: number
  vpn_quota_gb: number
  active: boolean
  sort_order: number
  features?: string[]
  notify_subscribers?: boolean
}) {
  return apiFetch<{ plan: PlanRow; notified_accounts: number }>('/v1/admin/plans', {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export type AnalyticsResponse = {
  kpis: { label: string; value: string; unit?: string; meta?: string }[]
  funnel: { intents: number; success: number; failed: number; conversion_pct: number }
  cancellations: { id: string; planId: string; reason: string | null; cancelledAt: string | null }[]
}

export async function fetchAnalytics() {
  return apiFetch<AnalyticsResponse>('/v1/admin/analytics')
}

export type AdminNotificationRow = {
  id: string
  title_en: string
  title_ckb?: string | null
  title_ar?: string | null
  body_en: string
  body_ckb?: string | null
  body_ar?: string | null
  audience: string
  created_at: string
}

export async function fetchAdminNotifications() {
  return apiFetch<{ notifications: AdminNotificationRow[] }>('/v1/admin/notifications')
}

export async function createAdminNotification(body: {
  title_en: string
  body_en: string
  title_ckb?: string
  title_ar?: string
  body_ckb?: string
  body_ar?: string
  audience?: string
}) {
  return apiFetch<{ notification: AdminNotificationRow; notified_accounts: number }>(
    '/v1/admin/notifications',
    { method: 'POST', body: JSON.stringify(body) },
  )
}

export type VpnNodeRow = {
  name: string
  city: string
  status: string
  ip: string
  service: string
  cpu: number
  memory: number
  memoryLabel: string
  bandwidth: string
  peers: string
}

export async function fetchVpnNodes() {
  return apiFetch<{ kpis: { label: string; value: string; meta: string }[]; nodes: VpnNodeRow[] }>(
    '/v1/admin/vpn/nodes',
  )
}

export type TelemetryMetric = {
  label: string
  value: string
  meta: string
  tone: string
}

export async function fetchTelemetry() {
  return apiFetch<{ metrics: TelemetryMetric[] }>('/v1/admin/telemetry')
}

export type WebhookEventRow = {
  id: string
  gateway: string
  gateway_order_id: string | null
  payload: Record<string, unknown>
  signature_valid: boolean
  processed: boolean
  error_message: string | null
  received_at: string
}

export async function fetchWebhooks() {
  return apiFetch<WebhookEventRow[]>('/v1/admin/webhooks')
}
