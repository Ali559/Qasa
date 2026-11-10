// The content script, one copy per frame (manifest `all_frames`). It finds login fields,
// draws the Qasa icon and menu (overlay.ts), fills only after a user action, and reports
// submitted credentials for saving. It never sees a URL it could lie about: the service
// worker uses the browser's `sender.url` for this frame.
//
// A copy only takes charge of an http(s) document. Frames without a URL of their own
// (about:blank, srcdoc, written by script) share their creator's origin, and the parent's
// copy handles them through detect.ts's same-origin traversal, with the parent's URL.
import type { BgResponse, BridgeStatus, ContentRequest, FillValues, IdentityItem, LoginMatch, TabRequest } from '@ext/shared/messages'
import { loadLanguage } from '@ext/shared/language'
import { isWebUrl } from '@ext/shared/protocol'
import { detect, FRAME_MARK, groupOf, iconFields, identityGroupOf, isOtpField, type Detection, type FieldGroup } from './detect'
import { setFieldValue, setSelectValue } from './fill'
import { candidates, optionFor, type IdentityGroup, type IdentityValues } from './identity'
import { loadFonts, type FontBytes } from './fonts'
import { effectiveOpacity, isFieldRendered, isFieldVisible } from './guard'
import type { ContentLanguage } from './i18n'
import { Overlay } from './overlay'
import { groupContaining, isSubmitControl, SaveDeduper, saveMessageFor } from './save'

type MarkedWindow = Window & { [FRAME_MARK]?: boolean }
/** Already running here: the popup injected a second copy into a tab that had one. */
const alreadyRunning = !!(window as MarkedWindow)[FRAME_MARK]
const ownsDocument = isWebUrl(location.href)
// Tell a parent frame's copy that this frame handles its own document (detect.ts).
if (ownsDocument) (window as MarkedWindow)[FRAME_MARK] = true

let language: ContentLanguage = 'en'
void loadLanguage().then((l) => (language = l))

let detection: Detection = { groups: [], otpFields: [], identity: [] }
/** One overlay per document we handle (ours, plus script-less same-origin frames). */
const overlays = new Map<Document, Overlay>()
/** Icons show only while the desktop is reachable and paired. */
let iconsAllowed = false
let statusCheckedAt = 0
const STATUS_RECHECK_MS = 30_000

async function ask<T>(request: ContentRequest): Promise<T> {
  const response = (await chrome.runtime.sendMessage(request)) as BgResponse<T> | undefined
  if (!response) throw new Error('noResponse')
  if (!response.ok) throw new Error(response.error)
  return response.value
}

function overlayFor(doc: Document): Overlay {
  let overlay = overlays.get(doc)
  if (!overlay) {
    overlay = new Overlay(doc, () => language, openMenuFor)
    overlays.set(doc, overlay)
    if (doc !== document) watchDocument(doc)
  }
  return overlay
}

// MARK: scanning

function scan(): void {
  detection = detect(document)
  // No icon on fields the page made (nearly) invisible.
  const fields = iconFields(detection).filter((f) => effectiveOpacity(f) >= 0.5)
  if (fields.length && Date.now() - statusCheckedAt > STATUS_RECHECK_MS) void refreshStatus()
  const byDoc = new Map<Document, HTMLInputElement[]>()
  for (const f of iconsAllowed ? fields : []) {
    const list = byDoc.get(f.ownerDocument) ?? []
    list.push(f)
    byDoc.set(f.ownerDocument, list)
  }
  for (const [doc, overlay] of overlays) overlay.setIcons(byDoc.get(doc) ?? [])
  for (const [doc, list] of byDoc) overlayFor(doc).setIcons(list)
}

async function refreshStatus(): Promise<void> {
  statusCheckedAt = Date.now()
  try {
    const status = await ask<BridgeStatus>({ kind: 'status' })
    const allowed = status.state === 'unlocked' || status.state === 'locked'
    if (allowed !== iconsAllowed) {
      iconsAllowed = allowed
      scan()
    }
  } catch {
    // The worker was restarting: try again shortly.
    statusCheckedAt = 0
    setTimeout(() => scheduleScan(0), 1000)
  }
}

/** Throttled: a page that mutates constantly still gets a scan every 250 ms at most. */
let scanTimer: ReturnType<typeof setTimeout> | undefined
function scheduleScan(delay = 250): void {
  if (scanTimer !== undefined && delay > 0) return
  clearTimeout(scanTimer)
  scanTimer = setTimeout(() => {
    scanTimer = undefined
    scan()
  }, delay)
}

let frame = 0
function scheduleReposition(): void {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    for (const overlay of overlays.values()) overlay.reposition()
  })
}

// MARK: menu

/** What a field's menu offers: its login form's logins, 2FA codes, cards or profiles. */
type MenuContext =
  | { kind: 'login'; group: FieldGroup }
  | { kind: 'otp' }
  | { kind: 'identity'; group: IdentityGroup }

function contextOf(field: HTMLInputElement): MenuContext | null {
  if (isOtpField(field)) return { kind: 'otp' }
  const group = groupOf(detection.groups, field)
  if (group) return { kind: 'login', group }
  const identity = identityGroupOf(detection.identity, field)
  return identity ? { kind: 'identity', group: identity } : null
}

function openMenuFor(field: HTMLInputElement): void {
  const context = contextOf(field)
  if (!context) return
  const overlay = overlayFor(field.ownerDocument)
  void loadFonts(field.ownerDocument, () => ask<FontBytes>({ kind: 'fonts' }))
  overlay.openMenu(field, {
    onPick: (item) => {
      if (context.kind === 'identity') {
        // The menu stays up while a card waits for its confirmation.
        void fillIdentity(field, context.group, item.id, overlay)
        return
      }
      overlay.closeMenu()
      if (context.kind === 'otp') void fillTotp(field, item.id, overlay.element)
      else void fillGroup(context.group, item.id, overlay.element)
    },
  })
  void loadMenu(overlay, context)
}

async function loadMenu(overlay: Overlay, context: MenuContext): Promise<void> {
  try {
    const status = await ask<BridgeStatus>({ kind: 'status' })
    if (status.state === 'locked' && status.passwordDueDays) {
      // No Touch ID / Hello until the main password is typed in the app: Unlock just
      // brings its window forward, and the menu waits as usual.
      overlay.setMenu({
        kind: 'message',
        key: 'auth.unlock.masterPasswordDueFmt',
        args: [status.passwordDueDays],
        action: { key: 'ext.openQasa', run: () => void unlock(overlay, context) },
      })
      return
    }
    if (status.state === 'locked') {
      overlay.setMenu({ kind: 'message', key: 'ext.page.locked', action: { key: 'ext.page.unlock', run: () => void unlock(overlay, context) } })
      return
    }
    if (status.state !== 'unlocked') {
      const key = status.state === 'appNotRunning' ? 'ext.page.appNotRunning' : status.state === 'noVault' ? 'ext.page.noVault' : 'ext.page.notPaired'
      overlay.setMenu({ kind: 'message', key })
      return
    }
    if (context.kind === 'identity') {
      const card = context.group.kind === 'card'
      const all = await ask<IdentityItem[]>({ kind: 'identities' })
      const items: LoginMatch[] = all
        .filter((i) => (i.kind === 'card') === card)
        .map((i) => ({ id: i.id, title: i.title, username: i.hint, hasTotp: false }))
      overlay.setMenu(items.length ? { kind: 'items', items } : { kind: 'message', key: card ? 'ext.page.noCards' : 'ext.page.noProfiles' })
      return
    }
    let items = await ask<LoginMatch[]>({ kind: 'matches' })
    if (context.kind === 'otp') items = items.filter((i) => i.hasTotp)
    overlay.setMenu(items.length ? { kind: 'items', items } : { kind: 'message', key: 'ext.page.noMatches' })
  } catch {
    overlay.setMenu({ kind: 'message', key: 'ext.page.error' })
  }
}

const UNLOCK_WAIT_MS = 120_000
const POLL_MS = 1_000
const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * The menu's Unlock: the desktop starts Touch ID / Hello (or shows its window for the
 * password), and the menu waits, then lists the items for the same field: no second
 * click on the field (docs/autofill-plan.md §1.2).
 */
async function unlock(overlay: Overlay, context: MenuContext): Promise<void> {
  overlay.setMenu({ kind: 'message', key: 'ext.page.unlocking' })
  await ask({ kind: 'unlock' }).catch(() => undefined)
  const started = Date.now()
  while (overlay.isMenuOpen() && Date.now() - started < UNLOCK_WAIT_MS) {
    await sleep(POLL_MS)
    const status = await ask<BridgeStatus>({ kind: 'status' }).catch(() => null)
    if (status?.state === 'unlocked') {
      if (overlay.isMenuOpen()) await loadMenu(overlay, context)
      return
    }
  }
  if (overlay.isMenuOpen()) await loadMenu(overlay, context)
}

// MARK: filling

/** The fields a fill of this group writes. */
function targets(group: FieldGroup): HTMLInputElement[] {
  return [group.username, group.password].filter((f): f is HTMLInputElement => !!f)
}

/**
 * Fills a group after a user action. Every target must be really visible (guard.ts)
 * before the credential is even requested, and again before it is written.
 */
async function fillGroup(group: FieldGroup, itemId: string, ownHost: Element | null): Promise<boolean> {
  const fields = targets(group)
  const visible = () => fields.length > 0 && fields.every((f) => isFieldVisible(f, ownHost))
  if (!visible()) return false
  const values = await ask<FillValues>({ kind: 'fill', itemId })
  if (!visible()) return false
  if (group.username && values.username) setFieldValue(group.username, values.username)
  if (group.password) setFieldValue(group.password, values.password)
  // The item has 2FA and the page asks for it on the same screen.
  const otp = values.totp ? detection.otpFields.find((f) => f.ownerDocument === fields[0].ownerDocument && isFieldVisible(f, ownHost)) : undefined
  if (otp && values.totp) setFieldValue(otp, values.totp)
  return true
}

async function fillTotp(field: HTMLInputElement, itemId: string, ownHost: Element | null): Promise<boolean> {
  if (!isFieldVisible(field, ownHost)) return false
  const code = await ask<string>({ kind: 'totp', itemId })
  if (!isFieldVisible(field, ownHost)) return false
  setFieldValue(field, code)
  return true
}

const CONFIRM_WAIT_MS = 120_000

/**
 * Fills a card or profile form after a pick. A card is confirmed on the desktop first
 * (Touch ID / Hello or the master password); the menu says so and waits. The field that
 * was clicked must be really visible (guard.ts); the rest of the form, which may be below
 * the fold, must at least be rendered inside the page.
 */
async function fillIdentity(anchor: HTMLInputElement, group: IdentityGroup, itemId: string, overlay: Overlay): Promise<boolean> {
  const ownHost = overlay.element
  if (!isFieldVisible(anchor, ownHost)) return false
  let values: IdentityValues | null = null
  const started = Date.now()
  let asked = false
  while (!values) {
    try {
      values = await ask<IdentityValues>({ kind: 'fillIdentity', itemId })
    } catch (e) {
      const code = e instanceof Error ? e.message : 'bad'
      if (code === 'confirm' && !asked) {
        asked = true
        overlay.setMenu({ kind: 'message', key: 'ext.page.confirming' })
        await ask({ kind: 'confirm', itemId }).catch(() => undefined)
      } else if ((code === 'pending' || code === 'confirm') && Date.now() - started < CONFIRM_WAIT_MS && overlay.isMenuOpen()) {
        await sleep(POLL_MS)
      } else {
        if (overlay.isMenuOpen()) overlay.setMenu({ kind: 'message', key: code === 'cancelled' ? 'ext.page.cancelled' : 'ext.page.error' })
        return false
      }
    }
  }
  overlay.closeMenu()
  if (!isFieldVisible(anchor, ownHost)) return false
  for (const f of group.fields) {
    if (f.field !== anchor && !isFieldRendered(f.field)) continue
    const wanted = candidates(f, group.fields, values)
    if (!wanted.length) continue
    if (f.field.tagName === 'SELECT') {
      const option = optionFor(f.field as HTMLSelectElement, wanted)
      if (option) setSelectValue(f.field as HTMLSelectElement, option.value)
    } else {
      setFieldValue(f.field as HTMLInputElement, wanted[0])
    }
  }
  return true
}

/** The popup's Fill button (top frame only): the focused group, else the first one that
 *  can be filled, else a one-time-code field. */
async function fillFromPopup(itemId: string): Promise<boolean> {
  scan()
  const fillable = detection.groups.filter((g) => g.kind !== 'signup' && targets(g).length)
  let active: Element | null = document.activeElement
  while (active?.shadowRoot?.activeElement) active = active.shadowRoot.activeElement
  const group = (active && groupOf(fillable, active)) || fillable[0]
  const host = (doc: Document) => overlays.get(doc)?.element ?? null
  if (group) {
    targets(group)[0].scrollIntoView({ block: 'nearest' })
    return fillGroup(group, itemId, host(targets(group)[0].ownerDocument))
  }
  const otp = detection.otpFields[0]
  if (otp) {
    otp.scrollIntoView({ block: 'nearest' })
    return fillTotp(otp, itemId, host(otp.ownerDocument))
  }
  return false
}

// MARK: save capture

const deduper = new SaveDeduper()

function captureFrom(el: Element | null): void {
  if (!el) return
  const group = groupContaining(detection.groups, el) ?? groupOf(detection.groups, el)
  if (!group) return
  const message = saveMessageFor(group)
  if (message && deduper.shouldSend(message)) void ask(message).catch(() => undefined)
}

function eventTarget(e: Event): Element | null {
  const t = e.composedPath()[0] as Node | undefined
  // Not `instanceof Element`: a child frame's nodes come from another realm.
  return t && t.nodeType === 1 ? (t as Element) : null
}

// MARK: lifecycle

/** Save capture, rescans and repositioning for our document and for each script-less
 *  child frame document we handle. */
function watchDocument(doc: Document): void {
  doc.addEventListener('submit', (e) => captureFrom(eventTarget(e)), true)
  doc.addEventListener(
    'keydown',
    (e) => {
      const t = eventTarget(e)
      if (e.key === 'Enter' && t?.tagName === 'INPUT') captureFrom(t)
    },
    true,
  )
  doc.addEventListener(
    'click',
    (e) => {
      const t = eventTarget(e)
      if (e.isTrusted && t && isSubmitControl(t)) captureFrom(t)
    },
    true,
  )
  // Clicking into a field Qasa can fill opens its menu, as Dashlane does; typing closes it.
  // Only a real click: a script focusing or clicking a field opens nothing.
  doc.addEventListener(
    'click',
    (e) => {
      const t = eventTarget(e)
      if (!e.isTrusted || !iconsAllowed || t?.tagName !== 'INPUT') return
      const field = t as HTMLInputElement
      if (!iconFields(detection).includes(field) || effectiveOpacity(field) < 0.5) return
      const overlay = overlays.get(field.ownerDocument)
      if (overlay?.menuField() === field) return
      openMenuFor(field)
    },
    true,
  )
  doc.addEventListener(
    'keydown',
    (e) => {
      const t = eventTarget(e)
      const overlay = t ? overlays.get(t.ownerDocument) : undefined
      if (e.isTrusted && overlay?.menuField() === t && (e.key.length === 1 || e.key === 'Backspace')) overlay.closeMenu()
    },
    true,
  )
  new MutationObserver(() => scheduleScan()).observe(doc, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'autocomplete', 'style', 'class', 'hidden'] })
  doc.addEventListener('focusin', () => scheduleScan(0), true)
  doc.addEventListener('scroll', scheduleReposition, { capture: true, passive: true })
}

function start(): void {
  chrome.runtime.onMessage.addListener((message: TabRequest, sender, sendResponse: (r: BgResponse<unknown>) => void) => {
    // Only our own extension (the popup) can send these, and only the top frame acts on them.
    if (sender.id !== chrome.runtime.id || window !== window.top) return false
    let work: Promise<unknown>
    if (message?.kind === 'qasaMatches') work = ask<LoginMatch[]>({ kind: 'matches' })
    else if (message?.kind === 'qasaFill') work = fillFromPopup(String(message.itemId))
    else return false
    work.then(
      (value) => sendResponse({ ok: true, value }),
      (error: unknown) => sendResponse({ ok: false, error: error instanceof Error ? error.message : 'bad' }),
    )
    return true
  })

  watchDocument(document)
  addEventListener('resize', scheduleReposition, { passive: true })
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => scan())
  else scan()
}

if (!alreadyRunning && ownsDocument) start()
