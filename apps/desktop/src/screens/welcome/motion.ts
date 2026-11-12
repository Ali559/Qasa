// What the tour's demos share: the motion tokens read from tokens.css, the reduce-motion
// setting, and timers that stop when the page leaves.
import { onBeforeUnmount } from 'vue'

/** A unitless seconds token (`--motion-tour-dwell` …) as milliseconds. */
export function tokenMs(name: string, fallbackSeconds: number): number {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const seconds = Number.parseFloat(raw)
  return (Number.isFinite(seconds) ? seconds : fallbackSeconds) * 1000
}

/** A duration token with a unit (`--motion-gauge-duration: 1100ms`) in milliseconds. */
export function durationMs(name: string, fallbackMs: number): number {
  const raw = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  const value = Number.parseFloat(raw)
  if (!Number.isFinite(value)) return fallbackMs
  return raw.endsWith('ms') ? value : value * 1000
}

export function prefersReducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** `later(ms, fn)` and `frame(fn)`, all cancelled when the component unmounts. */
export function useTimers() {
  const timeouts = new Set<ReturnType<typeof setTimeout>>()
  const frames = new Set<number>()

  function later(ms: number, fn: () => void): void {
    const id = setTimeout(() => {
      timeouts.delete(id)
      fn()
    }, ms)
    timeouts.add(id)
  }

  function frame(fn: (now: number) => void): void {
    const id = requestAnimationFrame((now) => {
      frames.delete(id)
      fn(now)
    })
    frames.add(id)
  }

  function clear(): void {
    timeouts.forEach(clearTimeout)
    frames.forEach(cancelAnimationFrame)
    timeouts.clear()
    frames.clear()
  }

  onBeforeUnmount(clear)
  return { later, frame, clear }
}
