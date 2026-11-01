import type { Component } from 'vue'
import {
  Activity,
  Bell,
  ChartColumn,
  CreditCard,
  LayoutDashboard,
  MessageSquareWarning,
  Network,
  Tags,
  Users,
} from '@lucide/vue'
import type { NavKey } from './types'

export type { NavKey }

export type NavGroup = 'Business' | 'Customers' | 'Network'

export const navGroups: NavGroup[] = ['Business', 'Customers', 'Network']

export const navItems: {
  key: NavKey
  label: string
  to: string
  icon: Component
  group: NavGroup
}[] = [
  { key: 'overview', label: 'Overview', to: '/', icon: LayoutDashboard, group: 'Business' },
  { key: 'analytics', label: 'Analytics', to: '/analytics', icon: ChartColumn, group: 'Business' },
  { key: 'users', label: 'Users & Devices', to: '/users', icon: Users, group: 'Customers' },
  { key: 'payments', label: 'Payments', to: '/payments', icon: CreditCard, group: 'Business' },
  { key: 'plans', label: 'Pricing', to: '/plans', icon: Tags, group: 'Business' },
  { key: 'cancellations', label: 'Cancellations', to: '/cancellations', icon: MessageSquareWarning, group: 'Customers' },
  { key: 'notifications', label: 'Notifications', to: '/notifications', icon: Bell, group: 'Customers' },
  { key: 'vpn', label: 'VPN Nodes', to: '/vpn', icon: Network, group: 'Network' },
  { key: 'telemetry', label: 'Telemetry', to: '/telemetry', icon: Activity, group: 'Network' },
]
