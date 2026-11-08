import { describe, expect, it } from 'vitest'

import { USERNAME_TTL_MS, UsernameMemory, type SessionArea } from '../../src/background/usernames'

/** storage.session as a Map: what survives is what the browser keeps, not the worker. */
function area(): SessionArea & { data: Map<string, unknown> } {
  const data = new Map<string, unknown>()
  return {
    data,
    get: async (k) => (data.has(k) ? { [k]: structuredClone(data.get(k)) } : {}),
    set: async (items) => void Object.entries(items).forEach(([k, v]) => data.set(k, structuredClone(v))),
    remove: async (k) => void data.delete(k),
  }
}

describe('multi-step username memory', () => {
  it('survives the service worker being replaced', async () => {
    const storage = area()
    await new UsernameMemory(storage).remember(7, 'example.com', 'me@example.com')
    // A stopped worker comes back with fresh module state; only storage.session remains.
    const restarted = new UsernameMemory(storage)
    expect(await restarted.recall(7, 'example.com')).toBe('me@example.com')
  })

  it('is per tab and per site', async () => {
    const memory = new UsernameMemory(area())
    await memory.remember(7, 'example.com', 'me')
    expect(await memory.recall(8, 'example.com')).toBeUndefined()
    expect(await memory.recall(7, 'evil.com')).toBeUndefined()
  })

  it('expires, and is forgotten when the tab leaves the site', async () => {
    let now = 1_000
    const storage = area()
    const memory = new UsernameMemory(storage, () => now)
    await memory.remember(7, 'example.com', 'me')
    now += USERNAME_TTL_MS + 1
    expect(await memory.recall(7, 'example.com')).toBeUndefined()

    now = 1_000
    await memory.remember(7, 'example.com', 'me')
    await memory.leftTo(7, 'example.com')
    expect(await memory.recall(7, 'example.com')).toBe('me')
    await memory.leftTo(7, 'other.org')
    expect(storage.data.size).toBe(0)
  })

  it('keeps nothing for an empty username', async () => {
    const storage = area()
    await new UsernameMemory(storage).remember(7, 'example.com', '')
    expect(storage.data.size).toBe(0)
  })
})
