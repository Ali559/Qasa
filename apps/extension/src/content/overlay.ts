// The in-page UI: a Qasa icon inside each detected field and the menu it opens. Both are
// drawn in a CLOSED shadow root on one host element attached to <html>, so the page can't
// read or script them; guard.ts checks the host was not restyled or covered before a menu
// click is acted on.
import { QASA_MARK_SHAPES, QASA_MARK_TRANSFORM, QASA_MARK_VIEWBOX } from '@/brand/qasaMark'
import { siteLogo } from '@/vault/present'
import { siteIconPath } from '@/vault/siteIcon'
import type { LoginMatch } from '@ext/shared/messages'
import { HOST_STYLE, isHostIntact, supportsOcclusion } from './guard'
import { ct, type ContentLanguage } from './i18n'
import { PLEX } from './fonts'
import TOKENS from '@/theme/tokens.css?raw'
import { FONT_WAIT_MS, ICON_INSET, ICON_SIZE, MENU_ARM_MS, MENU_GAP, MENU_HEADER, MENU_PADDING, MENU_WIDTH, ROW_HEIGHT } from './layout'

export type MenuState =
  | { kind: 'loading' }
  | { kind: 'items'; items: LoginMatch[] }
  | { kind: 'message'; key: string; args?: Array<string | number>; action?: { key: string; run: () => void } }

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
.row:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
.tile {
  width: var(--size-icon-badge); height: var(--size-icon-badge); flex-shrink: 0; border-radius: var(--radius-icon-badge);
  display: grid; place-items: center; color: #fff; font-weight: 700; font-size: calc(var(--size-icon-badge) * 0.375);
}
.tile.site { background: var(--card-muted); color: var(--text-primary); }
.tile.site svg { width: var(--size-icon-badge-glyph); height: var(--size-icon-badge-glyph); }
.text { display: flex; flex-direction: column; gap: var(--space-row-text-gap); min-width: 0; }
.title, .sub { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title { font-size: var(--type-row-title-size); font-weight: var(--type-row-title-weight); }
.sub { color: var(--text-secondary); font-size: var(--type-row-subtitle-size); font-weight: var(--type-row-subtitle-weight); }
.msg { padding: 8px 10px 10px; color: var(--text-secondary); }
/* iOS \`QasaButtonStyle(.primary)\`. */
.action {
  display: flex; align-items: center; justify-content: center;
  width: calc(100% - 20px); height: var(--size-button-height); margin: 0 10px 10px; padding: 0 var(--space-button-horizontal);
  border: 0; border-radius: var(--radius-control); background: var(--accent); color: var(--on-accent);
  font: inherit; font-size: var(--type-button-size); font-weight: var(--type-button-weight); white-space: nowrap; cursor: pointer;
  transition: opacity var(--motion-press), transform var(--motion-press);
}
.action:active { opacity: var(--motion-pressed-opacity); transform: scale(var(--motion-press-scale)); }
.action:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
/* Hidden until the Plex faces are in, so the menu never paints in a system font. */
.menu.pending { visibility: hidden; }
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
