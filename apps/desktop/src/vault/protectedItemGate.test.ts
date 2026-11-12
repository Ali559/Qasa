// A protected note never shows without the unlock and a click on the eye (design/README.md
// rule 6b, TODO Phase 4). The same cases as iOS `ProtectedItemGateTests`.
import { describe, expect, it } from 'vitest'

import { ProtectedItemGate } from './protectedItemGate'

describe('ProtectedItemGate', () => {
  it('starts locked and hides everything when protected', () => {
    const gate = new ProtectedItemGate(true)
    expect(gate.showsItem).toBe(false)
    expect(gate.isHidden('Title')).toBe(true)
    expect(gate.isHidden('Content')).toBe(true)
  })

  it('shows the item on unlock but keeps values hidden', () => {
    const gate = new ProtectedItemGate(true)
    gate.unlock()
    expect(gate.showsItem).toBe(true)
    expect(gate.isHidden('Content')).toBe(true)
  })

  it('reveals one value at a time with the eye', () => {
    const gate = new ProtectedItemGate(true)
    gate.unlock()
    gate.toggle('Content')
    expect(gate.isHidden('Content')).toBe(false)
    expect(gate.isHidden('Title')).toBe(true)
    gate.toggle('Content')
    expect(gate.isHidden('Content')).toBe(true)
  })

  it('ignores the eye while locked', () => {
    const gate = new ProtectedItemGate(true)
    gate.toggle('Content')
    expect(gate.isHidden('Content')).toBe(true)
    expect(gate.showsItem).toBe(false)
  })

  it('hides revealed values on leaving', () => {
    const gate = new ProtectedItemGate(true)
    gate.unlock()
    gate.toggle('Content')
    gate.leave()
    expect(gate.isHidden('Content')).toBe(true)
  })

  it('locks again in the background', () => {
    const gate = new ProtectedItemGate(true)
    gate.unlock()
    gate.toggle('Content')
    gate.lock()
    expect(gate.showsItem).toBe(false)
    expect(gate.isHidden('Content')).toBe(true)
  })

  it('never gates an ordinary item', () => {
    const gate = new ProtectedItemGate(false)
    expect(gate.showsItem).toBe(true)
    expect(gate.isHidden('Password')).toBe(false)
    gate.lock()
    expect(gate.showsItem).toBe(true)
  })
})
