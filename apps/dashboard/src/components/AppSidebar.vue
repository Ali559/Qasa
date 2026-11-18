<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ChevronsUpDown, LogOut } from '@lucide/vue'
import { navGroups, navItems } from '@/data/nav'
import type { NavKey } from '@/data/types'
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from '@/components/ui/sidebar'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import adminAvatar from '@/assets/images/admin-avatar.png'

const props = defineProps<{ activeKey: NavKey; email?: string | null; role?: string | null }>()

const displayName = computed(() => (props.role === 'viewer' ? 'Viewer' : props.role ? 'Admin' : 'System Admin'))
const displayEmail = computed(() => props.email ?? 'admin@qasa.io')
const initials = computed(() => (props.email ? props.email.slice(0, 2).toUpperCase() : 'SA'))
const emit = defineEmits<{ signOut: [] }>()

// Active item is one surface step up with a top hairline; the beige icon is the only warm note on the dock.
const itemClass =
  'h-8 rounded-lg px-2.5 text-sm text-sidebar-foreground/70 transition-colors duration-150 hover:text-sidebar-foreground [&>svg]:size-4 [&>svg]:text-sidebar-foreground/55 data-active:bg-sidebar-accent data-active:text-sidebar-foreground data-active:font-medium data-active:shadow-[inset_0_1px_0_rgb(255_255_255/0.08)] data-active:[&>svg]:text-sidebar-primary'

const itemsIn = (group: string) => navItems.filter((item) => item.group === group)
</script>

<template>
  <Sidebar collapsible="icon" variant="floating" class="[&>[data-slot=sidebar-inner]]:rounded-2xl [&>[data-slot=sidebar-inner]]:border-0 [&>[data-slot=sidebar-inner]]:shadow-none">
    <SidebarHeader class="px-3 pt-4 pb-2">
      <RouterLink
        to="/"
        class="flex items-center gap-2.5 rounded-lg p-1 outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
      >
        <img src="/favicon.svg" alt="" class="size-8 shrink-0 rounded-lg" />
        <span class="grid gap-0.5 leading-none group-data-[collapsible=icon]:hidden">
          <span class="text-base font-semibold">Qasa</span>
          <span class="text-sidebar-foreground/55 text-xs">Admin console</span>
        </span>
      </RouterLink>
    </SidebarHeader>

    <SidebarContent class="gap-4 px-1 pt-2">
      <SidebarGroup v-for="group in navGroups" :key="group" class="py-0">
        <SidebarGroupLabel class="text-sidebar-foreground/45 h-6 px-2.5">{{ group }}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu class="gap-0.5">
            <SidebarMenuItem v-for="item in itemsIn(group)" :key="item.key">
              <SidebarMenuButton
                as-child
                :is-active="activeKey === item.key"
                :tooltip="item.label"
                :class="itemClass"
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

    <SidebarFooter class="p-3">
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <SidebarMenuButton size="lg" class="rounded-xl hover:bg-sidebar-accent data-[state=open]:bg-sidebar-accent">
                <Avatar class="size-8 rounded-lg">
                  <AvatarImage :src="adminAvatar" alt="" />
                  <AvatarFallback class="rounded-lg">{{ initials }}</AvatarFallback>
                </Avatar>
                <span class="grid flex-1 gap-0.5 text-start text-sm leading-tight">
                  <span class="truncate font-medium">{{ displayName }}</span>
                  <span class="text-sidebar-foreground/55 truncate text-xs">{{ displayEmail }}</span>
                </span>
                <ChevronsUpDown class="text-sidebar-foreground/50 size-4" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent class="w-56" side="top" align="start">
              <DropdownMenuLabel>Signed in as {{ displayName }}<template v-if="role === 'viewer'"> (read-only)</template></DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem @click="emit('signOut')">
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
</template>
