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
