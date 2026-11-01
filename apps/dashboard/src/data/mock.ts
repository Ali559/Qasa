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
export type UserStatus = 'active' | 'idle' | 'expired'

export interface UserRow {
  id: string
  status: UserStatus
  tier: UserTier
  devices: string
  expiry: string
  expiryAlert?: boolean
}

export const users: UserRow[] = [
  { id: 'ks_8f92...3a1c', status: 'active', tier: 'premium', devices: '3 / 5', expiry: 'Oct 15, 2024' },
  { id: 'ks_2b1e...9f04', status: 'active', tier: 'free', devices: '1 / 1', expiry: 'Nov 02, 2024' },
  { id: 'ks_77c0...d812', status: 'idle', tier: 'premium', devices: '2 / 5', expiry: 'Dec 18, 2024' },
  { id: 'ks_a91d...44be', status: 'expired', tier: 'expired', devices: '0 / 1', expiry: 'Sep 01, 2024', expiryAlert: true },
  { id: 'ks_0ef3...c7aa', status: 'active', tier: 'premium', devices: '5 / 5', expiry: 'Jan 09, 2025' },
  { id: 'ks_55b8...1e20', status: 'active', tier: 'free', devices: '1 / 1', expiry: 'Oct 30, 2024' },
  { id: 'ks_c3d1...88af', status: 'active', tier: 'premium', devices: '4 / 5', expiry: 'Feb 12, 2025' },
  { id: 'ks_19aa...6b02', status: 'idle', tier: 'free', devices: '1 / 1', expiry: 'Nov 20, 2024' },
]

export type PayStatus = 'success' | 'failed' | 'pending'

export interface PaymentRow {
  timestamp: string
  orderId: string
  userId: string
  gateway: string
  amount: string
  status: PayStatus
}

export const payments: PaymentRow[] = [
  { timestamp: '2023-10-27 14:32:01', orderId: 'FIB-ORD-88921A', userId: 'USR-992K1', gateway: 'FIB', amount: '15,000', status: 'success' },
  { timestamp: '2023-10-27 14:28:44', orderId: 'FP-ORD-44102B', userId: 'USR-110X9', gateway: 'FastPay', amount: '25,000', status: 'failed' },
  { timestamp: '2023-10-27 14:15:12', orderId: 'SQ-ORD-77301C', userId: 'USR-882M3', gateway: 'SuperQi', amount: '10,000', status: 'pending' },
  { timestamp: '2023-10-27 13:58:09', orderId: 'FIB-ORD-88900Z', userId: 'USR-441N7', gateway: 'FIB', amount: '50,000', status: 'success' },
  { timestamp: '2023-10-27 13:41:33', orderId: 'FP-ORD-43988A', userId: 'USR-220P2', gateway: 'FastPay', amount: '7,500', status: 'success' },
  { timestamp: '2023-10-27 13:22:18', orderId: 'SQ-ORD-77011D', userId: 'USR-118Q4', gateway: 'SuperQi', amount: '12,000', status: 'pending' },
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
