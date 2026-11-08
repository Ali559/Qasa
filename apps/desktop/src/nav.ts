// Shell navigation, as iOS `AppShellView`: four tabs, and at most one page open on top of
// the current tab (with a back button). Premium pages redirect to the upgrade page.
import { reactive } from 'vue'

export type ShellTab = 'home' | 'passwords' | 'alerts' | 'you'

/** iOS `AppPage` (VPN aside), plus the desktop's Settings sub-pages. */
export type AppPage =
  | 'generator'
  | 'passwordHealth'
  | 'collections'
  | 'settings'
  | 'terms'
  | 'privacy'
  | 'notifications'
  | 'premiumUpgrade'
  | 'manageSubscription'
  | 'linkedDevices'
  | 'wifiSync'
  | 'browserExtension'

export const nav = reactive({
  tab: 'home' as ShellTab,
  page: null as AppPage | null,
  /** Item shown in the Passwords detail pane. */
  selectedItemId: null as string | null,
  /** A generated password waiting to become a new login (Generator → Save to my passwords). */
  pendingPassword: null as string | null,
  /** Set when the current page was opened from another page. */
  parent: null as AppPage | null,
})

export function openPage(page: AppPage): void {
  nav.page = page
}

/** The page to return to from a page opened on top of another (Settings → Linked devices). */
export function openSubPage(page: AppPage, parent: AppPage): void {
  nav.parent = parent
  nav.page = page
}

export function selectTab(tab: ShellTab): void {
  nav.page = null
  nav.parent = null
  nav.tab = tab
}

export function back(): void {
  nav.page = nav.parent
  nav.parent = null
}

export function resetNav(): void {
  nav.tab = 'home'
  nav.page = null
  nav.selectedItemId = null
  nav.pendingPassword = null
  nav.parent = null
}
