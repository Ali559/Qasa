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

const route = useRoute()
const router = useRouter()
const { isDark, toggleTheme } = useTheme()

const activeKey = computed<NavKey>(() => {
  const name = String(route.name ?? '')
  return navItems.find((item) => item.key === name)?.key ?? 'overview'
})

const titles: Record<NavKey, { title: string; description: string }> = {
  overview: {
    title: 'Overview',
    description: 'Revenue, subscribers and bandwidth at a glance.',
  },
  analytics: {
    title: 'Analytics',
    description: 'How checkouts convert and why people cancel.',
  },
  users: {
    title: 'Users & devices',
    description: 'Find a subscriber, check their devices, or block access.',
  },
  payments: {
    title: 'Payments',
    description: 'Every payment the gateways reported, with the raw webhook.',
  },
  plans: {
    title: 'Pricing',
    description: 'Change plan prices and VPN quotas in IQD.',
  },
  cancellations: {
    title: 'Cancellations',
    description: 'Reasons users give when they cancel Premium.',
  },
  notifications: {
    title: 'Notifications',
    description: 'Send a message to every user in English, Kurdish and Arabic.',
  },
  vpn: {
    title: 'VPN nodes',
    description: 'Load, peers and bandwidth on each server.',
  },
  telemetry: {
    title: 'Telemetry',
    description: 'Live service health and event logs.',
  },
}

const page = computed(() => titles[activeKey.value])

function signOut() {
  toast.message('Signed out')
  router.push({ name: 'login' })
}
</script>

<template>
  <SidebarProvider>
    <AppSidebar :active-key="activeKey" @sign-out="signOut" />

    <SidebarInset class="bg-transparent">
      <header class="flex items-start gap-3 px-4 pt-5 pb-2 md:px-8 md:pt-7">
        <SidebarTrigger class="mt-1 -ml-1 md:hidden" />
        <div class="min-w-0 flex-1">
          <h1 class="font-heading truncate text-2xl font-bold sm:text-[2rem] sm:leading-tight">
            {{ page.title }}
          </h1>
          <p class="text-muted-foreground mt-0.5 truncate text-sm">
            {{ page.description }}
          </p>
        </div>
        <span class="bg-card text-foreground mt-1 hidden items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold shadow-[0_3px_0_var(--lip)] sm:inline-flex">
          <span class="relative flex size-2">
            <span class="bg-ok absolute inset-0 animate-ping rounded-full opacity-60" />
            <span class="bg-ok relative size-2 rounded-full" />
          </span>
          All systems normal
        </span>
        <Button
          variant="outline"
          size="icon-lg"
          class="mt-0.5 rounded-xl"
          :aria-label="isDark ? 'Switch to light theme' : 'Switch to dark theme'"
          @click="toggleTheme()"
        >
          <Sun v-if="isDark" />
          <Moon v-else />
        </Button>
      </header>

      <div class="mx-auto flex w-full max-w-[1440px] flex-1 flex-col gap-6 px-4 pt-4 pb-10 md:px-8">
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
    opacity 0.18s ease,
    transform 0.18s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
