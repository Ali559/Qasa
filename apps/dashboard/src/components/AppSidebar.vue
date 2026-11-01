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
const displayEmail = computed(() => props.email ?? 'admin@kaso.vault')
const initials = computed(() => (props.email ? props.email.slice(0, 2).toUpperCase() : 'SA'))
const emit = defineEmits<{ signOut: [] }>()

// Active item reads as a raised sunflower key sitting on the ink dock.
const itemClass =
  'h-10 rounded-xl px-3 text-[0.9rem] text-sidebar-foreground/75 transition-[translate,background-color,color] data-active:bg-sidebar-primary data-active:text-sidebar-primary-foreground data-active:font-semibold data-active:shadow-[0_3px_0_#b8861a,inset_0_1px_0_rgb(255_255_255/0.5)] data-active:-translate-y-px'

const itemsIn = (group: string) => navItems.filter((item) => item.group === group)
</script>

<template>
  <Sidebar collapsible="icon" variant="floating" class="[&>[data-slot=sidebar-inner]]:rounded-[1.6rem] [&>[data-slot=sidebar-inner]]:shadow-[0_6px_0_#08071a]">
    <SidebarHeader class="px-3 pt-4">
      <RouterLink
        to="/"
        class="flex items-center gap-3 rounded-xl p-1 outline-none focus-visible:ring-2 focus-visible:ring-sidebar-ring"
      >
        <span
          class="bg-sidebar-primary text-sidebar-primary-foreground font-heading grid size-9 shrink-0 -rotate-6 place-items-center rounded-xl text-xl font-extrabold shadow-[0_3px_0_#b8861a]"
          aria-hidden="true"
        >q</span>
        <span class="grid leading-tight group-data-[collapsible=icon]:hidden">
          <span class="font-heading text-lg font-bold">Qasa</span>
          <span class="text-sidebar-foreground/60 text-xs">Admin console</span>
        </span>
      </RouterLink>
    </SidebarHeader>

    <SidebarContent class="px-1">
      <SidebarGroup v-for="group in navGroups" :key="group">
        <SidebarGroupLabel class="text-sidebar-foreground/45">{{ group }}</SidebarGroupLabel>
        <SidebarGroupContent>
          <SidebarMenu class="gap-1">
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
              <SidebarMenuButton size="lg" class="bg-sidebar-accent rounded-2xl data-[state=open]:bg-sidebar-accent">
                <Avatar class="size-8 rounded-xl">
                  <AvatarImage :src="adminAvatar" alt="" />
                  <AvatarFallback class="rounded-xl">{{ initials }}</AvatarFallback>
                </Avatar>
                <span class="grid flex-1 text-left text-sm leading-tight">
                  <span class="truncate font-semibold">{{ displayName }}</span>
                  <span class="text-sidebar-foreground/60 truncate text-xs">{{ displayEmail }}</span>
                </span>
                <ChevronsUpDown class="text-sidebar-foreground/50" />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent class="w-56 rounded-xl" side="top" align="start">
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
