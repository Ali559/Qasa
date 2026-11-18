// @vitest-environment happy-dom
// R7 (docs/autofill-hardening.md): a password field R1 doesn't call new never shows
// nothing. No matching login: "Use a strong password" first; logins: the logins, then the
// strong password last. A new field keeps the strong password only (index.ts, e2e), and on a
// login the strong password goes into that one field (constraints.ts strongPasswordFields).
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/theme/tokens.css?raw', () => ({ default: '' }))
vi.mock('@ext/content/guard', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@ext/content/guard')>()),
  supportsOcclusion: () => false,
}))

import { generateConstraints, strongPasswordFields } from '@ext/content/constraints'
import { detect } from '@ext/content/detect'
import { loginPasswordMenu } from '@ext/content/menu-fill'
import { Overlay, type MenuState } from '@ext/content/overlay'
import type { LoginMatch } from '@ext/shared/messages'

const ITEMS: LoginMatch[] = ['a', 'b'].map((id) => ({ id, title: `Site ${id}`, username: `${id}@example.com`, hasTotp: false }))
const suggest = { password: 'Gen-3rated-Pass!', run: () => {} }

/** The option rows a menu state renders, as their visible text. */
function rendered(state: MenuState): string[] {
  document.body.innerHTML = '<form><input id=p type=password></form>'
  const original = Element.prototype.attachShadow
  let root: ShadowRoot | undefined
  Element.prototype.attachShadow = function (init: ShadowRootInit) {
    root = original.call(this, init)
    return root
  }
  try {
    const overlay = new Overlay(document, () => 'en', () => {})
    overlay.openMenu(document.getElementById('p') as HTMLInputElement, { onPick: () => {} })
    overlay.setMenu(state)
    return [...root!.querySelectorAll('[role="option"]')].map((o) => o.querySelector('.title')?.textContent ?? '')
  } finally {
    Element.prototype.attachShadow = original
  }
}

describe('R7 menu of a login password field', () => {
  it('no matching login: the strong password first (and alone)', () => {
    const state = loginPasswordMenu([], suggest)
    expect(state).toMatchObject({ kind: 'suggest', password: suggest.password })
    expect(rendered(state)).toEqual(['Use a strong password'])
  })

  it('matching logins: the logins, then the strong password last', () => {
    const state = loginPasswordMenu(ITEMS, suggest)
    expect(state).toMatchObject({ kind: 'items', items: ITEMS, suggest })
    expect(rendered(state)).toEqual(['Site a', 'Site b', 'Use a strong password'])
  })

  it('the generator failed: the logins, or "no matches", as before', () => {
    expect(loginPasswordMenu(ITEMS, null)).toEqual({ kind: 'items', items: ITEMS })
    expect(loginPasswordMenu([], null)).toEqual({ kind: 'message', key: 'ext.page.noMatches' })
    expect(rendered({ kind: 'items', items: ITEMS })).toEqual(['Site a', 'Site b'])
  })
})

describe('R7 strong password on a login field', () => {
  it('goes into that field only, with its constraints', () => {
    document.body.innerHTML = '<form><input name=email type=email><input id=p type=password maxlength=14><input id=o type=password style="display:none"><button>تسجيل الدخول</button></form>'
    const [group] = detect(document).groups
    expect(group.kind).toBe('login')
    expect(strongPasswordFields(group)).toEqual([document.getElementById('p')])
    expect(generateConstraints(group)).toEqual({ maxLength: 14 })
  })

  it('a new field keeps new + confirm', () => {
    document.body.innerHTML = '<form><input id=p type=password autocomplete=new-password><input id=c type=password></form>'
    const [group] = detect(document).groups
    expect(strongPasswordFields(group)).toEqual([document.getElementById('p'), document.getElementById('c')])
  })
})
