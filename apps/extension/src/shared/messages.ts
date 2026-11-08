// Messages inside the extension: popup and content scripts → service worker
// (`runtime.sendMessage`), and popup → a tab's top-frame content script (`tabs.sendMessage`).
// None of them carries a URL: the service worker takes it from the browser (sender.url or
// the tab), never from the message.

/** What the popup shows. `pairing` carries the matching code while the desktop asks. */
export type BridgeStatus =
  | { state: 'appNotRunning' }
  | { state: 'notPaired'; reason?: 'denied' | 'expired' }
  | { state: 'pairing'; code: string }
  | { state: 'locked' }
  | { state: 'noVault' }
  | { state: 'unlocked' }

export interface LoginMatch {
  id: string
  title: string
  username: string
  hasTotp: boolean
}

export interface SearchHit extends LoginMatch {
  website: string
}

export type CopyField = 'username' | 'password' | 'totp'

/** Popup → service worker. */
export type PopupRequest =
  | { kind: 'status' }
  | { kind: 'pair' }
  | { kind: 'unlock' }
  | { kind: 'search'; query: string }
  | { kind: 'copy'; itemId: string; field: CopyField }
  | { kind: 'generate' }

/** Content script → service worker. */
export type ContentRequest =
  | { kind: 'status' }
  | { kind: 'unlock' }
  | { kind: 'matches' }
  | { kind: 'fill'; itemId: string }
  | { kind: 'totp'; itemId: string }
  | { kind: 'save'; username: string; password: string }
  /** Step 1 of a multi-step sign-in: kept in memory for this tab and site only. */
  | { kind: 'rememberUsername'; username: string }

export type BgRequest = PopupRequest | ContentRequest

/** Every reply from the service worker. `error` is a bridge or reply error code. */
export type BgResponse<T = unknown> = { ok: true; value: T } | { ok: false; error: string }

/**
 * Popup → the tab's top-frame content script. The popup never sends a page URL: the
 * content script asks the service worker, which uses the browser's `sender.url` for that
 * frame. This needs neither the `tabs` permission nor an activeTab grant.
 *   - `qasaMatches`: logins for the page.
 *   - `qasaFill`: fill the focused or first login form (or one-time-code field).
 */
export type TabRequest = { kind: 'qasaMatches' } | { kind: 'qasaFill'; itemId: string }

export interface FillValues {
  username: string
  password: string
  totp?: string
}
