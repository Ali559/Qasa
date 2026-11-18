<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Moon, Sun } from '@lucide/vue'
import { navItems } from '@/data/nav'
import type { NavKey } from '@/data/types'
import AppSidebar from '@/components/AppSidebar.vue'
import { SidebarInset, SidebarProvider, SidebarTrigger } from '@/components/ui/sidebar'
import { Button } from '@/components/ui/button'
import { useTheme } from '@/composables/useTheme'
import { toast } from 'vue-sonner'
import { adminLogout } from '@/api/admin'
import { adminEmail, adminRole, isLiveApiEnabled, setSession } from '@/api/client'

const route = useRoute()
const router = useRouter()
const { isDark, toggleTheme } = useTheme()

const activeKey = computed<NavKey>(() => {
  const name = String(route.name ?? '')
  if (name === 'account') return 'users'
  return navItems.find((item) => item.key === name)?.key ?? 'overview'
})

const titles: Record<NavKey, { title: string; description: string }> = {
  overview: {
    title: 'Overview',
    description: 'Revenue, subscribers and bandwidth at a glance',
  },
  analytics: {
    title: 'Analytics',
    description: 'How checkouts convert and why people cancel',
  },
  users: {
    title: 'Users and devices',
    description: 'Find a subscriber, check their devices, or block access',
  },
  payments: {
    title: 'Payments',
    description: 'Every payment the gateways reported, with the raw webhook',
  },
  plans: {
    title: 'Pricing',
    description: 'Change plan prices and VPN quotas in IQD',
  },
  cancellations: {
    title: 'Cancellations',
    description: 'Reasons users give when they cancel Premium',
  },
  notifications: {
    title: 'Notifications',
    description: 'Send a message to every user in English, Kurdish and Arabic',
  },
  vpn: {
    title: 'VPN nodes',
    description: 'Load, peers and bandwidth on each server',
  },
  telemetry: {
    title: 'Telemetry',
    description: 'Live service health and event logs',
  },
}

const page = computed(() =>
  route.name === 'account'
    ? { title: 'Account', description: 'Devices, subscriptions, payments and staff actions for one user' }
    : titles[activeKey.value],
)

async function signOut() {
  if (isLiveApiEnabled()) {
    try {
      await adminLogout()
    } catch {
      // The token may already be dead; clearing local state is what matters.
    }
    setSession(null)
  }
  toast.message('Signed out')
  router.push({ name: 'login' })
}
</script>

<template>
  <SidebarProvider>
    <AppSidebar
      :active-key="activeKey"
      :email="isLiveApiEnabled() ? adminEmail : null"
      :role="isLiveApiEnabled() ? adminRole : null"
      @sign-out="signOut"
    />

    <SidebarInset class="bg-transparent">
      <div class="mx-auto flex w-full max-w-[1440px] flex-1 flex-col gap-6 px-4 pt-6 pb-12 md:px-8 md:pt-8">
        <header class="flex items-start gap-3">
          <SidebarTrigger class="mt-0.5 -ms-1 md:hidden" />
          <div class="min-w-0 flex-1">
            <h1 class="truncate text-2xl leading-tight font-medium sm:text-[1.75rem]">{{ page.title }}</h1>
            <p class="text-muted-foreground mt-1 text-sm sm:truncate">{{ page.description }}</p>
          </div>
          <span
            v-if="isLiveApiEnabled() && adminRole === 'viewer'"
            class="bg-warn/14 text-warn mt-1 hidden h-7 items-center rounded-md px-2.5 text-xs font-medium sm:inline-flex"
            title="Your account can view everything but not change anything."
          >Read-only</span>
          <Button
            variant="ghost"
            size="icon"
            class="text-muted-foreground hover:text-foreground"
            :aria-label="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
            @click="toggleTheme()"
          >
            <Sun v-if="isDark" />
            <Moon v-else />
          </Button>
        </header>

        <RouterView v-slot="{ Component, route: childRoute }">
          <Transition name="page" mode="out-in">
            <div
              v-if="Component"
              :key="String(childRoute.name ?? childRoute.path)"
              class="min-h-0 w-full"
            >
              <component :is="Component" />
            </div>
          </Transition>
        </RouterView>
      </div>
    </SidebarInset>
  </SidebarProvider>
</template>

<style scoped>
.page-enter-active,
.page-leave-active {
  transition:
    opacity 0.24s cubic-bezier(0.22, 1, 0.36, 1),
    transform 0.24s cubic-bezier(0.22, 1, 0.36, 1);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
}
</style>
