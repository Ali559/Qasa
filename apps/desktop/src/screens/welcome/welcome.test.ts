import { describe, expect, it } from 'vitest'

import { advance, cleanName, gaugeValue, gestureDelta, keyDelta, TOUR_PAGE_COUNT } from './welcome'

describe('advance', () => {
  it('moves within the pages', () => {
    expect(advance(0, 1)).toBe(1)
    expect(advance(2, -1)).toBe(1)
  })

  it('finishes past the last page and stays before the first', () => {
    expect(advance(TOUR_PAGE_COUNT - 1, 1)).toBe('done')
    expect(advance(0, -1)).toBe('stay')
  })
})

describe('gestureDelta', () => {
  const at = (x: number, rtl: boolean, dx = 0, dy = 0) => gestureDelta({ dx, dy, x, left: 100, width: 400, rtl })

  it('clicks the trailing half forward and the leading half back', () => {
    expect(at(450, false)).toBe(1)
    expect(at(150, false)).toBe(-1)
  })

  it('mirrors the halves in RTL', () => {
    expect(at(450, true)).toBe(-1)
    expect(at(150, true)).toBe(1)
  })

  it('swipes towards the leading edge to go forward', () => {
    expect(at(300, false, -60)).toBe(1)
    expect(at(300, false, 60)).toBe(-1)
    expect(at(300, true, 60)).toBe(1)
    expect(at(300, true, -60)).toBe(-1)
  })

  it('ignores a vertical drag', () => {
    expect(at(450, false, 5, 40)).toBe(0)
  })
})

describe('keyDelta', () => {
  it('follows the reading direction', () => {
    expect(keyDelta('ArrowRight', false)).toBe(1)
    expect(keyDelta('ArrowLeft', false)).toBe(-1)
    expect(keyDelta('ArrowRight', true)).toBe(-1)
    expect(keyDelta('ArrowLeft', true)).toBe(1)
    expect(keyDelta('Enter', false)).toBe(0)
  })
})

describe('cleanName', () => {
  it('trims, and blank is no name', () => {
    expect(cleanName('  Sara \n')).toBe('Sara')
    expect(cleanName('   ')).toBe('')
  })
})

describe('gaugeValue', () => {
  it('sweeps from 0 to the target', () => {
    expect(gaugeValue(82, 0)).toBe(0)
    expect(gaugeValue(82, 1)).toBe(82)
    expect(gaugeValue(82, 0.5)).toBeGreaterThan(41)
    expect(gaugeValue(82, 2)).toBe(82)
  })
})
