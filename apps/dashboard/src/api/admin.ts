import { apiFetch, apiFetchWithHeaders, qs } from './client'
import type { PaymentRow, UserRow } from '@/data/mock'

export async function adminLogin(email: string, password: string) {
  return apiFetch<{ token: string; email: string; role: string }>('/v1/admin/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

/** Ends this admin session server-side (204). */
export async function adminLogout() {
  return apiFetch<void>('/v1/admin/auth/logout', { method: 'POST' })
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

/** Bare array; the next-page cursor (RFC3339) is in the `X-Next-Before` header. */
export async function fetchUsers(params: { q?: string; limit?: number; before?: string | null } = {}) {
  const { data, headers } = await apiFetchWithHeaders<UserRow[]>(`/v1/admin/users${qs(params)}`)
  return { users: data, nextBefore: headers.get('X-Next-Before') }
}

export async function fetchPayments(
  params: { q?: string; status?: string; gateway?: string; limit?: number; before?: string | null } = {},
) {
  return apiFetch<{ payments: PaymentRow[]; next_before: string | null }>(
    `/v1/admin/payments${qs(params)}`,
  )
}

export type PaymentDetail = {
  payment: {
    payment_id: string
    account_id: string
    status: string
    gateway: string
    gateway_order_id: string | null
    amount_iqd: number
    plan_id: string
    auto_renew: boolean
    kind?: 'checkout' | 'renewal'
    renews_subscription_id?: string | null
    subscription_id: string | null
    created_at: string
    completed_at: string | null
  }
  webhooks: WebhookEventRow[]
}

export async function fetchPaymentDetail(paymentId: string) {
  return apiFetch<PaymentDetail>(`/v1/admin/payments/${encodeURIComponent(paymentId)}`)
}

// --- Accounts ---------------------------------------------------------------

export type AccountDevice = {
  id: string
  device_name: string
  platform: string
  location: string | null
  created_at: string
  last_seen_at: string | null
  revoked_at: string | null
  notifications_enabled: boolean
  is_primary: boolean
}

export type AccountSubscription = {
  id: string
  status: string
  plan_id: string
  amount_iqd: number
  auto_renew: boolean
  started_at: string
  expires_at: string
  cancelled_at: string | null
  cancellation_reason: string | null
  granted_by: string | null
  grant_days: number | null
  grant_reason: string | null
  grant_revoked_at: string | null
  renewal_attempts?: number
}

export type AccountPayment = {
  payment_id: string
  status: string
  gateway: string
  gateway_order_id: string | null
  amount_iqd: number
  plan_id: string
  kind?: 'checkout' | 'renewal'
  created_at: string
  completed_at: string | null
}

export type AuditEntry = {
  id: string
  admin_id: string
  admin_email: string | null
  action: string
  target_type: string
  target_id: string
  payload: Record<string, unknown> | null
  created_at: string
}

export type AccountDetail = {
  account: {
    id: string
    public_id: string
    created_at: string
    blocked_at: string | null
    blocked_reason: string | null
    lapsed: boolean
  }
  entitlements: {
    tier: string
    device_limit: number
    breach_monitor_limit: number
    breach: boolean
    vpn: boolean
  }
  devices: AccountDevice[]
  subscriptions: AccountSubscription[]
  payments: AccountPayment[]
  /** Saved for automatic renewals; null when there is none. */
  payment_method?: { gateway: string; created_at: string } | null
  breach_monitors: number
  vpn_usage: {
    account_id: string
    period_start: string
    bytes_used: number
    throttled: boolean
    warned_80: boolean
    updated_at: string
  }
  audit: AuditEntry[]
}

export async function fetchAccountDetail(accountId: string) {
  return apiFetch<AccountDetail>(`/v1/admin/accounts/${encodeURIComponent(accountId)}`)
}

export async function blockAccount(accountId: string, reason: string) {
  return apiFetch<{ blocked: boolean; changed: boolean }>(
    `/v1/admin/accounts/${encodeURIComponent(accountId)}/block`,
    { method: 'POST', body: JSON.stringify({ reason }) },
  )
}

export async function unblockAccount(accountId: string) {
  return apiFetch<{ blocked: boolean; changed: boolean }>(
    `/v1/admin/accounts/${encodeURIComponent(accountId)}/block`,
    { method: 'DELETE' },
  )
}

export async function unlinkAccountDevice(accountId: string, deviceId: string) {
  return apiFetch<void>(
    `/v1/admin/accounts/${encodeURIComponent(accountId)}/devices/${encodeURIComponent(deviceId)}`,
    { method: 'DELETE' },
  )
}

export async function grantPremium(
  accountId: string,
  body: { days: number; reason: string; plan_id?: string },
) {
  return apiFetch<{
    subscription: { id: string; account_id: string; status: string; plan_id: string; started_at: string; expires_at: string }
    granted_days: number
  }>(`/v1/admin/accounts/${encodeURIComponent(accountId)}/grants`, {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function revokeGrant(subscriptionId: string) {
  return apiFetch<{ subscription: { id: string; status: string; expires_at: string }; revoked: boolean }>(
    `/v1/admin/grants/${encodeURIComponent(subscriptionId)}`,
    { method: 'DELETE' },
  )
}

export type GrantRow = {
  subscription_id: string
  account_id: string
  plan_id: string
  status: string
  expires_at: string
  grant_days: number | null
  grant_reason: string | null
  granted_by_email: string | null
  granted_at: string
  grant_revoked_at: string | null
}

export async function fetchGrants() {
  return apiFetch<{ grants: GrantRow[] }>('/v1/admin/grants')
}

// --- Reports ----------------------------------------------------------------

export type Timeseries = {
  days: number
  revenue_daily: { day: string; gateway: string; total_iqd: number; tx_count: number }[]
  signups_daily: { day: string; count: number }[]
  new_paid_daily: { day: string; count: number }[]
  gateway_mix: { gateway: string; total_iqd: number; tx_count: number }[]
}

export async function fetchTimeseries(days = 90) {
  return apiFetch<Timeseries>(`/v1/admin/timeseries${qs({ days })}`)
}

export async function fetchAudit(params: { target_id?: string; before?: string | null; limit?: number } = {}) {
  return apiFetch<{ entries: AuditEntry[]; next_before: string | null }>(`/v1/admin/audit${qs(params)}`)
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
  id?: string
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
  maxPeers?: number
  endpointPort?: number
  lastHeartbeat?: string | null
}

export async function fetchVpnNodes() {
  return apiFetch<{ kpis: { label: string; value: string; meta: string }[]; nodes: VpnNodeRow[] }>(
    '/v1/admin/vpn/nodes',
  )
}

/** Node as returned by create/update (snake_case, unlike the list rows). */
export type VpnNodeRecord = {
  id: string
  name: string
  city: string
  status: string
  public_ip: string
  endpoint_port: number
  wg_public_key: string
  max_peers: number
  active_peers: number
  ip_pool_cidr: string
  service_name: string
  last_heartbeat: string | null
}

export async function createVpnNode(body: {
  name: string
  city: string
  public_ip: string
  wg_public_key: string
  ip_pool_cidr: string
  endpoint_port?: number
  max_peers?: number
  service_name?: string
}) {
  return apiFetch<{ node: VpnNodeRecord; agent_token: string }>('/v1/admin/vpn/nodes', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export async function updateVpnNode(
  nodeId: string,
  body: {
    name?: string
    city?: string
    max_peers?: number
    endpoint_port?: number
    wg_public_key?: string
    status?: 'online' | 'draining'
  },
) {
  return apiFetch<{ node: VpnNodeRecord }>(`/v1/admin/vpn/nodes/${encodeURIComponent(nodeId)}`, {
    method: 'PATCH',
    body: JSON.stringify(body),
  })
}

export async function retireVpnNode(nodeId: string) {
  return apiFetch<void>(`/v1/admin/vpn/nodes/${encodeURIComponent(nodeId)}`, { method: 'DELETE' })
}

export async function rotateVpnAgentToken(nodeId: string) {
  return apiFetch<{ node_id: string; agent_token: string }>(
    `/v1/admin/vpn/nodes/${encodeURIComponent(nodeId)}/agent-token`,
    { method: 'POST' },
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
