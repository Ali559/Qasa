// Messages inside the extension: popup and content scripts → service worker
// (`runtime.sendMessage`), and popup → a tab's top-frame content script (`tabs.sendMessage`).
// None of them carries a URL: the service worker takes it from the browser (sender.url or
// the tab), never from the message.

/** What the popup shows. `pairing` carries the matching code while the desktop asks. */
export type BridgeStatus =
  | { state: 'appNotRunning' }
  | { state: 'notPaired'; reason?: 'denied' | 'expired' }
  | { state: 'pairing'; code: string }
  /** `passwordDueDays`: Touch ID / Hello is set up but the main password is due again
   *  (it's been that many days), so it must be typed in the desktop app. */
  | { state: 'locked'; passwordDueDays?: number }
  | { state: 'noVault' }
  | { state: 'unlocked' }

export interface LoginMatch {
  id: string
  title: string
  username: string
  hasTotp: boolean
  /** The login's website, for its bundled site icon (`siteIcon.ts`). Older desktop apps leave it out. */
  website?: string
}

/** A card or profile item for the in-page menu: never a secret, only a masked hint. */
export interface IdentityItem {
  id: string
  kind: 'card' | 'address' | 'name' | 'email' | 'phone' | 'company'
  title: string
  hint: string
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
  /** Cards and profile items (not tied to a site). */
  | { kind: 'identities' }
  /** A picked card or profile: its values by `autocomplete` token. Cards answer `confirm` first. */
  | { kind: 'fillIdentity'; itemId: string }
  /** Ask the desktop to confirm a card fill (Touch ID / Hello or the master password). */
  | { kind: 'confirm'; itemId: string }
  /** Step 1 of a multi-step sign-in: kept in memory for this tab and site only. */
  | { kind: 'rememberUsername'; username: string }
  /** The in-page menu's font files (content/fonts.ts). */
  | { kind: 'fonts' }

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
