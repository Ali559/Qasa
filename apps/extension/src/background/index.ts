// The service worker (Chrome) / background script (Firefox). The only part of the
// extension that talks to the desktop. Security rules enforced here (plan §8.2.1):
//   - The page URL for matches/fill/totp/save comes from the browser: `sender.url`, the
//     frame's own URL. Never from a message. The popup gets a tab's matches through that
//     tab's top-frame content script, so the same rule covers it (messages.ts TabRequest).
//   - `copy`, `search`, `generate` and `pair` are accepted only from our own extension pages.
//   - Nothing is cached: secrets in a reply go straight to the caller. The only stored
//     data is the pairing (bridge.ts).
import type { BgRequest, BgResponse, BridgeStatus, ContentRequest, LoginMatch, PopupRequest, SearchHit } from '@ext/shared/messages'
import { isWebUrl, NATIVE_HOST } from '@ext/shared/protocol'
import type { FontBytes } from '@ext/content/fonts'
import { FONT_FILES } from '@ext/shared/fonts'
import { Bridge, BridgeError, type PairingStore, type StoredPairing } from './bridge'
import { UsernameMemory } from './usernames'

const PAIRING_KEY = 'pairing'

const store: PairingStore = {
  async load() {
    const { [PAIRING_KEY]: value } = await chrome.storage.local.get(PAIRING_KEY)
    const p = value as Partial<StoredPairing> | undefined
    return p && typeof p.clientId === 'string' && typeof p.key === 'string' ? { clientId: p.clientId, key: p.key } : null
  },
  save: (pairing) => chrome.storage.local.set({ [PAIRING_KEY]: pairing }),
  clear: () => chrome.storage.local.remove(PAIRING_KEY),
}

/** The name the desktop shows in "Allow … to use Qasa?". */
function browserName(): string {
  const ua = navigator.userAgent
  if (ua.includes('Firefox/')) return 'Firefox'
  if (ua.includes('Edg/')) return 'Edge'
  return 'Chrome'
}

const bridge = new Bridge(() => chrome.runtime.connectNative(NATIVE_HOST), store, browserName())

// MARK: multi-step sign-in memory

/** Step 1's username (usernames.ts). Cleared when the tab closes or leaves the site, and
 *  after a save that used it. */
const usernames = new UsernameMemory(chrome.storage.session)

chrome.tabs.onRemoved.addListener((tabId) => void usernames.forget(tabId))
chrome.tabs.onUpdated.addListener((tabId, change) => {
  if (change.url) void usernames.leftTo(tabId, hostOf(change.url))
})

// MARK: fonts for the in-page menu

/** The bundled fonts as base64, read once per worker life (content/fonts.ts). */
let fontBytes: Promise<FontBytes> | undefined

async function readFont(path: string): Promise<string> {
  const buf = new Uint8Array(await (await fetch(chrome.runtime.getURL(path))).arrayBuffer())
  let bin = ''
  for (let i = 0; i < buf.length; i += 0x8000) bin += String.fromCharCode(...buf.subarray(i, i + 0x8000))
  return btoa(bin)
}

function fonts(): Promise<FontBytes> {
  fontBytes ??= Promise.all([readFont(FONT_FILES.rubik), readFont(FONT_FILES.rudaw)])
    .then(([rubik, rudaw]) => ({ rubik, rudaw }))
    .catch((e) => {
      fontBytes = undefined
      throw e
    })
  return fontBytes
}

function hostOf(url: string): string {
  try {
    return new URL(url).hostname
  } catch {
    return ''
  }
}

// MARK: routing

const ownOrigin = chrome.runtime.getURL('')

/** Our own pages (the popup) have our extension origin. Content scripts never do. */
function isExtensionPage(sender: chrome.runtime.MessageSender): boolean {
  return sender.id === chrome.runtime.id && !!sender.url?.startsWith(ownOrigin)
}

/** The frame a content-script message came from, as the browser reports it. */
function contentFrameUrl(sender: chrome.runtime.MessageSender): string {
  if (sender.id !== chrome.runtime.id || !sender.tab || isExtensionPage(sender)) throw new BridgeError('forbidden')
  // Only http(s) frames run the content script in earnest (content/index.ts).
  if (isWebUrl(sender.url)) return sender.url
  throw new BridgeError('badUrl')
}

function items<T>(reply: Record<string, unknown>): T[] {
  return Array.isArray(reply.items) ? (reply.items as T[]) : []
}

async function handlePopup(req: PopupRequest): Promise<unknown> {
  switch (req.kind) {
    case 'status':
      return bridge.status()
    case 'pair':
      return bridge.pair()
    case 'unlock':
      await bridge.request('unlock', {})
      return null
    case 'search':
      return items<SearchHit>(await bridge.request('search', { query: String(req.query).slice(0, 200) }))
    case 'copy':
      if (!['username', 'password', 'totp'].includes(req.field)) throw new BridgeError('bad')
      await bridge.request('copy', { itemId: String(req.itemId), field: req.field })
      return null
    case 'generate':
      return (await bridge.request('generate', {})).password
  }
}

/** Every frame of every page asks for the status once, to decide whether to show its
 *  field icons; one answer serves them all for a few seconds. */
let contentStatus: { at: number; value: Promise<BridgeStatus> } | undefined
const CONTENT_STATUS_TTL_MS = 3_000

async function handleContent(req: ContentRequest, sender: chrome.runtime.MessageSender): Promise<unknown> {
  const url = contentFrameUrl(sender)
  const tabId = sender.tab!.id!
  switch (req.kind) {
    case 'status':
      if (!contentStatus || Date.now() - contentStatus.at > CONTENT_STATUS_TTL_MS) {
        contentStatus = { at: Date.now(), value: bridge.status() }
      }
      return contentStatus.value
    case 'unlock':
      await bridge.request('unlock', {})
      return null
    case 'matches':
      return items<LoginMatch>(await bridge.request('matches', { url }))
    case 'fill': {
      const r = await bridge.request('fill', { itemId: String(req.itemId), url })
      return { username: r.username, password: r.password, ...(typeof r.totp === 'string' ? { totp: r.totp } : {}) }
    }
    case 'totp':
      return (await bridge.request('totp', { itemId: String(req.itemId), url })).code
    case 'rememberUsername':
      await usernames.remember(tabId, hostOf(url), String(req.username ?? ''))
      return null
    case 'fonts':
      return fonts()
    case 'save': {
      if (!req.password) throw new BridgeError('bad')
      const kept = await usernames.recall(tabId, hostOf(url))
      const username = String(req.username ?? '') || kept || ''
      const r = await bridge.request('save', { url, username, password: String(req.password) })
      if (kept !== undefined) await usernames.forget(tabId)
      return r.result
    }
  }
}

const POPUP_ONLY = new Set(['pair', 'search', 'copy', 'generate'])
const CONTENT_ONLY = new Set(['matches', 'fill', 'totp', 'save', 'rememberUsername', 'fonts'])

async function handle(req: BgRequest, sender: chrome.runtime.MessageSender): Promise<unknown> {
  if (!req || typeof req !== 'object' || typeof req.kind !== 'string') throw new BridgeError('bad')
  if (isExtensionPage(sender)) {
    if (CONTENT_ONLY.has(req.kind)) throw new BridgeError('forbidden')
    return handlePopup(req as PopupRequest)
  }
  if (POPUP_ONLY.has(req.kind)) throw new BridgeError('forbidden')
  return handleContent(req as ContentRequest, sender)
}

chrome.runtime.onMessage.addListener((message: BgRequest, sender, sendResponse: (r: BgResponse) => void) => {
  handle(message, sender).then(
    (value) => sendResponse({ ok: true, value }),
    (error: unknown) => sendResponse({ ok: false, error: error instanceof BridgeError ? error.code : 'bad' }),
  )
  return true // async response
})
