// The service worker's connection to the desktop app: one native port, opened lazily and
// re-opened after it drops (MV3 workers are stopped when idle, and nm-host exits when the
// browser closes the port). Owns pairing and the encrypted request/reply cycle of
// plan §8.2.1. Holds no vault data: replies are handed to the caller and forgotten.
import type { BridgeStatus } from '@ext/shared/messages'
import {
  derivePairing,
  fromB64,
  importSessionKey,
  newPairingKeys,
  openReply,
  parseFrame,
  sealRequest,
  toB64,
  type InFrame,
  type Op,
  type ReplyBody,
} from '@ext/shared/protocol'

export const REQUEST_TIMEOUT_MS = 10_000
export const PAIRING_TIMEOUT_MS = 120_000

/** The parts of `chrome.runtime.Port` the bridge uses, so tests can pass a fake. */
export interface PortLike {
  postMessage(message: unknown): void
  disconnect(): void
  onMessage: { addListener(fn: (message: unknown) => void): void }
  onDisconnect: { addListener(fn: () => void): void }
}

/** The one thing the extension persists (plan §8.1): the pairing, in storage.local. */
export interface StoredPairing {
  clientId: string
  /** Raw AES-256 key, base64. */
  key: string
}

export interface PairingStore {
  load(): Promise<StoredPairing | null>
  save(pairing: StoredPairing): Promise<void>
  clear(): Promise<void>
}

/** A failed request. `code` is a bridge error (`appNotRunning`, `notPaired`, `timeout`, …)
 *  or a reply error from the desktop (`locked`, `noMatch`, …). */
export class BridgeError extends Error {
  constructor(readonly code: string) {
    super(code)
  }
}

/** A frame waiting for its answer. Clear-text errors carry no request id, so they settle
 *  the frame they name by nonce or, failing that, the oldest one (nm-host and the
 *  desktop answer frames in order). */
interface Outstanding {
  key: string
  nonce?: string
  resolve(body: ReplyBody): void
  reject(error: BridgeError): void
  timer: ReturnType<typeof setTimeout>
}

interface Pairing {
  privateKey: CryptoKey
  pub: Uint8Array<ArrayBuffer>
  /** Set once the desktop answered `pairPending`. */
  secret?: { key: Uint8Array<ArrayBuffer>; code: string }
  waiters: Array<{ resolve(code: string): void; reject(error: BridgeError): void }>
  timer: ReturnType<typeof setTimeout>
}

interface Session {
  clientId: string
  key: CryptoKey
}

export class Bridge {
  private port: PortLike | null = null
  private readonly outstanding: Outstanding[] = []
  private pairing: Pairing | null = null
  /** `undefined` until loaded from storage. */
  private session: Session | null | undefined
  /** Why the last pairing ended without a key, shown once by `status()`. */
  private pairOutcome: 'denied' | 'expired' | undefined
  /** Frames are handled one at a time, in arrival order (pairPending before paired). */
  private inbox: Promise<void> = Promise.resolve()

  constructor(
    private readonly connect: () => PortLike,
    private readonly store: PairingStore,
    private readonly clientName: string,
    private readonly requestTimeoutMs = REQUEST_TIMEOUT_MS,
  ) {}

  // MARK: public

  async status(): Promise<BridgeStatus> {
    if (this.pairing?.secret) return { state: 'pairing', code: this.pairing.secret.code }
    // Waiting for `pairPending`: a probe now would only race the pair frame.
    if (this.pairing) return { state: 'notPaired' }
    if (!(await this.loadSession())) return this.unpairedStatus()
    try {
      const reply = await this.request('status', {})
      const state = reply.state
      if (state === 'locked' && reply.passwordDue === true && typeof reply.passwordIntervalDays === 'number') {
        return { state, passwordDueDays: reply.passwordIntervalDays }
      }
      if (state === 'locked' || state === 'unlocked' || state === 'noVault') return { state }
      return { state: 'appNotRunning' }
    } catch (error) {
      return this.statusForError(error)
    }
  }

  /** Starts pairing (or joins the one in progress). Resolves with the matching code once
   *  the desktop has answered; `paired` arrives later and is handled in the background. */
  async pair(): Promise<string> {
    if (this.pairing?.secret) return this.pairing.secret.code
    if (!this.pairing) {
      const { privateKey, publicRaw } = await newPairingKeys()
      const timer = setTimeout(() => this.endPairing(new BridgeError('pairExpired'), 'expired'), PAIRING_TIMEOUT_MS)
      this.pairing = { privateKey, pub: publicRaw, waiters: [], timer }
      this.pairOutcome = undefined
      // The `pair` frame is answered by `pairPending`, or by a clear-text error from
      // nm-host (app not running) or the desktop (another pairing pending).
      this.track('pair', undefined, PAIRING_TIMEOUT_MS)
        .then(() => undefined)
        .catch((error: BridgeError) => this.endPairing(error))
      this.send({ type: 'pair', client: this.clientName, pub: toB64(publicRaw) })
    }
    const pairing = this.pairing
    return new Promise((resolve, reject) => pairing.waiters.push({ resolve, reject }))
  }

  /** One encrypted request. Rejects with a BridgeError; never retries on its own. */
  async request(op: Op, args: Record<string, unknown>): Promise<ReplyBody> {
    const session = await this.loadSession()
    if (!session) throw new BridgeError('notPaired')
    const { id, frame } = await sealRequest(session.key, session.clientId, op, args)
    const reply = this.track(id, frame.n, this.requestTimeoutMs)
    this.send(frame)
    const body = await reply
    if (!body.ok) throw new BridgeError(body.error ?? 'bad')
    return body
  }

  // MARK: port

  private send(frame: unknown): void {
    if (!this.port) {
      const port = this.connect()
      port.onMessage.addListener((message) => {
        this.inbox = this.inbox.then(() => this.onFrame(port, message)).catch(() => undefined)
      })
      port.onDisconnect.addListener(() => this.onDisconnect(port))
      this.port = port
    }
    this.port.postMessage(frame)
  }

  /** The host exited or was never installed. Everything in flight fails; the next
   *  request opens a new port. */
  private onDisconnect(port: PortLike): void {
    if (this.port !== port) return
    this.port = null
    for (const o of this.outstanding.splice(0)) {
      clearTimeout(o.timer)
      o.reject(new BridgeError('appNotRunning'))
    }
    if (this.pairing) this.endPairing(new BridgeError('appNotRunning'))
  }

  private async onFrame(port: PortLike, raw: unknown): Promise<void> {
    if (this.port !== port) return
    const frame = parseFrame(raw)
    if (!frame) return
    switch (frame.type) {
      case 'res':
        return this.onReply(frame)
      case 'error':
        return this.onClearError(frame)
      case 'pairPending':
      case 'paired':
      case 'pairDenied':
      case 'pairExpired':
        return this.onPairing(frame)
    }
  }

  private async onReply(frame: Extract<InFrame, { type: 'res' }>): Promise<void> {
    const session = this.session
    if (!session) return
    let body: ReplyBody
    try {
      body = await openReply(session.key, session.clientId, frame)
    } catch {
      // Not from our desktop (bad tag) or garbled: drop it; the request times out.
      return
    }
    this.settle(this.outstanding.findIndex((o) => o.key === body.id))?.resolve(body)
  }

  private async onClearError(frame: Extract<InFrame, { type: 'error' }>): Promise<void> {
    const index = frame.n ? this.outstanding.findIndex((o) => o.nonce === frame.n) : 0
    const target = this.settle(index >= 0 ? index : 0)
    const error = new BridgeError(frame.code)
    target?.reject(error)
    if (frame.code === 'notPaired') {
      // The desktop forgot us (revoked in Settings): drop the key and fail the rest.
      await this.forget()
      for (const o of this.outstanding.filter((o) => o.key !== 'pair')) {
        this.settle(this.outstanding.indexOf(o))?.reject(error)
      }
    }
  }

  // MARK: pairing

  private async onPairing(frame: InFrame): Promise<void> {
    const pairing = this.pairing
    if (!pairing) return
    if (frame.type === 'pairPending') {
      if (pairing.secret) return
      const deskPub = fromB64(frame.pub)
      try {
        pairing.secret = await derivePairing(pairing.privateKey, pairing.pub, deskPub)
      } catch {
        return this.endPairing(new BridgeError('bad'))
      }
      this.settle(this.outstanding.findIndex((o) => o.key === 'pair'))?.resolve({ id: 'pair', ok: true })
      for (const w of pairing.waiters.splice(0)) w.resolve(pairing.secret.code)
    } else if (frame.type === 'paired') {
      if (!pairing.secret) return
      const stored = { clientId: frame.clientId, key: toB64(pairing.secret.key) }
      await this.store.save(stored)
      this.session = { clientId: stored.clientId, key: await importSessionKey(pairing.secret.key) }
      this.endPairing()
    } else if (frame.type === 'pairDenied') {
      this.endPairing(new BridgeError('pairDenied'), 'denied')
    } else if (frame.type === 'pairExpired') {
      this.endPairing(new BridgeError('pairExpired'), 'expired')
    }
  }

  private endPairing(error?: BridgeError, outcome?: 'denied' | 'expired'): void {
    const pairing = this.pairing
    if (!pairing) return
    this.pairing = null
    clearTimeout(pairing.timer)
    if (outcome) this.pairOutcome = outcome
    for (const w of pairing.waiters.splice(0)) w.reject(error ?? new BridgeError('pairExpired'))
    this.settle(this.outstanding.findIndex((o) => o.key === 'pair'))?.reject(error ?? new BridgeError('pairExpired'))
  }

  // MARK: helpers

  private track(key: string, nonce: string | undefined, timeoutMs: number): Promise<ReplyBody> {
    return new Promise((resolve, reject) => {
      const entry: Outstanding = {
        key,
        nonce,
        resolve,
        reject,
        timer: setTimeout(() => {
          const i = this.outstanding.indexOf(entry)
          if (i >= 0) this.outstanding.splice(i, 1)
          reject(new BridgeError('timeout'))
        }, timeoutMs),
      }
      this.outstanding.push(entry)
    })
  }

  /** Removes and returns the outstanding entry at `index`, clearing its timer. */
  private settle(index: number): Outstanding | undefined {
    if (index < 0 || index >= this.outstanding.length) return undefined
    const [entry] = this.outstanding.splice(index, 1)
    clearTimeout(entry.timer)
    return entry
  }

  private async loadSession(): Promise<Session | null> {
    if (this.session !== undefined) return this.session
    const stored = await this.store.load()
    try {
      this.session = stored ? { clientId: stored.clientId, key: await importSessionKey(fromB64(stored.key)) } : null
    } catch {
      // A damaged entry can't be used; pairing again replaces it.
      await this.store.clear()
      this.session = null
    }
    return this.session
  }

  private async forget(): Promise<void> {
    this.session = null
    await this.store.clear()
  }

  /**
   * Without a pairing there is nothing to encrypt with, so the bridge sends a request
   * for an unknown client: the desktop answers `notPaired`, nm-host `appNotRunning`.
   */
  private async unpairedStatus(): Promise<BridgeStatus> {
    const nonce = toB64(crypto.getRandomValues(new Uint8Array(12)))
    const reply = this.track('probe', nonce, this.requestTimeoutMs)
    this.send({ type: 'req', clientId: 'unpaired', n: nonce, c: toB64(crypto.getRandomValues(new Uint8Array(16))) })
    try {
      await reply
    } catch (error) {
      return this.statusForError(error)
    }
    return { state: 'notPaired' }
  }

  private statusForError(error: unknown): BridgeStatus {
    const code = error instanceof BridgeError ? error.code : 'bad'
    if (code === 'notPaired') {
      const reason = this.pairOutcome
      this.pairOutcome = undefined
      return reason ? { state: 'notPaired', reason } : { state: 'notPaired' }
    }
    if (code === 'locked') return { state: 'locked' }
    if (code === 'noVault') return { state: 'noVault' }
    return { state: 'appNotRunning' }
  }
}
