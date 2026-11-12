// The first launch's decisions, kept apart from the screens so they can be tested
// (design/README.md rule 6d; iOS `WelcomeFlowView`, `OnboardingTourView`).

/** Vault, sign-up, two-step code, password health. */
export const TOUR_PAGE_COUNT = 4

/** A horizontal move past this many pixels is a swipe, not a click (as iOS). */
export const SWIPE_THRESHOLD = 40

/** Where a move by `delta` pages lands: a page, `done` past the last one, or `stay` before the first. */
export function advance(page: number, delta: number, count = TOUR_PAGE_COUNT): number | 'done' | 'stay' {
  const next = page + delta
  if (next >= count) return 'done'
  if (next < 0) return 'stay'
  return next
}

/**
 * Which way a pointer gesture on the tour goes: +1 forward, -1 back, 0 nothing. A swipe
 * towards the leading edge goes forward (finger moves the page along); a click on the
 * trailing half goes forward and the leading half back. Both are mirrored in RTL.
 */
export function gestureDelta(input: {
  dx: number
  dy: number
  x: number
  left: number
  width: number
  rtl: boolean
}): -1 | 0 | 1 {
  const dx = input.dx * (input.rtl ? -1 : 1)
  if (dx < -SWIPE_THRESHOLD) return 1
  if (dx > SWIPE_THRESHOLD) return -1
  if (Math.abs(input.dy) >= 20) return 0
  const rightHalf = input.x > input.left + input.width / 2
  // The trailing half is the right one in LTR, the left one in RTL.
  return rightHalf !== input.rtl ? 1 : -1
}

/** The arrow keys, in reading direction: the one pointing at the trailing edge goes forward. */
export function keyDelta(key: string, rtl: boolean): -1 | 0 | 1 {
  if (key === 'ArrowRight') return rtl ? -1 : 1
  if (key === 'ArrowLeft') return rtl ? 1 : -1
  return 0
}

/** As iOS `AppPreferences.userName`: kept trimmed; blank means no name. */
export function cleanName(name: string): string {
  return name.trim()
}

/** The health demo's number while the gauge sweeps: ease-out, as `motion.gauge`. */
export function gaugeValue(target: number, progress: number): number {
  const p = Math.min(Math.max(progress, 0), 1)
  return Math.round(target * (1 - (1 - p) ** 2))
}
