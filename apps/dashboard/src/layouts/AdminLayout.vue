<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { LogOut, ShieldCheck } from '@lucide/vue'
import { navItems } from '@/data/nav'
import type { NavKey } from '@/data/types'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
  SidebarRail,
  SidebarTrigger,
} from '@/components/ui/sidebar'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { toast } from 'vue-sonner'
import adminAvatar from '@/assets/images/admin-avatar.png'

const route = useRoute()
const router = useRouter()

const activeKey = computed<NavKey>(() => {
  const name = String(route.name ?? 'overview')
  if (
    name === 'users' ||
    name === 'payments' ||
    name === 'cancellations' ||
    name === 'plans' ||
    name === 'analytics' ||
    name === 'notifications' ||
    name === 'vpn' ||
    name === 'telemetry'
  ) {
    return name as NavKey
  }
  return 'overview'
})

const titles: Record<NavKey, { title: string; description: string }> = {
  overview: {
    title: 'Executive Overview',
    description: 'Platform health and revenue at a glance.',
  },
  analytics: {
    title: 'Analytics',
    description: 'Funnels, VPN usage, and cancellation cohorts.',
  },
  users: {
    title: 'User Directory',
    description: 'Manage network access, subscriptions, and devices.',
  },
  payments: {
    title: 'Webhook Audit Logs',
    description: 'Monitor payment gateway payloads in real time.',
  },
  plans: {
    title: 'Pricing Plans',
    description: 'Edit IQD amounts, VPN quotas, and notify subscribers.',
  },
  cancellations: {
    title: 'Cancellation Feedback',
    description: 'Reasons users give when they cancel Premium.',
  },
  notifications: {
    title: 'Push & Inbox',
    description: 'Compose multi-locale notifications for devices.',
  },
  vpn: {
    title: 'Infrastructure Health',
    description: 'Node distribution and bandwidth telemetry.',
  },
  telemetry: {
    title: 'System Health',
    description: 'Live events and infrastructure monitoring.',
  },
}

const page = computed(() => titles[activeKey.value])

function goHome() {
  if (route.name === 'overview') return
  router.push({ name: 'overview' })
}

function signOut() {
  toast.message('Signed out')
  router.push({ name: 'login' })
}
</script>

<template>
  <SidebarProvider>
    <Sidebar collapsible="icon" variant="inset">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton
              size="lg"
              :is-active="activeKey === 'overview'"
              @click="goHome"
            >
              <div class="bg-primary text-primary-foreground flex aspect-square size-8 items-center justify-center rounded-lg">
                <ShieldCheck class="size-4" />
              </div>
              <div class="grid flex-1 text-left text-sm leading-tight">
                <span class="truncate font-semibold tracking-wide">Qasa ADMIN</span>
                <span class="text-muted-foreground truncate text-xs">Local & Offline</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>

      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Navigation</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem v-for="item in navItems" :key="item.key">
                <SidebarMenuButton
                  as-child
                  :is-active="activeKey === item.key"
                  :tooltip="item.label"
                >
                  <RouterLink :to="item.to">
                    <component :is="item.icon" />
                    <span>{{ item.label }}</span>
                  </RouterLink>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>

      <SidebarFooter>
        <SidebarMenu>
          <SidebarMenuItem>
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <SidebarMenuButton size="lg" class="data-[state=open]:bg-sidebar-accent">
                  <Avatar class="size-8 rounded-lg">
                    <AvatarImage :src="adminAvatar" alt="Admin" />
                    <AvatarFallback class="rounded-lg">AD</AvatarFallback>
                  </Avatar>
                  <div class="grid flex-1 text-left text-sm leading-tight">
                    <span class="truncate font-medium">System Admin</span>
                    <span class="text-muted-foreground truncate text-xs">admin@kaso.vault</span>
                  </div>
                </SidebarMenuButton>
              </DropdownMenuTrigger>
              <DropdownMenuContent class="w-56" side="top" align="start">
                <DropdownMenuLabel>Account</DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem @click="signOut">
                  <LogOut />
                  Sign out
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarFooter>
      <SidebarRail />
    </Sidebar>

    <SidebarInset>
      <header class="bg-background/80 sticky top-0 z-20 flex h-14 items-center gap-3 border-b px-4 backdrop-blur-md lg:px-6">
        <SidebarTrigger class="-ml-1" />
        <Separator orientation="vertical" class="mr-1 h-4" />
        <div class="min-w-0 flex-1">
          <h1 class="truncate text-sm font-semibold tracking-tight sm:text-base">
            {{ page.title }}
          </h1>
          <p class="text-muted-foreground hidden truncate text-xs sm:block">
            {{ page.description }}
          </p>
        </div>
        <Badge variant="outline" class="hidden gap-1.5 sm:inline-flex">
          <span class="bg-primary size-1.5 animate-pulse rounded-full" />
          Platform Secure
        </Badge>
        <Button variant="ghost" size="icon" class="rounded-full" @click="signOut">
          <Avatar class="size-8">
            <AvatarImage :src="adminAvatar" alt="Admin" />
            <AvatarFallback>AD</AvatarFallback>
          </Avatar>
        </Button>
      </header>

      <div class="flex flex-1 flex-col gap-6 p-4 md:p-6">
        <RouterView v-slot="{ Component, route: childRoute }">
          <Transition name="page">
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
