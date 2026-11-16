// @vitest-environment happy-dom
// The in-page menu from the keyboard (overlay.ts handleKey): ArrowDown / ArrowUp move a
// highlighted option (wrapping), Enter picks it once the menu is armed, Escape and Tab
// close it, and a key the page made up does nothing.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// Outside this package's root, which Vite's dev server won't serve; the styles don't matter here.
vi.mock('@/theme/tokens.css?raw', () => ({ default: '' }))
vi.mock('@ext/content/guard', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@ext/content/guard')>()),
  // happy-dom has no layout: the host's hit test is browser-only (e2e).
  isHostIntact: vi.fn(() => true),
  supportsOcclusion: () => false,
}))

import { isHostIntact } from '@ext/content/guard'
import { MENU_ARM_MS } from '@ext/content/layout'
import { Overlay } from '@ext/content/overlay'
import type { LoginMatch } from '@ext/shared/messages'

const ITEMS: LoginMatch[] = ['a', 'b', 'c'].map((id) => ({ id, title: `Site ${id}`, username: `${id}@example.com`, hasTotp: false }))

let roots: ShadowRoot[] = []
let now = 0
const original = Element.prototype.attachShadow

beforeEach(() => {
  roots = []
  now = 1000
  vi.spyOn(performance, 'now').mockImplementation(() => now)
  Element.prototype.attachShadow = function (init: ShadowRootInit) {
    const root = original.call(this, init)
    roots.push(root)
    return root
  }
  document.body.innerHTML = '<form><input id="u" name="username"></form>'
  vi.mocked(isHostIntact).mockReturnValue(true)
})

afterEach(() => {
  Element.prototype.attachShadow = original
  vi.restoreAllMocks()
})

function key(k: string, trusted = true, init: KeyboardEventInit = {}): KeyboardEvent {
  const e = new KeyboardEvent('keydown', { key: k, bubbles: true, cancelable: true, ...init })
  Object.defineProperty(e, 'isTrusted', { value: trusted })
  return e
}

function setup(opts: { keyboard?: boolean } = {}) {
  const field = document.getElementById('u') as HTMLInputElement
  const overlay = new Overlay(document, () => 'en', () => {})
  const onPick = vi.fn()
  overlay.openMenu(field, { onPick }, opts)
  overlay.setMenu({ kind: 'items', items: ITEMS })
  const root = roots[roots.length - 1]
  const options = () => [...root.querySelectorAll<HTMLElement>('[role="option"]')]
  const selected = () => options().findIndex((o) => o.getAttribute('aria-selected') === 'true')
  return { field, overlay, onPick, root, options, selected }
}

describe('menu keyboard', () => {
  it('opened from the keyboard, highlights the first row once the rows arrive', () => {
    const { overlay, selected, root, options } = setup({ keyboard: true })
    expect(overlay.activeIndex()).toBe(0)
    expect(selected()).toBe(0)
    expect(root.querySelector('[role="listbox"]')!.getAttribute('aria-activedescendant')).toBe(options()[0].id)
    expect(options()[0].classList.contains('active')).toBe(true)
    expect(root.querySelector('[aria-live]')!.textContent).toBe('Site a, a@example.com')
  })

  it('opened by a click, highlights nothing until an arrow key', () => {
    const { overlay, selected } = setup()
    expect(selected()).toBe(-1)
    expect(overlay.handleKey(key('ArrowDown'))).toBe(true)
    expect(selected()).toBe(0)
  })

  it('ArrowDown and ArrowUp move the highlight and wrap around', () => {
    const { overlay, selected, options } = setup({ keyboard: true })
    overlay.handleKey(key('ArrowDown'))
    overlay.handleKey(key('ArrowDown'))
    expect(selected()).toBe(2)
    overlay.handleKey(key('ArrowDown'))
    expect(selected()).toBe(0)
    overlay.handleKey(key('ArrowUp'))
    expect(selected()).toBe(2)
    expect(options().filter((o) => o.getAttribute('aria-selected') === 'true')).toHaveLength(1)
  })

  it('Enter picks the highlighted row, without submitting the form', () => {
    const { overlay, onPick } = setup({ keyboard: true })
    overlay.handleKey(key('ArrowDown'))
    now += MENU_ARM_MS
    const enter = key('Enter')
    expect(overlay.handleKey(enter)).toBe(true)
    expect(enter.defaultPrevented).toBe(true)
    expect(onPick).toHaveBeenCalledWith(ITEMS[1])
  })

  it('Enter in the first 500 ms picks nothing (and still does not submit)', () => {
    const { overlay, onPick } = setup({ keyboard: true })
    now += MENU_ARM_MS - 1
    const enter = key('Enter')
    expect(overlay.handleKey(enter)).toBe(true)
    expect(enter.defaultPrevented).toBe(true)
    expect(onPick).not.toHaveBeenCalled()
  })

  it('Enter with no highlight is left to the page', () => {
    const { overlay, onPick } = setup()
    now += MENU_ARM_MS
    const enter = key('Enter')
    expect(overlay.handleKey(enter)).toBe(false)
    expect(enter.defaultPrevented).toBe(false)
    expect(onPick).not.toHaveBeenCalled()
  })

  it('Enter on a restyled or covered menu closes it and picks nothing', () => {
    const { overlay, onPick } = setup({ keyboard: true })
    now += MENU_ARM_MS
    vi.mocked(isHostIntact).mockReturnValue(false)
    overlay.handleKey(key('Enter'))
    expect(onPick).not.toHaveBeenCalled()
    expect(overlay.isMenuOpen()).toBe(false)
  })

  it('Escape closes the menu; focus stays in the field', () => {
    const { overlay, field } = setup({ keyboard: true })
    field.focus()
    const esc = key('Escape')
    expect(overlay.handleKey(esc)).toBe(true)
    expect(esc.defaultPrevented).toBe(true)
    expect(overlay.isMenuOpen()).toBe(false)
    expect(document.activeElement).toBe(field)
  })

  it('Tab closes the menu and lets focus move on', () => {
    const { overlay } = setup({ keyboard: true })
    const tab = key('Tab')
    expect(overlay.handleKey(tab)).toBe(false)
    expect(tab.defaultPrevented).toBe(false)
    expect(overlay.isMenuOpen()).toBe(false)
  })

  it('a key the page dispatched does nothing', () => {
    const { overlay, onPick, selected, field } = setup({ keyboard: true })
    now += MENU_ARM_MS
    for (const k of ['ArrowDown', 'ArrowUp', 'Enter', 'Escape', 'Tab']) expect(overlay.handleKey(key(k, false))).toBe(false)
    // Nor does the document-level Escape.
    field.dispatchEvent(key('Escape', false))
    expect(selected()).toBe(0)
    expect(onPick).not.toHaveBeenCalled()
    expect(overlay.isMenuOpen()).toBe(true)
  })

  it('keys do nothing while a fill runs with the menu hidden', () => {
    const { overlay, onPick } = setup({ keyboard: true })
    now += MENU_ARM_MS
    overlay.hideMenu()
    expect(overlay.handleKey(key('Enter'))).toBe(false)
    expect(onPick).not.toHaveBeenCalled()
  })

  it('the message button (Unlock) is reachable from the keyboard', () => {
    const { overlay } = setup({ keyboard: true })
    const run = vi.fn()
    overlay.setMenu({ kind: 'message', key: 'ext.page.locked', action: { key: 'ext.page.unlock', run } })
    expect(overlay.activeIndex()).toBe(0)
    now += MENU_ARM_MS
    overlay.handleKey(key('Enter'))
    expect(run).toHaveBeenCalledOnce()
  })
})
