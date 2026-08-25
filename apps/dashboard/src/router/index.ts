import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
    },
    {
      path: '/',
      component: AdminLayout,
      children: [
        {
          path: '',
          name: 'overview',
          component: () => import('../views/OverviewView.vue'),
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('../views/UsersView.vue'),
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('../views/PaymentsView.vue'),
        },
        {
          path: 'vpn',
          name: 'vpn',
          component: () => import('../views/VpnNodesView.vue'),
        },
        {
          path: 'telemetry',
          name: 'telemetry',
          component: () => import('../views/TelemetryView.vue'),
        },
      ],
    },
  ],
})
