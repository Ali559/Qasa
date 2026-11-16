// R5 (docs/autofill-hardening.md): a fill started from the in-page menu never closes the
// menu as if it worked.
import type { Overlay } from './overlay'

/** How a fill ended: true when the values went in, false when they could not (a covered
 *  or missing field), 'cancelled' when the user said no to a card's confirmation. */
export type FillOutcome = boolean | 'cancelled'

type MenuLike = Pick<Overlay, 'hideMenu' | 'showMenu' | 'closeMenu' | 'isMenuOpen' | 'setMenu'>

/**
 * Runs a fill with the menu out of the way (so it can't stand in for a covered field in
 * the visibility checks), then closes it, or, when nothing could be filled, shows it again
 * saying so: "Couldn't fill", "Not filled." for a declined card, or the generic error when
 * the fill threw. A menu the user closed meanwhile stays closed. The fill may show the menu
 * itself while it waits (a card's confirmation).
 */
export async function fillFromMenu(overlay: MenuLike, fill: () => Promise<FillOutcome> | FillOutcome): Promise<void> {
  overlay.hideMenu()
  let key = 'ext.page.fillFailed'
  try {
    const outcome = await fill()
    if (outcome === true) {
      overlay.closeMenu()
      return
    }
    if (outcome === 'cancelled') key = 'ext.page.cancelled'
  } catch {
    key = 'ext.page.error'
  }
  if (!overlay.isMenuOpen()) return
  overlay.showMenu()
  overlay.setMenu({ kind: 'message', key })
}
