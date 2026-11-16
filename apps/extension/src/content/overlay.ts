// The in-page UI: a Qasa icon inside each detected field and the menu it opens. Both are
// drawn in a CLOSED shadow root on one host element attached to <html>, so the page can't
// read or script them; guard.ts checks the host was not restyled or covered before a menu
// click is acted on.
import { QASA_MARK_SHAPES, QASA_MARK_TRANSFORM, QASA_MARK_VIEWBOX } from '@/brand/qasaMark'
import { siteLetter } from '@/vault/present'
import type { LoginMatch } from '@ext/shared/messages'
import { HOST_STYLE, hostParent, isHostIntact, occlusionMayMisreport, supportsOcclusion } from './guard'
import { ct, type ContentLanguage } from './i18n'
import { PLEX } from './fonts'
import TOKENS from '@/theme/tokens.css?raw'
import { FONT_WAIT_MS, ICON_INSET, ICON_SIZE, MENU_ARM_MS, MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from './layout'

export type MenuState =
  | { kind: 'loading' }
  | { kind: 'items'; items: LoginMatch[] }
  | { kind: 'message'; key: string; args?: Array<string | number>; action?: { key: string; run: () => void } }
  /** A new-password field (rule 6c): "Use a strong password", the generated value under it. */
  | { kind: 'suggest'; password: string; run: () => void }

/** The app's tokens (tokens.css, generated from design/tokens.json, the iOS values) as
 *  declarations for the shadow root: sizes, type, motion and shadows from the first `:root`
 *  block, and the light / dark palettes, so the in-page menu uses the same values. */
function tokenBlock(selector: string): string {
  const start = TOKENS.indexOf(`${selector} {`)
  if (start < 0) return ''
  const body = TOKENS.slice(TOKENS.indexOf('{', start) + 1, TOKENS.indexOf('}', start))
  return body
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.startsWith('--'))
    .join(' ')
}
const METRICS = tokenBlock(':root')
const LIGHT = tokenBlock(':root[data-theme="light"]')
const DARK = tokenBlock(':root[data-theme="dark"]')

const CSS = `
:host { all: initial; }
* { box-sizing: border-box; }
.icon, .menu {
  ${METRICS}
  ${LIGHT}
  /* The per-document Plex face (fonts.ts): the page's own "IBM Plex Sans Arabic" can't
     stand in for it. */
  --font-family: '${PLEX}', 'IBM Plex Sans Arabic', system-ui, sans-serif;
  font-family: var(--font-family);
  font-size: var(--type-body-size);
  font-weight: var(--type-body-weight);
  line-height: var(--line-height);
  letter-spacing: 0;
  -webkit-font-smoothing: antialiased;
}
@media (prefers-color-scheme: dark) {
  .icon, .menu { ${DARK} }
}
.icon {
  position: fixed; width: ${ICON_SIZE}px; height: ${ICON_SIZE}px; padding: 0; margin: 0;
  border: 0; border-radius: 6px; background: transparent; color: var(--accent);
  display: grid; place-items: center; cursor: pointer; pointer-events: auto;
}
.icon:hover { background: color-mix(in srgb, var(--accent) 12%, transparent); }
.icon svg { width: 16px; height: 18px; }
/* A card (iOS \`CardContainer\`: card fill, 1px stroke, card radius) holding a
   \`SettingsSection\`-style list, lifted off the page with the toast shadow. */
.menu {
  position: fixed; width: ${MENU_WIDTH}px; padding: ${MENU_PADDING}px; margin: 0;
  background: var(--card-background); color: var(--text-primary);
  border: var(--size-stroke) solid var(--stroke); border-radius: var(--radius-card);
  box-shadow: var(--shadow-toast); pointer-events: auto;
}
.head {
  height: ${MENU_HEADER}px; display: flex; align-items: center; gap: 8px; padding-inline: 10px;
  color: var(--accent); font-size: var(--type-row-title-size); font-weight: 700;
}
.head svg { width: 14px; height: 16px; flex-shrink: 0; }
/* iOS \`SettingsRow\`: 60 tall, a 36 badge (radius 10) 14 from a 15 medium title and a 12
   subtitle 2 below; \`RowButtonStyle\`'s 6% wash on hover and press, radius 10. */
.row {
  position: relative; height: ${ROW_HEIGHT}px; width: 100%; display: flex; align-items: center;
  gap: var(--space-row-icon-gap); padding: var(--space-row-vertical) 10px; margin: 0;
  border: 0; border-radius: var(--radius-row-press); background: transparent; color: inherit;
  font: inherit; text-align: start; cursor: pointer;
}
.row::after {
  content: ''; position: absolute; inset: 0; border-radius: inherit; background: var(--text-primary);
  opacity: 0; pointer-events: none; transition: opacity var(--motion-press);
}
.row:hover::after, .row:active::after { opacity: var(--motion-row-pressed-opacity); }
.row:focus-visible, .row.active { outline: 2px solid var(--accent); outline-offset: -2px; }
/* The keyboard's row (focus stays in the page's field): the focus ring plus the hover wash. */
.row.active::after { opacity: var(--motion-row-pressed-opacity); }
.list { display: block; }
.sr { position: fixed; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.tile {
  width: var(--size-icon-badge); height: var(--size-icon-badge); flex-shrink: 0; border-radius: 50%;
  display: grid; place-items: center; box-sizing: border-box; background: var(--card-muted); color: var(--accent);
  font-weight: 700; font-size: calc(var(--size-icon-badge) * 18 / 48); line-height: 1;
}
/* docs/site-icons.md: the site's icon (28 of 48) on siteIconTile with a 1px stroke ring. */
.tile.site { background: var(--site-icon-tile); border: var(--size-stroke) solid var(--stroke); }
.tile.site canvas { display: block; width: calc(var(--size-icon-badge) * 28 / 48); height: calc(var(--size-icon-badge) * 28 / 48); }
.text { display: flex; flex-direction: column; gap: var(--space-row-text-gap); min-width: 0; }
.title, .sub { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title { font-size: var(--type-row-title-size); font-weight: var(--type-row-title-weight); }
.sub { color: var(--text-secondary); font-size: var(--type-row-subtitle-size); font-weight: var(--type-row-subtitle-weight); }
.msg { padding: 8px 10px 10px; color: var(--text-secondary); }
.tile.brand { background: var(--card-muted); color: var(--accent); }
.tile.brand svg { width: 16px; height: 18px; }
.sub.mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
/* iOS \`QasaButtonStyle(.primary)\`. */
.action {
  display: flex; align-items: center; justify-content: center;
  width: calc(100% - 20px); height: var(--size-button-height); margin: 0 10px 10px; padding: 0 var(--space-button-horizontal);
  border: 0; border-radius: var(--radius-control); background: var(--accent); color: var(--on-accent);
  font: inherit; font-size: var(--type-button-size); font-weight: var(--type-button-weight); white-space: nowrap; cursor: pointer;
  transition: opacity var(--motion-press), transform var(--motion-press);
}
.action:active { opacity: var(--motion-pressed-opacity); transform: scale(var(--motion-press-scale)); }
.action:focus-visible, .action.active { outline: 2px solid var(--accent); outline-offset: 2px; }
/* Hidden until the Plex faces are in, so the menu never paints in a system font. */
.menu.pending { visibility: hidden; }
`

/** The side of the icons the desktop sends (docs/site-icons.md step 6). */
const SITE_ICON_PX = 64

/**
 * Draws a login's cached icon (PNG, base64) into a `<canvas>`. Decoded with
 * `createImageBitmap` rather than an `<img src="data:…">`, which a page's CSP `img-src`
 * can block. False on any error: the tile keeps its letter.
 */
async function siteIconCanvas(doc: Document, base64: string): Promise<HTMLCanvasElement | null> {
  try {
    const binary = atob(base64)
    const bytes = new Uint8Array(binary.length)
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i)
    const bitmap = await createImageBitmap(new Blob([bytes], { type: 'image/png' }))
    try {
      const canvas = doc.createElement('canvas')
      canvas.width = SITE_ICON_PX
      canvas.height = SITE_ICON_PX
      canvas.setAttribute('aria-hidden', 'true')
      const ctx = canvas.getContext('2d')
      if (!ctx) return null
      ctx.drawImage(bitmap, 0, 0, SITE_ICON_PX, SITE_ICON_PX)
      return canvas
    } finally {
      bitmap.close()
    }
  } catch {
    return null
  }
}

/** The Qasa mark (Qaf Keyhole), as in the desktop's BrandHeader. DOM calls, no innerHTML. */
function qasaMark(doc: Document): SVGSVGElement {
  const ns = 'http://www.w3.org/2000/svg'
  const svg = doc.createElementNS(ns, 'svg')
  svg.setAttribute('viewBox', QASA_MARK_VIEWBOX)
  svg.setAttribute('aria-hidden', 'true')
  const g = doc.createElementNS(ns, 'g')
  g.setAttribute('transform', QASA_MARK_TRANSFORM)
  for (const shape of QASA_MARK_SHAPES) {
    const el = doc.createElementNS(ns, shape.tag)
    for (const [k, v] of Object.entries(shape.attrs)) el.setAttribute(k, v)
    g.append(el)
  }
  svg.append(g)
  return svg
}

export interface MenuHandlers {
  /** A login row was picked by a click (or Enter) that passed every check. */
  onPick(item: LoginMatch): void
}

export interface MenuOptions {
  /** Opened from the keyboard (ArrowDown in the field): the first row is highlighted as
   *  soon as there is one. */
  keyboard?: boolean
}

interface OpenMenu {
  el: HTMLElement
  field: HTMLInputElement
  shownAt: number
  handlers: MenuHandlers
  /** The highlighted row or button (keyboard), -1 for none. */
  active: number
  /** The keyboard is driving this menu: each new state highlights its first row. */
  keyboard: boolean
  /** Announces the highlighted row: focus stays in the page's field, so an
   *  `aria-activedescendant` there can't point into our closed shadow root. */
  live: HTMLElement
}

export class Overlay {
  private readonly host: HTMLElement
  private readonly root: ShadowRoot
  private readonly icons = new Map<HTMLInputElement, HTMLButtonElement>()
  private menu: OpenMenu | null = null
  private optionSeq = 0
  private observer: IntersectionObserver | null = null
  /** Latest IntersectionObserver v2 verdict for the menu; null when unsupported. */
  private menuVisible: boolean | null = null
  /** The page restyled our host (see ensureAttached). */
  private tampered = false

  constructor(
    private readonly doc: Document,
    private readonly language: () => ContentLanguage,
    private readonly onIcon: (field: HTMLInputElement) => void,
  ) {
    this.host = doc.createElement('div')
    this.host.setAttribute('style', HOST_STYLE)
    this.root = this.host.attachShadow({ mode: 'closed' })
    const style = doc.createElement('style')
    style.textContent = CSS
    this.root.append(style)
    doc.addEventListener('mousedown', (e) => this.onOutside(e), true)
    doc.addEventListener('keydown', (e) => e.isTrusted && e.key === 'Escape' && this.closeMenu(), true)
  }

  get element(): HTMLElement {
    return this.host
  }

  /**
   * Re-attaches the host if the page removed it. A page that rewrote the host's style is
   * treated as hostile: the menu closes and Qasa's UI stays off on this document (restoring
   * the style instead would let the page flip it between our checks).
   */
  private ensureAttached(): boolean {
    if (this.tampered) return false
    if (this.host.getAttribute('style') !== HOST_STYLE) {
      this.tampered = true
      this.closeMenu()
      this.host.remove()
      return false
    }
    const parent = hostParent(this.doc)
    if (this.host.parentNode !== parent) parent.append(this.host)
    this.raise()
    return true
  }

  /**
   * A modal <dialog>, a fullscreen element or an open popover sits in the top layer, above
   * any z-index: then the host goes there too, as a manual popover (when the browser has
   * popovers), shown again so it is above the page's latest top-layer element. Without
   * anything in the top layer it stays a plain fixed element, stacked by z-index.
   */
  private raise(): void {
    const host = this.host as HTMLElement & { showPopover?: () => void; hidePopover?: () => void }
    if (typeof host.showPopover !== 'function') return
    let topLayer = false
    try {
      topLayer = !!this.doc.querySelector('dialog:modal, :fullscreen, :popover-open:not([data-qasa])') || !!this.doc.fullscreenElement
    } catch {
      return // selectors unsupported
    }
    try {
      if (!topLayer) {
        if (host.matches(':popover-open')) host.hidePopover?.()
        return
      }
      if (host.getAttribute('popover') !== 'manual') host.setAttribute('popover', 'manual')
      host.setAttribute('data-qasa', '')
      if (host.matches(':popover-open')) host.hidePopover?.()
      host.showPopover()
    } catch {
      // Not connected, or the page interfered: stay a plain fixed element.
    }
  }

  /** Shows icons for exactly these fields. */
  setIcons(fields: HTMLInputElement[]): void {
    if (this.tampered) return
    for (const [field, icon] of this.icons) {
      if (!fields.includes(field)) {
        icon.remove()
        this.icons.delete(field)
      }
    }
    for (const field of fields) {
      if (this.icons.has(field)) continue
      const icon = this.doc.createElement('button')
      icon.type = 'button'
      icon.className = 'icon'
      icon.tabIndex = -1
      icon.append(qasaMark(this.doc))
      // Keep focus in the page's field.
      icon.addEventListener('mousedown', (e) => e.preventDefault())
      icon.addEventListener('click', (e) => {
        if (!e.isTrusted) return
        e.preventDefault()
        this.onIcon(field)
      })
      this.root.append(icon)
      this.icons.set(field, icon)
    }
    if (this.icons.size && !this.ensureAttached()) return
    this.reposition()
  }

  /** Places each icon inside its field at the inline-end edge; hides it when the field
   *  is off screen or not visible. */
  reposition(): void {
    const win = this.doc.defaultView
    if (!win) return
    for (const [field, icon] of this.icons) {
      const r = field.getBoundingClientRect()
      const shown = field.isConnected && r.width >= ICON_SIZE * 2 && r.height >= ICON_SIZE - 4 && r.bottom > 0 && r.top < win.innerHeight
      icon.style.display = shown ? 'grid' : 'none'
      if (!shown) continue
      const rtl = win.getComputedStyle(field).direction === 'rtl'
      const left = rtl ? r.left + ICON_INSET : r.right - ICON_INSET - ICON_SIZE
      icon.style.left = `${Math.round(left)}px`
      icon.style.top = `${Math.round(r.top + (r.height - ICON_SIZE) / 2)}px`
    }
    if (this.menu) this.placeMenu()
  }

  // MARK: menu

  openMenu(field: HTMLInputElement, handlers: MenuHandlers, options: MenuOptions = {}): void {
    this.closeMenu()
    if (!this.ensureAttached()) return
    // Last child of <html>, so later page elements with the same z-index can't simply sit on
    // top (or of the open modal dialog: see hostParent).
    hostParent(this.doc).append(this.host)
    this.raise()
    const el = this.doc.createElement('div')
    el.className = 'menu'
    const live = this.doc.createElement('div')
    live.className = 'sr'
    live.setAttribute('aria-live', 'polite')
    this.root.append(el, live)
    this.menu = { el, field, shownAt: performance.now(), handlers, active: -1, keyboard: !!options.keyboard, live }
    this.placeMenu()
    this.setMenu({ kind: 'loading' })
    this.menuVisible = null
    if (supportsOcclusion()) {
      this.observer = new IntersectionObserver(
        (entries) => {
          const last = entries[entries.length - 1] as IntersectionObserverEntry & { isVisible?: boolean }
          this.menuVisible = !!last.isVisible
        },
        { threshold: [1], trackVisibility: true, delay: 100 } as IntersectionObserverInit,
      )
      this.observer.observe(el)
    }
    el.addEventListener('click', (e) => this.onMenuClick(e))
  }

  /** Keeps the open menu hidden until `fonts` settles (at most `FONT_WAIT_MS`), then shows
   *  it and restarts the click arming from the moment it became visible. */
  revealAfter(fonts: Promise<void>): void {
    const menu = this.menu
    if (!menu) return
    menu.el.classList.add('pending')
    const reveal = () => {
      if (this.menu !== menu || !menu.el.classList.contains('pending')) return
      menu.el.classList.remove('pending')
      menu.shownAt = performance.now()
    }
    void fonts.finally(reveal)
    setTimeout(reveal, FONT_WAIT_MS)
  }

  /** Takes the open menu off the screen while a fill runs (index.ts `fillFromMenu`),
   *  keeping its state; `showMenu` brings it back. */
  hideMenu(): void {
    if (this.menu) this.menu.el.style.display = 'none'
  }

  showMenu(): void {
    const menu = this.menu
    if (!menu || menu.el.style.display !== 'none') return
    menu.el.style.display = ''
    menu.shownAt = performance.now()
    this.placeMenu()
  }

  isMenuOpen(): boolean {
    return this.menu !== null
  }

  /** The field the open menu belongs to. */
  menuField(): HTMLInputElement | null {
    return this.menu?.field ?? null
  }

  closeMenu(): void {
    this.observer?.disconnect()
    this.observer = null
    this.menu?.el.remove()
    this.menu?.live.remove()
    this.menu = null
  }

  setMenu(state: MenuState): void {
    const menu = this.menu
    if (!menu) return
    const lang = this.language()
    menu.el.lang = lang
    menu.el.dir = lang === 'en' ? 'ltr' : 'rtl'
    menu.el.replaceChildren()
    menu.active = -1
    menu.live.textContent = ''
    const head = this.doc.createElement('div')
    head.className = 'head'
    head.append(qasaMark(this.doc), ct(lang, 'brand.name'))
    menu.el.append(head)
    if (state.kind === 'loading') {
      menu.el.append(this.message('…'))
    } else if (state.kind === 'message') {
      menu.el.append(this.message(ct(lang, state.key, ...(state.args ?? []))))
      if (state.action) {
        const b = this.doc.createElement('button')
        b.className = 'action'
        b.type = 'button'
        b.textContent = ct(lang, state.action.key)
        b.dataset.action = '1'
        const run = state.action.run
        this.actions.set(b, run)
        menu.el.append(b)
      }
    } else if (state.kind === 'suggest') {
      const list = this.listbox(lang)
      const row = this.option()
      const tile = this.doc.createElement('span')
      tile.className = 'tile brand'
      tile.append(qasaMark(this.doc))
      const text = this.doc.createElement('span')
      text.className = 'text'
      const title = this.doc.createElement('span')
      title.className = 'title'
      title.textContent = ct(lang, 'fill.strongPassword')
      const sub = this.doc.createElement('span')
      sub.className = 'sub mono'
      sub.textContent = state.password
      sub.dir = 'ltr'
      text.append(title, sub)
      row.append(tile, text)
      this.actions.set(row, state.run)
      list.append(row)
      menu.el.append(list)
    } else {
      const list = this.listbox(lang)
      for (const item of state.items) {
        const row = this.option()
        const tile = this.doc.createElement('span')
        tile.className = 'tile'
        // The calm letter until the site's icon is decoded (and for good if it can't be).
        tile.textContent = siteLetter(item.title)
        if (item.icon) {
          void siteIconCanvas(this.doc, item.icon).then((canvas) => {
            if (!canvas) return
            tile.textContent = ''
            tile.classList.add('site')
            tile.append(canvas)
          })
        }
        const text = this.doc.createElement('span')
        text.className = 'text'
        const title = this.doc.createElement('span')
        title.className = 'title'
        title.textContent = item.title
        title.dir = 'auto'
        const sub = this.doc.createElement('span')
        sub.className = 'sub'
        sub.textContent = item.username
        sub.dir = 'auto'
        text.append(title, sub)
        row.append(tile, text)
        this.rows.set(row, item)
        list.append(row)
      }
      menu.el.append(list)
    }
    this.placeMenu()
    if (menu.keyboard && this.options().length) this.highlight(0)
  }

  /** The rows' container: a listbox whose highlighted option is its active descendant. */
  private listbox(lang: ContentLanguage): HTMLElement {
    const list = this.doc.createElement('div')
    list.className = 'list'
    list.setAttribute('role', 'listbox')
    list.setAttribute('aria-label', ct(lang, 'brand.name'))
    return list
  }

  private option(): HTMLButtonElement {
    const row = this.doc.createElement('button')
    row.type = 'button'
    row.className = 'row'
    row.tabIndex = -1
    row.id = `qasa-option-${++this.optionSeq}`
    row.setAttribute('role', 'option')
    row.setAttribute('aria-selected', 'false')
    return row
  }

  /** What the keyboard moves through: the rows, else the message's button. */
  private options(): HTMLElement[] {
    return this.menu ? [...this.menu.el.querySelectorAll<HTMLElement>('.row, .action')] : []
  }

  private highlight(index: number): void {
    const menu = this.menu
    if (!menu) return
    const options = this.options()
    menu.active = options.length ? ((index % options.length) + options.length) % options.length : -1
    options.forEach((o, i) => {
      o.classList.toggle('active', i === menu.active)
      if (o.getAttribute('role') === 'option') o.setAttribute('aria-selected', String(i === menu.active))
    })
    const current = options[menu.active]
    const list = menu.el.querySelector('[role="listbox"]')
    if (list && current?.id) list.setAttribute('aria-activedescendant', current.id)
    else list?.removeAttribute('aria-activedescendant')
    const text = current ? [...current.querySelectorAll('.title, .sub')].map((n) => n.textContent ?? '').filter(Boolean) : []
    menu.live.textContent = current ? (text.length ? text.join(', ') : (current.textContent ?? '')) : ''
  }

  /** The highlighted row's index, -1 for none (tests). */
  activeIndex(): number {
    return this.menu?.active ?? -1
  }

  /**
   * A key pressed in the menu's field while it is open (index.ts calls this only for the
   * menu's own field). ArrowDown / ArrowUp move the highlight (wrapping), Enter picks the
   * highlighted row, Escape closes the menu, Tab closes it and lets focus move on. Only a
   * trusted key counts: it stands in for the trusted click. True when the key was used
   * (the caller then keeps it from the page).
   */
  handleKey(e: KeyboardEvent): boolean {
    const menu = this.menu
    if (!menu || !e.isTrusted || e.isComposing || e.altKey || e.ctrlKey || e.metaKey) return false
    // A fill is running with the menu out of the way.
    if (menu.el.style.display === 'none') return false
    if (e.key === 'Tab') {
      this.closeMenu()
      return false
    }
    if (e.shiftKey) return false
    if (e.key === 'Escape') {
      e.preventDefault()
      this.closeMenu()
      return true
    }
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      menu.keyboard = true
      const n = this.options().length
      if (n) this.highlight(menu.active < 0 ? (e.key === 'ArrowDown' ? 0 : n - 1) : menu.active + (e.key === 'ArrowDown' ? 1 : -1))
      return true
    }
    if (e.key === 'Enter') {
      const target = this.options()[menu.active]
      if (!target) return false
      // Never let this Enter submit the form, even when the pick is refused.
      e.preventDefault()
      const r = target.getBoundingClientRect()
      this.activate(target, { x: r.left + r.width / 2, y: r.top + r.height / 2 })
      return true
    }
    return false
  }

  private readonly rows = new WeakMap<Element, LoginMatch>()
  private readonly actions = new WeakMap<Element, () => void>()

  private message(text: string): HTMLElement {
    const p = this.doc.createElement('div')
    p.className = 'msg'
    p.textContent = text
    return p
  }

  /** The page replaced the menu's field (a re-render): the menu follows the new one. */
  retarget(field: HTMLInputElement): void {
    if (!this.menu || this.menu.field === field) return
    this.menu.field = field
    this.placeMenu()
  }

  private placeMenu(): void {
    const menu = this.menu
    const win = this.doc.defaultView
    if (!menu || !win) return
    // A field the page just removed has no box: stay put until it is re-found (retarget).
    if (!menu.field.isConnected) return
    const r = menu.field.getBoundingClientRect()
    const rtl = win.getComputedStyle(menu.field).direction === 'rtl'
    const left = rtl ? r.right - MENU_WIDTH : r.left
    menu.el.style.left = `${Math.round(Math.max(4, Math.min(left, win.innerWidth - MENU_WIDTH - 4)))}px`
    const below = r.bottom + MENU_GAP
    const height = menu.el.offsetHeight
    const top = below + height > win.innerHeight && r.top - MENU_GAP - height > 0 ? r.top - MENU_GAP - height : below
    menu.el.style.top = `${Math.round(top)}px`
  }

  /**
   * The clickjacking gate for menu clicks: a real (trusted) click, at least MENU_ARM_MS
   * after the menu appeared, on a host the page has not restyled or covered.
   */
  private onMenuClick(e: MouseEvent): void {
    if (!this.menu || !e.isTrusted) return
    const target = (e.composedPath()[0] as Element).closest?.('.row, .action')
    if (target) this.activate(target, { x: e.clientX, y: e.clientY })
  }

  /** Runs a row or button after a trusted click or Enter at `point` (for Enter, the row's
   *  centre), when the menu is armed and its host intact (see onMenuClick). */
  private activate(target: Element, point: { x: number; y: number }): void {
    const menu = this.menu
    if (!menu || menu.el.classList.contains('pending')) return
    if (performance.now() - menu.shownAt < MENU_ARM_MS) return
    // IntersectionObserver v2 reports any filter or transform on <html>, or a menu partly
    // outside the viewport, as "not visible" even when nothing covers it. Only then does a
    // click count without it: the hit test at the click point must still find our host and
    // the menu must have been up for MENU_ARM_MS (checked above). A cover that lets clicks
    // through (`pointer-events: none`) on an ordinary page is still refused.
    let ioVisible = supportsOcclusion() ? this.menuVisible === true : null
    if (ioVisible === false && occlusionMayMisreport(this.doc, menu.el)) ioVisible = null
    if (!isHostIntact(this.host, point, ioVisible)) {
      this.closeMenu()
      return
    }
    const action = this.actions.get(target)
    if (action) return action()
    const item = this.rows.get(target)
    if (item) menu.handlers.onPick(item)
  }

  private onOutside(e: MouseEvent): void {
    if (this.menu && !e.composedPath().includes(this.host)) this.closeMenu()
  }
}
