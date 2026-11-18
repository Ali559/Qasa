// The content script, one copy per frame (manifest `all_frames`). It finds login fields,
// draws the Qasa icon and menu (overlay.ts), fills only after a user action, and reports
// submitted credentials for saving. It never sees a URL it could lie about: the service
// worker uses the browser's `sender.url` for this frame.
//
// A copy only takes charge of an http(s) document. Frames without a URL of their own
// (about:blank, srcdoc, written by script) share their creator's origin, and the parent's
// copy handles them through detect.ts's same-origin traversal, with the parent's URL.
import type { BgResponse, BridgeStatus, ContentRequest, FillValues, IdentityItem, LoginMatch, TabRequest } from '@ext/shared/messages'
import { LANGUAGE_KEY, loadLanguage } from '@ext/shared/language'
import { isWebUrl } from '@ext/shared/protocol'
import { generateConstraints, strongPasswordFields } from './constraints'
import { deepInputs, detect, FRAME_MARK, groupOf, iconFields, identityGroupOf, isOtpField, isPasswordField, type Detection, type FieldGroup } from './detect'
import { fillGenerated, fillOtpRun, restoreFocus, setFieldValue, setSelectValue } from './fill'
import { candidates, optionFor, type IdentityGroup, type IdentityValues } from './identity'
import { loadFonts, type FontBytes } from './fonts'
import { effectiveOpacity, isFieldRendered, isFieldVisible } from './guard'
import type { ContentLanguage } from './i18n'
import { fillFromMenu, loginPasswordMenu, type FillOutcome } from './menu-fill'
import { Overlay, type Suggestion } from './overlay'
import { groupContaining, isSubmitControl, SaveDeduper, saveMessageFor } from './save'

type MarkedWindow = Window & { [FRAME_MARK]?: boolean }
/** Already running here: the popup injected a second copy into a tab that had one. */
const alreadyRunning = !!(window as MarkedWindow)[FRAME_MARK]
const ownsDocument = isWebUrl(location.href)
// Tell a parent frame's copy that this frame handles its own document (detect.ts).
if (ownsDocument) (window as MarkedWindow)[FRAME_MARK] = true

let language: ContentLanguage = 'en'
void loadLanguage().then((l) => (language = l))
// The popup's language picker: follow it at once (the next menu, and an open one's next
// state, use it) instead of keeping the language this page loaded with.
try {
  chrome.storage.onChanged.addListener((changes, area) => {
    const next = changes[LANGUAGE_KEY]?.newValue
    if (area === 'local' && (next === 'en' || next === 'ar' || next === 'ckb')) language = next
  })
} catch {
  // Storage events unavailable: the language stays as loaded.
}

let detection: Detection = { groups: [], otpFields: [], otpRuns: [], identity: [] }
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
  // The open menu's field was replaced by a re-render: move the menu to its successor.
  for (const overlay of overlays.values()) {
    const field = overlay.menuField()
    if (!field || field.isConnected || openMenuRef?.overlay !== overlay) continue
    const live = deepInputs(openMenuRef.ref.doc).find((f) => matchesRef(f, openMenuRef!.ref))
    if (live) overlay.retarget(live)
  }
}

/** The field the open menu was opened on, to find it again after a re-render. */
let openMenuRef: { overlay: Overlay; ref: FieldRef } | undefined

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

/** What a field's menu offers: its login form's logins, a strong password for a new
 *  password (rule 6c), 2FA codes, cards or profiles. */
type MenuContext =
  | { kind: 'login'; group: FieldGroup; anchor: HTMLInputElement }
  | { kind: 'newPassword'; group: FieldGroup; anchor: HTMLInputElement }
  | { kind: 'otp'; anchor: HTMLInputElement }
  | { kind: 'identity'; group: IdentityGroup }

/** A field with a split one-time code: its run of one-character boxes. */
function otpRunOf(field: HTMLInputElement): HTMLInputElement[] | undefined {
  return detection.otpRuns.find((run) => run.includes(field))
}

/** Fields whose click (or ArrowDown) opens the menu: the icon fields and every box of a
 *  split one-time code. */
function openable(field: HTMLInputElement): boolean {
  return iconFields(detection).includes(field) || !!otpRunOf(field)
}

function contextOf(field: HTMLInputElement): MenuContext | null {
  if (isOtpField(field) || otpRunOf(field)) return { kind: 'otp', anchor: field }
  const group = groupOf(detection.groups, field)
  // Saved logins are never offered on a new-password field: only a strong password.
  if (group && (field === group.newPassword || field === group.confirmPassword)) return { kind: 'newPassword', group, anchor: field }
  if (group) return { kind: 'login', group, anchor: field }
  const identity = identityGroupOf(detection.identity, field)
  return identity ? { kind: 'identity', group: identity } : null
}

function openMenuFor(field: HTMLInputElement, keyboard = false): void {
  const context = contextOf(field)
  if (!context) return
  const overlay = overlayFor(field.ownerDocument)
  const fonts = loadFonts(field.ownerDocument, () => ask<FontBytes>({ kind: 'fonts' }))
  const snap = context.kind === 'login' || context.kind === 'newPassword' ? snapshot(context.group, field) : undefined
  openMenuRef = { overlay, ref: fieldRef(field) }
  overlay.openMenu(field, {
    onPick: (item) => {
      if (context.kind === 'identity') {
        // The menu comes back while a card waits for its confirmation.
        void fillFromMenu(overlay, () => fillIdentity(field, context.group, item.id, overlay))
        return
      }
      if (context.kind === 'otp') {
        void fillFromMenu(overlay, () => {
          const live = liveField(fieldRef(field), field)
          return live ? fillTotp(live, item.id, overlay.element) : false
        })
      } else if (context.kind === 'login' && snap) {
        void fillFromMenu(overlay, () => {
          const live = liveGroup(snap, context.group)
          return live ? fillGroup(live, item.id, overlay.element, liveField(snap.anchor, field) ?? undefined) : false
        })
      }
    },
  }, { keyboard })
  overlay.revealAfter(fonts)
  void loadMenu(overlay, context, snap)
}

// MARK: re-finding fields (R6)

const ROLES = ['username', 'password', 'newPassword', 'confirmPassword'] as const
type GroupRole = (typeof ROLES)[number]

/** How to find a field again after the page re-rendered it: its name / id / autocomplete,
 *  and its position among the inputs of its document. */
interface FieldRef {
  key: string
  index: number
  doc: Document
}

interface GroupSnapshot {
  kind: FieldGroup['kind']
  container: FieldGroup['container']
  refs: Partial<Record<GroupRole, FieldRef>>
  anchor: FieldRef
}

function fieldKey(f: HTMLInputElement): string {
  const name = f.name || f.getAttribute('name') || ''
  return [isPasswordField(f) ? 'pw' : 'tx', name, f.id, f.getAttribute('autocomplete') ?? ''].join('|')
}

function fieldRef(f: HTMLInputElement): FieldRef {
  return { key: fieldKey(f), index: deepInputs(f.ownerDocument).indexOf(f), doc: f.ownerDocument }
}

/** A field matches a ref by its name / id / autocomplete when it has any, else by position. */
function matchesRef(f: HTMLInputElement, ref: FieldRef): boolean {
  if (f.ownerDocument !== ref.doc) return false
  const named = !/^\w+\|\|\|$/.test(ref.key)
  return named ? fieldKey(f) === ref.key : deepInputs(ref.doc).indexOf(f) === ref.index
}

function snapshot(group: FieldGroup, anchor: HTMLInputElement): GroupSnapshot {
  const refs: GroupSnapshot['refs'] = {}
  for (const role of ROLES) {
    const f = group[role]
    if (f) refs[role] = fieldRef(f)
  }
  return { kind: group.kind, container: group.container, refs, anchor: fieldRef(anchor) }
}

/** The live field for a ref: `current` while it is still in the page, else the field of a
 *  fresh scan that matches it. */
function liveField(ref: FieldRef, current: HTMLInputElement): HTMLInputElement | null {
  if (current.isConnected) return current
  scan()
  return deepInputs(ref.doc).find((f) => matchesRef(f, ref)) ?? null
}

/**
 * The group as it is in the page now. A React / Vue re-render between the menu opening and
 * the pick can replace the fields (and the form); then a fresh scan's group with the same
 * fields (same form, name / id / autocomplete, or position) is used.
 */
function liveGroup(snap: GroupSnapshot, current: FieldGroup): FieldGroup | null {
  if (ROLES.every((r) => !current[r] || current[r]!.isConnected)) return current
  scan()
  const primary: GroupRole = snap.refs.newPassword ? 'newPassword' : snap.refs.password ? 'password' : 'username'
  let best: FieldGroup | null = null
  let bestScore = 0
  for (const g of detection.groups) {
    const main = g[primary]
    if (!main || !matchesRef(main, snap.refs[primary]!)) continue
    let score = 2
    if (g.container === snap.container) score += 2
    if (g.kind === snap.kind) score += 1
    for (const role of ROLES) {
      const ref = snap.refs[role]
      const f = g[role]
      if (role !== primary && ref && f && matchesRef(f, ref)) score += 1
    }
    if (score > bestScore) {
      best = g
      bestScore = score
    }
  }
  return best
}

async function loadMenu(overlay: Overlay, context: MenuContext, snap?: GroupSnapshot): Promise<void> {
  try {
    const status = await ask<BridgeStatus>({ kind: 'status' })
    if (context.kind === 'newPassword' && (status.state === 'unlocked' || status.state === 'locked')) {
      // The generator needs the desktop, not an unlocked vault. A new field never gets logins.
      overlay.setMenu({ kind: 'suggest', ...(await suggestion(overlay, context, snap)) })
      return
    }
    if (status.state === 'locked' && status.passwordDueDays) {
      // No Touch ID / Hello until the main password is typed in the app: Unlock just
      // brings its window forward, and the menu waits as usual.
      overlay.setMenu({
        kind: 'message',
        key: 'auth.unlock.masterPasswordDueFmt',
        args: [status.passwordDueDays],
        action: { key: 'ext.openQasa', run: () => void unlock(overlay, context, snap) },
      })
      return
    }
    if (status.state === 'locked') {
      overlay.setMenu({ kind: 'message', key: 'ext.page.locked', action: { key: 'ext.page.unlock', run: () => void unlock(overlay, context, snap) } })
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
    if (context.kind === 'login' && isLoginPassword(context)) {
      // R7: a password R1 calls a login may still be a sign-up: a strong password too.
      const [items, suggest] = await Promise.all([
        ask<LoginMatch[]>({ kind: 'matches' }),
        suggestion(overlay, context, snap).catch(() => null),
      ])
      overlay.setMenu(loginPasswordMenu(items, suggest))
      return
    }
    let items = await ask<LoginMatch[]>({ kind: 'matches' })
    if (context.kind === 'otp') items = items.filter((i) => i.hasTotp)
    overlay.setMenu(items.length ? { kind: 'items', items } : { kind: 'message', key: 'ext.page.noMatches' })
  } catch {
    overlay.setMenu({ kind: 'message', key: 'ext.page.error' })
  }
}

/** The password field of a login form (not a change form's current password). */
function isLoginPassword(context: Extract<MenuContext, { kind: 'login' }>): boolean {
  return context.group.kind === 'login' && context.anchor === context.group.password
}

/**
 * "Use a strong password" for this field's group (rule 6c): generated by the desktop to fit
 * the fields (R2), filled into every new field at once (R3), or into just the login field.
 */
async function suggestion(overlay: Overlay, context: Extract<MenuContext, { group: FieldGroup }>, snap?: GroupSnapshot): Promise<Suggestion> {
  const password = await ask<string>({ kind: 'generate', constraints: generateConstraints(context.group) })
  return {
    password,
    run: () =>
      void fillFromMenu(overlay, () => {
        const live = snap ? liveGroup(snap, context.group) : context.group
        const anchor = snap ? liveField(snap.anchor, context.anchor) : context.anchor
        return !!live && fillStrongPassword(live, password, overlay.element, anchor ?? undefined)
      }),
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
async function unlock(overlay: Overlay, context: MenuContext, snap?: GroupSnapshot): Promise<void> {
  overlay.setMenu({ kind: 'message', key: 'ext.page.unlocking' })
  await ask({ kind: 'unlock' }).catch(() => undefined)
  const started = Date.now()
  while (overlay.isMenuOpen() && Date.now() - started < UNLOCK_WAIT_MS) {
    await sleep(POLL_MS)
    const status = await ask<BridgeStatus>({ kind: 'status' }).catch(() => null)
    if (status?.state === 'unlocked') {
      if (overlay.isMenuOpen()) await loadMenu(overlay, context, snap)
      return
    }
  }
  if (overlay.isMenuOpen()) await loadMenu(overlay, context, snap)
}

// MARK: filling

/** The fields a fill of this group writes. */
function targets(group: FieldGroup): HTMLInputElement[] {
  return [group.username, group.password].filter((f): f is HTMLInputElement => !!f)
}

/**
 * The fields of `fields` to write, or null to write none. Every field must be really
 * visible (guard.ts), except one the page doesn't draw at all (`display: none`, off the
 * page, transparent), which is skipped: an optional field hidden by the page never
 * receives a value, and never blocks the visible ones. A field that is drawn but covered
 * or off screen blocks the whole fill (clickjacking).
 */
function visibleTargets(fields: HTMLInputElement[], ownHost: Element | null): HTMLInputElement[] | null {
  const out: HTMLInputElement[] = []
  for (const f of fields) {
    if (isFieldVisible(f, ownHost)) out.push(f)
    else if (isFieldRendered(f)) return null
  }
  return out.length ? out : null
}

/**
 * Fills a group after a user action. The targets are checked (visibleTargets) before the
 * credential is even requested, and again before it is written.
 */
async function fillGroup(group: FieldGroup, itemId: string, ownHost: Element | null, anchor?: HTMLInputElement): Promise<boolean> {
  const fields = targets(group)
  if (!visibleTargets(fields, ownHost)) return false
  const values = await ask<FillValues>({ kind: 'fill', itemId })
  const write = visibleTargets(fields, ownHost)
  if (!write) return false
  if (group.username && values.username && write.includes(group.username)) setFieldValue(group.username, values.username)
  if (group.password && write.includes(group.password)) setFieldValue(group.password, values.password)
  // The item has 2FA and the page asks for it on the same screen.
  const doc = write[0].ownerDocument
  const otp = values.totp ? detection.otpFields.find((f) => f.ownerDocument === doc && isFieldVisible(f, ownHost)) : undefined
  if (otp && values.totp) {
    const run = otpRunOf(otp)
    if (run) fillOtpRun(run, values.totp)
    else setFieldValue(otp, values.totp)
  }
  restoreFocus(anchor)
  return true
}

/**
 * "Use a strong password" (rule 6c, R3): the generated password into the new-password field
 * and its confirm field in one action; on a login form (R7) into its password field only.
 * The first field must be really visible (guard.ts); the confirm field at least rendered.
 * Submitting the form then offers to save the login (R4).
 */
function fillStrongPassword(group: FieldGroup, password: string, ownHost: Element | null, anchor?: HTMLInputElement): boolean {
  const [field, confirm] = strongPasswordFields(group)
  if (!field || !isFieldVisible(field, ownHost)) return false
  fillGenerated(field, password)
  if (confirm && isFieldRendered(confirm)) fillGenerated(confirm, password)
  restoreFocus(anchor)
  return true
}

/** A one-time code into its field, or a digit per box of a split code. */
async function fillTotp(field: HTMLInputElement, itemId: string, ownHost: Element | null): Promise<boolean> {
  const boxes = otpRunOf(field) ?? [field]
  const visible = () => boxes.every((b) => isFieldVisible(b, ownHost))
  if (!visible()) return false
  const code = await ask<string>({ kind: 'totp', itemId })
  if (!visible()) return false
  if (boxes.length > 1) fillOtpRun(boxes, code)
  else setFieldValue(field, code)
  return true
}

const CONFIRM_WAIT_MS = 120_000

/**
 * Fills a card or profile form after a pick. A card is confirmed on the desktop first
 * (Touch ID / Hello or the master password); the menu says so and waits. The field that
 * was clicked must be really visible (guard.ts); the rest of the form, which may be below
 * the fold, must at least be rendered inside the page.
 */
async function fillIdentity(anchor: HTMLInputElement, group: IdentityGroup, itemId: string, overlay: Overlay): Promise<FillOutcome> {
  const ownHost = overlay.element
  if (!isFieldVisible(anchor, ownHost)) return false
  let values: IdentityValues | null = null
  const started = Date.now()
  let asked = false
  let waiting = false
  while (!values) {
    try {
      values = await ask<IdentityValues>({ kind: 'fillIdentity', itemId })
    } catch (e) {
      const code = e instanceof Error ? e.message : 'bad'
      if ((code === 'confirm' || code === 'pending') && !waiting) {
        // The menu comes back saying so while the desktop asks.
        waiting = true
        overlay.showMenu()
        overlay.setMenu({ kind: 'message', key: 'ext.page.confirming' })
      }
      if (code === 'confirm' && !asked) {
        asked = true
        await ask({ kind: 'confirm', itemId }).catch(() => undefined)
      } else if ((code === 'pending' || code === 'confirm') && Date.now() - started < CONFIRM_WAIT_MS && overlay.isMenuOpen()) {
        await sleep(POLL_MS)
      } else if (code === 'cancelled') {
        // The user said no on the desktop: "Not filled.", not "Couldn't fill".
        return 'cancelled'
      } else {
        throw e
      }
    }
  }
  // Out of the way again for the checks and the writes.
  overlay.hideMenu()
  if (!isFieldVisible(anchor, ownHost)) return false
  let wrote = false
  for (const f of group.fields) {
    if (f.field !== anchor && !isFieldRendered(f.field)) continue
    const wanted = candidates(f, group.fields, values)
    if (!wanted.length) continue
    if (f.field.tagName === 'SELECT') {
      const option = optionFor(f.field as HTMLSelectElement, wanted)
      if (option) {
        setSelectValue(f.field as HTMLSelectElement, option.value)
        wrote = true
      }
    } else {
      setFieldValue(f.field as HTMLInputElement, wanted[0])
      wrote = true
    }
  }
  restoreFocus(anchor)
  return wrote
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
  // Keys in the open menu's field drive the menu (overlay.ts handleKey). First, so a picking
  // Enter never reaches the save capture below or the page's own handlers.
  doc.addEventListener(
    'keydown',
    (e) => {
      if (!e.isTrusted) return
      const t = eventTarget(e)
      const overlay = t ? overlays.get(t.ownerDocument) : undefined
      if (!overlay || overlay.menuField() !== t) return
      if (overlay.handleKey(e)) e.stopImmediatePropagation()
    },
    true,
  )
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
      if (!openable(field) || effectiveOpacity(field) < 0.5) return
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
      // ArrowDown in an empty field opens its menu, as a browser's own suggestions do.
      if (!e.isTrusted || e.key !== 'ArrowDown' || e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return
      if (!iconsAllowed || t?.tagName !== 'INPUT') return
      const field = t as HTMLInputElement
      if (field.value !== '' || !openable(field) || effectiveOpacity(field) < 0.5 || overlay?.menuField() === field) return
      e.preventDefault()
      openMenuFor(field, true)
    },
    true,
  )
  new MutationObserver(() => scheduleScan()).observe(doc, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'autocomplete', 'style', 'class', 'hidden', 'open'] })
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
