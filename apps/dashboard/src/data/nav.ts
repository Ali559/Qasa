import type { Component } from 'vue'
import {
  Activity,
  CreditCard,
  LayoutDashboard,
  MessageSquareWarning,
  Network,
  Users,
} from '@lucide/vue'
import type { NavKey } from './types'

export type { NavKey }

export const navItems: {
  key: NavKey
  label: string
  to: string
  icon: Component
}[] = [
  { key: 'overview', label: 'Overview', to: '/', icon: LayoutDashboard },
  { key: 'users', label: 'Users & Devices', to: '/users', icon: Users },
  { key: 'payments', label: 'Payments', to: '/payments', icon: CreditCard },
  { key: 'cancellations', label: 'Cancellations', to: '/cancellations', icon: MessageSquareWarning },
  { key: 'vpn', label: 'VPN Nodes', to: '/vpn', icon: Network },
  { key: 'telemetry', label: 'Telemetry', to: '/telemetry', icon: Activity },
]
