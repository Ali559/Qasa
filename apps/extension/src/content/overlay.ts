// The in-page UI: a Qasa icon inside each detected field and the menu it opens. Both are
// drawn in a CLOSED shadow root on one host element attached to <html>, so the page can't
// read or script them; guard.ts checks the host was not restyled or covered before a menu
// click is acted on.
import { siteLogo } from '@/vault/present'
import { siteIconPath } from '@/vault/siteIcon'
import type { LoginMatch } from '@ext/shared/messages'
import { HOST_STYLE, isHostIntact, supportsOcclusion } from './guard'
import { ct, type ContentLanguage } from './i18n'
import { RUBIK, RUDAW } from './fonts'
import { ICON_INSET, ICON_SIZE, MENU_ARM_MS, MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from './layout'

export type MenuState =
  | { kind: 'loading' }
  | { kind: 'items'; items: LoginMatch[] }
  | { kind: 'message'; key: string; action?: { key: string; run: () => void } }

const CSS = `
:host { all: initial; }
* { box-sizing: border-box; }
.icon, .menu {
  --accent: #7C6CF2; --card: #FFFFFF; --stroke: #EBE8F4; --text: #24213A; --text2: #6B6785; --muted: #F1EEFA;
  font-family: '${RUBIK}', system-ui, -apple-system, 'Segoe UI', sans-serif;
}
.menu:lang(ar), .menu:lang(ckb) { font-family: '${RUDAW}', '${RUBIK}', 'Noto Naskh Arabic', 'Geeza Pro', system-ui, sans-serif; }
@media (prefers-color-scheme: dark) {
  .icon, .menu { --accent: #A79BFF; --card: #221F3A; --stroke: #332F4F; --text: #EEECF8; --text2: #B5B1CC; --muted: #2A2745; }
}
.icon {
  position: fixed; width: ${ICON_SIZE}px; height: ${ICON_SIZE}px; padding: 0; margin: 0;
  border: 0; border-radius: 6px; background: transparent; color: var(--accent);
  display: grid; place-items: center; cursor: pointer; pointer-events: auto;
}
.icon:hover { background: color-mix(in srgb, var(--accent) 14%, transparent); }
.icon svg { width: 16px; height: 18px; }
.menu {
  position: fixed; width: ${MENU_WIDTH}px; padding: ${MENU_PADDING}px; margin: 0;
  background: var(--card); color: var(--text); border: 1px solid var(--stroke); border-radius: 16px;
  box-shadow: 0 10px 30px rgb(0 0 0 / 0.18); pointer-events: auto; font-size: 14px; line-height: 1.3;
}
.head { height: ${MENU_HEADER}px; display: flex; align-items: center; gap: 8px; padding-inline: 10px; color: var(--accent); font-weight: 700; font-size: 15px; }
.head svg { width: 14px; height: 16px; }
.row {
  height: ${ROW_HEIGHT}px; width: 100%; display: flex; align-items: center; gap: 12px; padding: 0 10px;
  border: 0; border-radius: 12px; background: transparent; color: inherit; font: inherit; text-align: start; cursor: pointer;
}
.row:hover, .row:focus-visible { background: var(--muted); outline: none; }
.tile { width: 32px; height: 32px; flex-shrink: 0; border-radius: 9px; display: grid; place-items: center; color: #fff; font-weight: 700; font-size: 13px; }
.tile.site { background: var(--muted); color: var(--text); }
.tile.site svg { width: 16px; height: 16px; }
.text { display: flex; flex-direction: column; min-width: 0; }
.title, .sub { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title { font-weight: 600; }
.sub { color: var(--text2); font-size: 13px; }
.msg { padding: 8px 10px 10px; color: var(--text2); }
.action { margin: 0 10px 8px; padding: 8px 14px; border: 0; border-radius: 999px; background: var(--accent); color: #fff; font: inherit; font-weight: 600; cursor: pointer; }
`

/** A filled 24×24 icon from one path. Built with DOM calls (no innerHTML), so pages with
 *  Trusted Types don't interfere. */
function glyph(doc: Document, d: string): SVGSVGElement {
  const ns = 'http://www.w3.org/2000/svg'
  const svg = doc.createElementNS(ns, 'svg')
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('fill', 'currentColor')
  svg.setAttribute('aria-hidden', 'true')
  const path = doc.createElementNS(ns, 'path')
  path.setAttribute('d', d)
  svg.append(path)
  return svg
}

/** Lucide `shield`, filled, as in the desktop's BrandHeader. */
function shield(doc: Document): SVGSVGElement {
  return glyph(
    doc,
    'M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z',
  )
}

export interface MenuHandlers {
  /** A login row was picked by a click that passed every check. */
  onPick(item: LoginMatch): void
}

export class Overlay {
  private readonly host: HTMLElement
  private readonly root: ShadowRoot
  private readonly icons = new Map<HTMLInputElement, HTMLButtonElement>()
  private menu: { el: HTMLElement; field: HTMLInputElement; shownAt: number; handlers: MenuHandlers } | null = null
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
    doc.addEventListener('keydown', (e) => e.key === 'Escape' && this.closeMenu(), true)
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
    if (this.host.parentNode !== this.doc.documentElement) this.doc.documentElement.append(this.host)
    return true
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
      icon.append(shield(this.doc))
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

  openMenu(field: HTMLInputElement, handlers: MenuHandlers): void {
    this.closeMenu()
    if (!this.ensureAttached()) return
    // Last child of <html>, so later page elements with the same z-index can't simply sit on top.
    this.doc.documentElement.append(this.host)
    const el = this.doc.createElement('div')
    el.className = 'menu'
    el.setAttribute('role', 'menu')
    this.root.append(el)
    this.menu = { el, field, shownAt: performance.now(), handlers }
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
    this.menu = null
  }

  setMenu(state: MenuState): void {
    const menu = this.menu
    if (!menu) return
    const lang = this.language()
    menu.el.lang = lang
    menu.el.dir = lang === 'en' ? 'ltr' : 'rtl'
    menu.el.replaceChildren()
    const head = this.doc.createElement('div')
    head.className = 'head'
    head.append(shield(this.doc), ct(lang, 'brand.name'))
    menu.el.append(head)
    if (state.kind === 'loading') {
      menu.el.append(this.message('…'))
    } else if (state.kind === 'message') {
      menu.el.append(this.message(ct(lang, state.key)))
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
    } else {
      for (const item of state.items) {
        const row = this.doc.createElement('button')
        row.type = 'button'
        row.className = 'row'
        row.setAttribute('role', 'menuitem')
        const tile = this.doc.createElement('span')
        tile.className = 'tile'
        const iconPath = siteIconPath(item.website)
        if (iconPath) {
          tile.classList.add('site')
          tile.append(glyph(this.doc, iconPath))
        } else {
          const logo = siteLogo(item.title)
          tile.style.background = logo.color
          tile.textContent = logo.letter
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
        menu.el.append(row)
      }
    }
    this.placeMenu()
  }

  private readonly rows = new WeakMap<Element, LoginMatch>()
  private readonly actions = new WeakMap<Element, () => void>()

  private message(text: string): HTMLElement {
    const p = this.doc.createElement('div')
    p.className = 'msg'
    p.textContent = text
    return p
  }

  private placeMenu(): void {
    const menu = this.menu
    const win = this.doc.defaultView
    if (!menu || !win) return
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
    const menu = this.menu
    if (!menu || !e.isTrusted) return
    const target = (e.composedPath()[0] as Element).closest?.('.row, .action')
    if (!target) return
    if (performance.now() - menu.shownAt < MENU_ARM_MS) return
    const ioVisible = supportsOcclusion() ? this.menuVisible === true : null
    if (!isHostIntact(this.host, { x: e.clientX, y: e.clientY }, ioVisible)) {
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
