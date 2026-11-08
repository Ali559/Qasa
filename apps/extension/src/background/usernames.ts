// The username typed on step 1 of a multi-step sign-in, per tab, so step 2 can save it
// with the password. Kept in `storage.session`: in the browser's memory, never on disk,
// gone when the browser quits, and readable only by the extension's own pages and worker
// (not content scripts). Unlike a variable, it survives the service worker being stopped
// between the two steps.

/** A sign-in is two pages, not a coffee break. */
export const USERNAME_TTL_MS = 10 * 60 * 1000

interface Kept {
  host: string
  username: string
  at: number
}

/** The part of `chrome.storage.StorageArea` this needs, so tests can pass a fake. */
export interface SessionArea {
  get(key: string): Promise<Record<string, unknown>>
  set(items: Record<string, unknown>): Promise<void>
  remove(key: string): Promise<void>
}

const key = (tabId: number) => `username:${tabId}`

export class UsernameMemory {
  constructor(
    private readonly area: SessionArea,
    private readonly now: () => number = Date.now,
  ) {}

  async remember(tabId: number, host: string, username: string): Promise<void> {
    if (!username) return
    await this.area.set({ [key(tabId)]: { host, username, at: this.now() } satisfies Kept })
  }

  /** The username kept for this tab and site, if recent. */
  async recall(tabId: number, host: string): Promise<string | undefined> {
    const kept = (await this.area.get(key(tabId)))[key(tabId)] as Kept | undefined
    if (!kept || kept.host !== host || this.now() - kept.at > USERNAME_TTL_MS) return undefined
    return kept.username
  }

  async forget(tabId: number): Promise<void> {
    await this.area.remove(key(tabId))
  }

  /** Forgets the tab's username when it navigates to another site. */
  async leftTo(tabId: number, host: string): Promise<void> {
    const kept = (await this.area.get(key(tabId)))[key(tabId)] as Kept | undefined
    if (kept && kept.host !== host) await this.forget(tabId)
  }
}
