// Shell navigation, as iOS `AppShellView`: four tabs, and at most one page open on top of
// the current tab (with a back button). Premium pages redirect to the upgrade page.
import { reactive } from 'vue'

export type ShellTab = 'home' | 'passwords' | 'alerts' | 'you'

/** iOS `AppPage`, minus the pages that arrive in later phases (notifications, VPN, premium). */
export type AppPage = 'generator' | 'passwordHealth' | 'collections' | 'settings' | 'terms' | 'privacy'

export const nav = reactive({
  tab: 'home' as ShellTab,
  page: null as AppPage | null,
  /** Item shown in the Passwords detail pane. */
  selectedItemId: null as string | null,
  /** A generated password waiting to become a new login (Generator → Save to my passwords). */
  pendingPassword: null as string | null,
})

export function openPage(page: AppPage): void {
  nav.page = page
}

export function selectTab(tab: ShellTab): void {
  nav.page = null
  nav.tab = tab
}

export function back(): void {
  nav.page = null
}

export function resetNav(): void {
  nav.tab = 'home'
  nav.page = null
  nav.selectedItemId = null
  nav.pendingPassword = null
}
