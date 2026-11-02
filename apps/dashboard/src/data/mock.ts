export const overviewKpis = [
  {
    label: 'Monthly Recurring Rev',
    value: '4.5M',
    unit: 'IQD',
    meta: '+12.4% vs last month',
    tone: 'up' as const,
  },
  {
    label: 'Paid Subscribers',
    value: '450',
    meta: '+42 new this week',
    tone: 'up' as const,
  },
  {
    label: 'Active Free Devices',
    value: '2,100',
    meta: 'Stable',
    tone: 'flat' as const,
  },
  {
    label: 'VPN Bandwidth (30D)',
    value: '1.2',
    unit: 'TB',
    progress: 45,
    tone: 'progress' as const,
  },
]

export const growthData = [
  { month: 'Jan', subscribers: 180 },
  { month: 'Feb', subscribers: 210 },
  { month: 'Mar', subscribers: 255 },
  { month: 'Apr', subscribers: 290 },
  { month: 'May', subscribers: 360 },
  { month: 'Jun', subscribers: 410 },
  { month: 'Jul', subscribers: 450 },
]

export const gateways = [
  { name: 'FIB', value: 50, color: 'var(--chart-1)' },
  { name: 'FastPay', value: 35, color: 'var(--chart-3)' },
  { name: 'SuperQi', value: 15, color: 'var(--chart-2)' },
]

export type UserTier = 'premium' | 'free' | 'expired'
export type UserStatus = 'active' | 'idle' | 'expired' | 'blocked'

export interface UserRow {
  /** Masked display id. */
  id: string
  /** Real account UUID, used for account actions. */
  accountId: string
  status: UserStatus
  tier: UserTier
  devices: string
  expiry: string
  expiryAlert?: boolean
}

export const users: UserRow[] = [
  { id: 'ks_8f92...3a1c', accountId: '00000000-0000-4000-8000-000000000001', status: 'active', tier: 'premium', devices: '3 / 5', expiry: 'Oct 15, 2024' },
  { id: 'ks_2b1e...9f04', accountId: '00000000-0000-4000-8000-000000000002', status: 'active', tier: 'free', devices: '1 / 1', expiry: 'Nov 02, 2024' },
  { id: 'ks_77c0...d812', accountId: '00000000-0000-4000-8000-000000000003', status: 'idle', tier: 'premium', devices: '2 / 5', expiry: 'Dec 18, 2024' },
  { id: 'ks_a91d...44be', accountId: '00000000-0000-4000-8000-000000000004', status: 'expired', tier: 'expired', devices: '0 / 1', expiry: 'Sep 01, 2024', expiryAlert: true },
  { id: 'ks_0ef3...c7aa', accountId: '00000000-0000-4000-8000-000000000005', status: 'active', tier: 'premium', devices: '5 / 5', expiry: 'Jan 09, 2025' },
  { id: 'ks_55b8...1e20', accountId: '00000000-0000-4000-8000-000000000006', status: 'active', tier: 'free', devices: '1 / 1', expiry: 'Oct 30, 2024' },
  { id: 'ks_c3d1...88af', accountId: '00000000-0000-4000-8000-000000000007', status: 'active', tier: 'premium', devices: '4 / 5', expiry: 'Feb 12, 2025' },
  { id: 'ks_19aa...6b02', accountId: '00000000-0000-4000-8000-000000000008', status: 'idle', tier: 'free', devices: '1 / 1', expiry: 'Nov 20, 2024' },
]

export type PayStatus = 'success' | 'failed' | 'pending' | 'expired'

export interface PaymentRow {
  paymentId?: string
  planId?: string
  timestamp: string
  orderId: string
  userId: string
  gateway: string
  amount: string
  status: PayStatus
}

export const payments: PaymentRow[] = [
  { paymentId: 'demo-pay-1', timestamp: '2023-10-27 14:32:01', orderId: 'FIB-ORD-88921A', userId: 'USR-992K1', gateway: 'FIB', amount: '15,000', status: 'success' },
  { paymentId: 'demo-pay-2', timestamp: '2023-10-27 14:28:44', orderId: 'FP-ORD-44102B', userId: 'USR-110X9', gateway: 'FastPay', amount: '25,000', status: 'failed' },
  { paymentId: 'demo-pay-3', timestamp: '2023-10-27 14:15:12', orderId: 'SQ-ORD-77301C', userId: 'USR-882M3', gateway: 'SuperQi', amount: '5,000', status: 'pending' },
  { paymentId: 'demo-pay-4', timestamp: '2023-10-27 13:58:09', orderId: 'FIB-ORD-88900Z', userId: 'USR-441N7', gateway: 'FIB', amount: '50,000', status: 'success' },
  { paymentId: 'demo-pay-5', timestamp: '2023-10-27 13:41:33', orderId: 'FP-ORD-43988A', userId: 'USR-220P2', gateway: 'FastPay', amount: '7,500', status: 'success' },
  { paymentId: 'demo-pay-6', timestamp: '2023-10-27 13:22:18', orderId: 'SQ-ORD-77011D', userId: 'USR-118Q4', gateway: 'SuperQi', amount: '12,000', status: 'pending' },
]

export const vpnKpis = [
  { label: 'Total Active Peers', value: '1,248', meta: '+12%' },
  { label: 'Global Bandwidth', value: '4.2 Gbps' },
  { label: 'Avg Latency', value: '24 ms' },
  { label: 'Node Uptime', value: '99.98%' },
]

export const vpnNodes = [
  {
    name: 'Node 1',
    city: 'Erbil',
    status: 'online' as const,
    ip: '10.0.1.1',
    service: 'wg-main',
    cpu: 45,
    memory: 62,
    memoryLabel: '62% (19.8GB)',
    bandwidth: '1.4 Gbps',
    peers: '412',
  },
  {
    name: 'Node 2',
    city: 'Sulaymaniyah',
    status: 'online' as const,
    ip: '10.0.2.4',
    service: 'wg-main',
    cpu: 28,
    memory: 54,
    memoryLabel: '54% (17.2GB)',
    bandwidth: '980 Mbps',
    peers: '308',
  },
  {
    name: 'Node 3',
    city: 'Baghdad',
    status: 'warn' as const,
    ip: '10.0.3.8',
    service: 'wg-edge',
    cpu: 92,
    memory: 78,
    memoryLabel: '78% (24.9GB)',
    bandwidth: '1.8 Gbps',
    peers: '528',
  },
]

export const telemetryMetrics = [
  { label: 'Active Sessions', value: '24,591', meta: '+12% vs last hr', tone: 'good' as const },
  { label: 'Avg Latency (Global)', value: '42 ms', meta: 'Stable', tone: 'warn' as const },
  { label: 'API Error Rate', value: '0.04%', meta: '-0.01% vs last hr', tone: 'good' as const },
  { label: 'DB Read/Write', value: '8.2 k/s', meta: 'Optimal', tone: 'good' as const },
]

export const resources = [
  { label: 'CPU Load (Cluster)', value: '42%', width: 42 },
  { label: 'Memory Utilization', value: '78%', width: 78 },
  { label: 'Storage Capacity', value: '31%', width: 31 },
  { label: 'Network I/O', value: '1.2 GB/s', width: 64 },
]

export const events = [
  { time: '14:02:11.402', level: 'INFO' as const, message: 'Auth service scaled successfully to 8 replicas.' },
  { time: '14:02:08.192', level: 'WARN' as const, message: 'High latency detected on node bag-edge-03 (148ms).' },
  { time: '14:02:05.881', level: 'DEBUG' as const, message: 'Vault synchronization completed for region MENA.' },
  { time: '14:01:59.001', level: 'ERROR' as const, message: 'API Gateway Timeout — retry scheduled in 5s.' },
  { time: '14:01:52.440', level: 'INFO' as const, message: 'Webhook FIB-ORD-88921A signature verified.' },
  { time: '14:01:48.110', level: 'INFO' as const, message: 'Peer handshake established: 10.0.1.1:51820.' },
]

// --- Demo data for screens backed by newer admin endpoints -------------------

import type { AccountDetail, AuditEntry, PaymentDetail, Timeseries } from '@/api/admin'

const daysAgo = (n: number) => new Date(Date.now() - n * 86_400_000).toISOString()

export function demoAccountDetail(accountId: string): AccountDetail {
  const row = users.find((u) => u.accountId === accountId) ?? users[0]!
  const premium = row.tier === 'premium'
  return {
    account: {
      id: row.accountId,
      public_id: row.id,
      created_at: daysAgo(120),
      blocked_at: row.status === 'blocked' ? daysAgo(2) : null,
      blocked_reason: row.status === 'blocked' ? 'Chargeback abuse' : null,
      lapsed: row.tier === 'expired',
    },
    entitlements: {
      tier: premium ? 'premium' : 'free',
      device_limit: premium ? 5 : 1,
      breach_monitor_limit: premium ? 10 : 1,
      breach: premium,
      vpn: premium,
    },
    devices: [
      {
        id: 'dev-demo-1',
        device_name: 'Pixel 8',
        platform: 'android',
        location: 'Erbil',
        created_at: daysAgo(120),
        last_seen_at: daysAgo(0),
        revoked_at: null,
        notifications_enabled: true,
        is_primary: true,
      },
      {
        id: 'dev-demo-2',
        device_name: 'MacBook Air',
        platform: 'macos',
        location: 'Erbil',
        created_at: daysAgo(40),
        last_seen_at: daysAgo(3),
        revoked_at: null,
        notifications_enabled: false,
        is_primary: false,
      },
      {
        id: 'dev-demo-3',
        device_name: 'Old iPhone',
        platform: 'ios',
        location: null,
        created_at: daysAgo(200),
        last_seen_at: daysAgo(90),
        revoked_at: daysAgo(80),
        notifications_enabled: false,
        is_primary: false,
      },
    ],
    subscriptions: [
      {
        id: 'sub-demo-2',
        status: 'active',
        plan_id: 'premium_monthly',
        amount_iqd: 0,
        auto_renew: false,
        started_at: daysAgo(5),
        expires_at: daysAgo(-12),
        cancelled_at: null,
        cancellation_reason: null,
        granted_by: 'admin-demo',
        grant_days: 7,
        grant_reason: 'Outage compensation',
        grant_revoked_at: null,
      },
      {
        id: 'sub-demo-1',
        status: 'active',
        plan_id: 'premium_monthly',
        amount_iqd: 15000,
        auto_renew: true,
        started_at: daysAgo(25),
        expires_at: daysAgo(-5),
        cancelled_at: null,
        cancellation_reason: null,
        granted_by: null,
        grant_days: null,
        grant_reason: null,
        grant_revoked_at: null,
      },
    ],
    payments: [
      {
        payment_id: 'demo-pay-1',
        status: 'success',
        gateway: 'fib',
        gateway_order_id: 'FIB-ORD-88921A',
        amount_iqd: 15000,
        plan_id: 'premium_monthly',
        created_at: daysAgo(25),
        completed_at: daysAgo(25),
      },
    ],
    breach_monitors: 2,
    vpn_usage: {
      account_id: row.accountId,
      period_start: daysAgo(25),
      bytes_used: 18_400_000_000,
      throttled: false,
      warned_80: false,
      updated_at: daysAgo(0),
    },
    audit: [
      {
        id: 'audit-demo-1',
        admin_id: 'admin-demo',
        admin_email: 'admin@qasa.io',
        action: 'grant_premium',
        target_type: 'account',
        target_id: row.accountId,
        payload: { days: 7, reason: 'Outage compensation' },
        created_at: daysAgo(5),
      },
    ],
  }
}

export function demoPaymentDetail(row: PaymentRow): PaymentDetail {
  const amount = Number(row.amount.replace(/,/g, ''))
  return {
    payment: {
      payment_id: row.paymentId ?? 'demo',
      account_id: row.userId,
      status: row.status,
      gateway: row.gateway.toLowerCase(),
      gateway_order_id: row.orderId,
      amount_iqd: amount,
      plan_id: row.planId ?? 'premium_monthly',
      auto_renew: true,
      subscription_id: null,
      created_at: row.timestamp,
      completed_at: row.status === 'success' ? row.timestamp : null,
    },
    webhooks:
      row.status === 'pending'
        ? []
        : [
            {
              id: `wh-${row.orderId}`,
              gateway: row.gateway.toLowerCase(),
              gateway_order_id: row.orderId,
              payload: { order_id: row.orderId, status: row.status, amount_iqd: amount },
              signature_valid: true,
              processed: true,
              error_message: null,
              received_at: row.timestamp,
            },
          ],
  }
}

export const demoTimeseries: Timeseries = (() => {
  const revenue_daily: Timeseries['revenue_daily'] = []
  const signups_daily: Timeseries['signups_daily'] = []
  const new_paid_daily: Timeseries['new_paid_daily'] = []
  for (let d = 89; d >= 0; d--) {
    const day = daysAgo(d)
    const wave = Math.round(3 + 2 * Math.sin(d / 6))
    revenue_daily.push({ day, gateway: 'fib', total_iqd: wave * 15000, tx_count: wave })
    revenue_daily.push({ day, gateway: 'fastpay', total_iqd: Math.round(wave * 0.7) * 15000, tx_count: Math.round(wave * 0.7) })
    if (d % 2 === 0) revenue_daily.push({ day, gateway: 'superqi', total_iqd: 15000, tx_count: 1 })
    signups_daily.push({ day, count: 8 + ((d * 7) % 11) })
    new_paid_daily.push({ day, count: Math.max(0, wave - 1 + (89 - d) / 30) | 0 })
  }
  const mix = new Map<string, { total_iqd: number; tx_count: number }>()
  for (const r of revenue_daily) {
    const e = mix.get(r.gateway) ?? { total_iqd: 0, tx_count: 0 }
    e.total_iqd += r.total_iqd
    e.tx_count += r.tx_count
    mix.set(r.gateway, e)
  }
  return {
    days: 90,
    revenue_daily,
    signups_daily,
    new_paid_daily,
    gateway_mix: [...mix].map(([gateway, v]) => ({ gateway, ...v })),
  }
})()

export const demoAudit: AuditEntry[] = [
  { id: 'a1', admin_id: 'x', admin_email: 'admin@qasa.io', action: 'update_vpn_node', target_type: 'vpn_node', target_id: 'node-2', payload: { status: 'online' }, created_at: daysAgo(0.01) },
  { id: 'a2', admin_id: 'x', admin_email: 'admin@qasa.io', action: 'grant_premium', target_type: 'account', target_id: users[0]!.accountId, payload: { days: 7, reason: 'Outage compensation' }, created_at: daysAgo(0.2) },
  { id: 'a3', admin_id: 'x', admin_email: 'ops@qasa.io', action: 'block_account', target_type: 'account', target_id: users[3]!.accountId, payload: { reason: 'Chargeback abuse' }, created_at: daysAgo(0.5) },
  { id: 'a4', admin_id: 'x', admin_email: 'admin@qasa.io', action: 'upsert_plan', target_type: 'plan', target_id: 'premium_monthly', payload: { amount_iqd: 15000 }, created_at: daysAgo(1.2) },
  { id: 'a5', admin_id: 'x', admin_email: 'ops@qasa.io', action: 'retire_vpn_node', target_type: 'vpn_node', target_id: 'node-old', payload: {}, created_at: daysAgo(2) },
  { id: 'a6', admin_id: 'x', admin_email: 'admin@qasa.io', action: 'create_notification', target_type: 'notification', target_id: 'n-1', payload: { audience: 'all' }, created_at: daysAgo(3) },
]
