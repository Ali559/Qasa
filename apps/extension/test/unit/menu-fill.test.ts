// R5 (docs/autofill-hardening.md): a fill from the in-page menu never closes it as if it
// worked. Logins, strong passwords, codes, cards and profiles all go through fillFromMenu.
import { describe, expect, it } from 'vitest'

import { fillFromMenu, type FillOutcome } from '@ext/content/menu-fill'
import type { MenuState } from '@ext/content/overlay'

function fakeMenu() {
  const log: string[] = []
  let open = true
  let state: MenuState | null = null
  return {
    log,
    get state() {
      return state
    },
    closeByUser: () => (open = false),
    menu: {
      hideMenu: () => void log.push('hide'),
      showMenu: () => void log.push('show'),
      closeMenu: () => {
        log.push('close')
        open = false
      },
      isMenuOpen: () => open,
      setMenu: (s: MenuState) => {
        log.push('set')
        state = s
      },
    },
  }
}

const message = (s: MenuState | null) => (s?.kind === 'message' ? s.key : null)

describe('fillFromMenu', () => {
  it('hides the menu while filling and closes it on success', async () => {
    const m = fakeMenu()
    await fillFromMenu(m.menu, async () => {
      expect(m.log).toEqual(['hide'])
      return true
    })
    expect(m.log).toEqual(['hide', 'close'])
  })

  it.each<[string, () => FillOutcome | Promise<FillOutcome>, string]>([
    ['a field that could not be filled', () => false, 'ext.page.fillFailed'],
    ['a card the user declined on the desktop', async () => 'cancelled' as const, 'ext.page.cancelled'],
    [
      'a fill that threw',
      () => {
        throw new Error('bad')
      },
      'ext.page.error',
    ],
  ])('%s brings the menu back saying so', async (_, fill, key) => {
    const m = fakeMenu()
    await fillFromMenu(m.menu, fill)
    expect(m.log).toEqual(['hide', 'show', 'set'])
    expect(message(m.state)).toBe(key)
  })

  it('leaves a menu the user closed during the fill closed', async () => {
    const m = fakeMenu()
    await fillFromMenu(m.menu, async () => {
      m.closeByUser()
      return false
    })
    expect(m.log).toEqual(['hide'])
  })
})
