import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import LoginView from '../views/LoginView.vue'
import OverviewView from '../views/OverviewView.vue'
import UsersView from '../views/UsersView.vue'
import PaymentsView from '../views/PaymentsView.vue'
import CancellationsView from '../views/CancellationsView.vue'
import PlansView from '../views/PlansView.vue'
import AnalyticsView from '../views/AnalyticsView.vue'
import NotificationsAdminView from '../views/NotificationsAdminView.vue'
import VpnNodesView from '../views/VpnNodesView.vue'
import TelemetryView from '../views/TelemetryView.vue'
import { getAuthToken, isLiveApiEnabled } from '../api/client'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/',
      component: AdminLayout,
      children: [
        { path: '', name: 'overview', component: OverviewView },
        { path: 'analytics', name: 'analytics', component: AnalyticsView },
        { path: 'users', name: 'users', component: UsersView },
        { path: 'payments', name: 'payments', component: PaymentsView },
        { path: 'plans', name: 'plans', component: PlansView },
        { path: 'cancellations', name: 'cancellations', component: CancellationsView },
        { path: 'notifications', name: 'notifications', component: NotificationsAdminView },
        { path: 'vpn', name: 'vpn', component: VpnNodesView },
        { path: 'telemetry', name: 'telemetry', component: TelemetryView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  if (!isLiveApiEnabled()) return true
  if (to.name === 'login') return true
  if (!getAuthToken()) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})
