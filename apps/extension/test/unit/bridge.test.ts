// The service worker's bridge against an in-memory desktop that speaks §8.2.1.
import { describe, expect, it, vi } from 'vitest'

import { Bridge, BridgeError, type PairingStore, type PortLike, type StoredPairing } from '@ext/background/bridge'
import { derivePairing, fromB64, importSessionKey, newPairingKeys, open, seal, toB64, type ReqFrame } from '@ext/shared/protocol'

type Desktop = (frame: Record<string, unknown>, reply: (f: unknown) => void) => void | Promise<void>

/** A port whose other end is `desktop`; `disconnect()` from the far side via `drop()`. */
function fakePort(desktop: Desktop) {
  const listeners: Array<(m: unknown) => void> = []
  const closers: Array<() => void> = []
  const port: PortLike & { drop(): void; sent: unknown[] } = {
    sent: [],
    postMessage(m) {
      port.sent.push(m)
      void desktop(m as Record<string, unknown>, (f) => queueMicrotask(() => listeners.forEach((l) => l(f))))
    },
    disconnect() {},
    onMessage: { addListener: (fn) => listeners.push(fn) },
    onDisconnect: { addListener: (fn) => closers.push(fn) },
    drop: () => closers.forEach((c) => c()),
  }
  return port
}

function memoryStore(initial: StoredPairing | null = null): PairingStore & { value: StoredPairing | null } {
  return {
    value: initial,
    async load() {
      return this.value
    },
    async save(p) {
      this.value = p
    },
    async clear() {
      this.value = null
    },
  }
}

/** A desktop that pairs, then answers `status` with `state` (plus `extra` fields). */
function pairingDesktop(state: string, opts: { deny?: boolean; extra?: Record<string, unknown> } = {}) {
  const clients = new Map<string, CryptoKey>()
  const seen: Record<string, unknown>[] = []
  const desktop: Desktop = async (frame, reply) => {
    if (frame.type === 'pair') {
      const desk = await newPairingKeys()
      const extPub = fromB64(frame.pub as string)
      const secret = await derivePairing(desk.privateKey, extPub, desk.publicRaw, false)
      reply({ type: 'pairPending', pub: toB64(desk.publicRaw) })
      if (opts.deny) return reply({ type: 'pairDenied' })
      clients.set('client-1', await importSessionKey(secret.key))
      reply({ type: 'paired', clientId: 'client-1' })
      return
    }
    const req = frame as unknown as ReqFrame
    const key = clients.get(req.clientId)
    if (!key) return reply({ type: 'error', code: 'notPaired' })
    const body = JSON.parse(new TextDecoder().decode(await open(key, req.clientId, req.n, req.c)))
    seen.push(body)
    const answer = body.op === 'status' ? { id: body.id, ok: true, state, ...opts.extra } : { id: body.id, ok: false, error: 'locked' }
    reply({ type: 'res', ...(await seal(key, req.clientId, new TextEncoder().encode(JSON.stringify(answer)))) })
  }
  return { desktop, clients, seen }
}

describe('Bridge', () => {
  it('passes on that the main password is due, with its interval', async () => {
    const d = pairingDesktop('locked', { extra: { passwordDue: true, passwordIntervalDays: 14 } })
    const store = memoryStore()
    const bridge = new Bridge(() => fakePort(d.desktop), store, 'Chrome')
    await bridge.pair()
    await vi.waitFor(() => expect(store.value?.clientId).toBe('client-1'))
    expect(await bridge.status()).toEqual({ state: 'locked', passwordDueDays: 14 })
  })

  it('reports notPaired from the unpaired probe, pairs, then reads the status', async () => {
    const d = pairingDesktop('unlocked')
    const store = memoryStore()
    const bridge = new Bridge(() => fakePort(d.desktop), store, 'Chrome')
    expect(await bridge.status()).toEqual({ state: 'notPaired' })
    const code = await bridge.pair()
    expect(code).toMatch(/^\d{6}$/)
    await vi.waitFor(() => expect(store.value?.clientId).toBe('client-1'))
    expect(fromB64(store.value!.key)).toHaveLength(32)
    expect(await bridge.status()).toEqual({ state: 'unlocked' })
    expect(d.seen[0]).toMatchObject({ op: 'status' })
    expect(Math.abs((d.seen[0].ts as number) - Date.now())).toBeLessThan(5000)
  })

  it('shows the denial once after the desktop says no', async () => {
    const d = pairingDesktop('unlocked', { deny: true })
    const bridge = new Bridge(() => fakePort(d.desktop), memoryStore(), 'Chrome')
    await bridge.pair().catch(() => undefined)
    await vi.waitFor(async () => expect(await bridge.status()).toEqual({ state: 'notPaired', reason: 'denied' }))
    expect(await bridge.status()).toEqual({ state: 'notPaired' })
  })

  it('reports appNotRunning when nm-host answers in the clear', async () => {
    const bridge = new Bridge(() => fakePort((_f, reply) => reply({ type: 'error', code: 'appNotRunning' })), memoryStore(), 'Chrome')
    expect(await bridge.status()).toEqual({ state: 'appNotRunning' })
    await expect(bridge.pair()).rejects.toMatchObject({ code: 'appNotRunning' })
  })

  it('reports appNotRunning when the host is missing (port drops)', async () => {
    let port: ReturnType<typeof fakePort> | undefined
    const bridge = new Bridge(
      () => (port = fakePort(() => queueMicrotask(() => port!.drop()))),
      memoryStore(),
      'Chrome',
    )
    expect(await bridge.status()).toEqual({ state: 'appNotRunning' })
  })

  it('reopens the port after it drops', async () => {
    const d = pairingDesktop('locked')
    const ports: Array<ReturnType<typeof fakePort>> = []
    const bridge = new Bridge(() => {
      const p = fakePort(d.desktop)
      ports.push(p)
      return p
    }, memoryStore(), 'Chrome')
    await bridge.pair()
    await vi.waitFor(async () => expect(await bridge.status()).toEqual({ state: 'locked' }))
    ports[0].drop()
    expect(await bridge.status()).toEqual({ state: 'locked' })
    expect(ports).toHaveLength(2)
  })

  it('forgets the pairing when the desktop answers notPaired', async () => {
    const store = memoryStore({ clientId: 'revoked', key: toB64(new Uint8Array(32)) })
    const bridge = new Bridge(() => fakePort((_f, reply) => reply({ type: 'error', code: 'notPaired' })), store, 'Chrome')
    expect(await bridge.status()).toEqual({ state: 'notPaired' })
    expect(store.value).toBeNull()
  })

  it('surfaces reply errors as BridgeError codes', async () => {
    const d = pairingDesktop('unlocked')
    const bridge = new Bridge(() => fakePort(d.desktop), memoryStore(), 'Chrome')
    await bridge.pair()
    await vi.waitFor(async () => expect(await bridge.status()).toEqual({ state: 'unlocked' }))
    const error = await bridge.request('matches', { url: 'https://a.example' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(BridgeError)
    expect((error as BridgeError).code).toBe('locked')
  })

  it('ignores replies that do not decrypt, then times out', async () => {
    const store = memoryStore({ clientId: 'c', key: toB64(new Uint8Array(32)) })
    let forged = 0
    const bridge = new Bridge(
      () =>
        fakePort((_f, reply) => {
          forged++
          reply({ type: 'res', n: toB64(new Uint8Array(12)), c: toB64(new Uint8Array(40)) })
        }),
      store,
      'Chrome',
      100,
    )
    const code = await bridge.request('status', {}).catch((e: BridgeError) => e.code)
    expect(forged).toBe(1)
    expect(code).toBe('timeout')
  })
})
