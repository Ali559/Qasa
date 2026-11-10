// A stand-in for the desktop app's bridge (plan §8.2.1) for the e2e tests: listens on a
// unix socket like the real app, speaks little-endian length-prefixed JSON frames to the
// real nm-host, auto-approves pairing, and serves a few fixture logins. It follows the
// spec as written (clear-text errors carry no nonce) so the extension's in-order matching
// of errors is exercised. Matching is approximated by an exact host match (the desktop
// uses the iOS ranking with score ≥ 60).
import { createHmac, randomBytes, randomUUID } from 'node:crypto'
import { existsSync, unlinkSync } from 'node:fs'
import { createServer, type Server, type Socket } from 'node:net'

import { derivePairing, fromB64, importSessionKey, newPairingKeys, open, seal, toB64 } from '../src/shared/protocol'

export interface FakeLogin {
  id: string
  title: string
  username: string
  password: string
  website: string
  totpSecret?: string
}

export const LOGINS: FakeLogin[] = [
  { id: 'a1', title: 'Alpha 127', username: 'alice@127', password: 'alpha-127-password', website: 'http://127.0.0.1', totpSecret: 'JBSWY3DPEHPK3PXP' },
  { id: 'a2', title: 'Beta 127', username: 'beth@127', password: 'beta-127-password', website: 'http://127.0.0.1' },
  { id: 'b1', title: 'Localhost Account', username: 'bob@localhost', password: 'localhost-password', website: 'http://localhost' },
  { id: 'c1', title: 'Elsewhere', username: 'carol', password: 'elsewhere-password', website: 'https://example.com' },
]

/** Cards and profile items, as the desktop's `identities` lists them, with their values. */
export const IDENTITIES = [
  {
    id: 'card-1',
    kind: 'card',
    title: 'Visa',
    hint: '•••• 4242',
    values: { 'cc-number': '4242424242424242', 'cc-name': 'Ali Barznji', 'cc-exp': '03/29', 'cc-exp-month': '03', 'cc-exp-year': '2029', 'cc-csc': '123' },
  },
  {
    id: 'addr-1',
    kind: 'address',
    title: 'Home',
    hint: 'Sulaymaniyah, IQ',
    values: {
      name: 'Ali Barznji',
      'given-name': 'Ali',
      'family-name': 'Barznji',
      email: 'ali@example.com',
      tel: '+9647501234567',
      'tel-national': '07501234567',
      'tel-country-code': '+964',
      'street-address': '100 Salim St',
      'address-line1': '100 Salim St',
      'address-line2': 'Building 4',
      'address-level2': 'Sulaymaniyah',
      'postal-code': '46001',
      country: 'IQ',
      'country-name': 'Iraq',
    },
  },
] as const

export type VaultState = 'locked' | 'unlocked' | 'noVault'

// MARK: TOTP (RFC 6238, SHA-1, 6 digits, 30 s), also used by the tests to check codes.

function base32(secret: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567'
  let bits = ''
  for (const c of secret.replace(/=+$/, '').toUpperCase()) bits += alphabet.indexOf(c).toString(2).padStart(5, '0')
  const bytes = []
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(Number.parseInt(bits.slice(i, i + 8), 2))
  return Buffer.from(bytes)
}

export function totp(secret: string, at = Date.now()): string {
  const counter = Buffer.alloc(8)
  counter.writeBigUInt64BE(BigInt(Math.floor(at / 1000 / 30)))
  const mac = createHmac('sha1', base32(secret)).update(counter).digest()
  const offset = mac[mac.length - 1] & 0xf
  const n = (mac.readUInt32BE(offset) & 0x7fffffff) % 1_000_000
  return String(n).padStart(6, '0')
}

// MARK: server

export class FakeDesktop {
  state: VaultState = 'unlocked'
  /** `deny` answers pairDenied instead of paired. */
  approve: 'auto' | 'deny' = 'auto'
  readonly clients = new Map<string, CryptoKey>()
  readonly saves: Array<{ url: string; username: string; password: string }> = []
  readonly copies: Array<{ itemId: string; field: string }> = []
  readonly ops: string[] = []
  /** Host of every `matches` / `fill` / `totp` request, as `op host`. */
  readonly urlOps: string[] = []
  readonly hellos: string[] = []
  unlocks = 0
  /** How a card confirmation ends, a moment after it was asked for. */
  confirmAnswer: 'approve' | 'deny' = 'approve'
  readonly confirms: string[] = []
  private readonly approvals = new Map<string, 'pending' | 'approved' | 'denied'>()
  /** `unlock` with `prompt`: unlock this long after (Touch ID being touched). */
  unlockDelayMs = 0
  lastPairCode: string | undefined
  private server: Server | undefined
  private readonly sockets = new Set<Socket>()
  private readonly seenIds = new Map<string, number>()

  readonly socketPath: string

  constructor(socketPath: string) {
    this.socketPath = socketPath
  }

  async start(): Promise<void> {
    if (existsSync(this.socketPath)) unlinkSync(this.socketPath)
    this.server = createServer((socket) => this.accept(socket))
    await new Promise<void>((resolve) => this.server!.listen(this.socketPath, resolve))
  }

  /** Stops listening and drops every connection: the app "quit". */
  async stop(): Promise<void> {
    for (const s of this.sockets) s.destroy()
    this.sockets.clear()
    await new Promise<void>((resolve) => (this.server ? this.server.close(() => resolve()) : resolve()))
    this.server = undefined
    if (existsSync(this.socketPath)) unlinkSync(this.socketPath)
  }

  private accept(socket: Socket): void {
    this.sockets.add(socket)
    socket.on('close', () => this.sockets.delete(socket))
    socket.on('error', () => undefined)
    let buffer = Buffer.alloc(0)
    let chain = Promise.resolve()
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk])
      while (buffer.length >= 4) {
        const len = buffer.readUInt32LE(0)
        if (buffer.length < 4 + len) break
        const frame = JSON.parse(buffer.subarray(4, 4 + len).toString('utf8')) as Record<string, unknown>
        buffer = buffer.subarray(4 + len)
        chain = chain.then(() => this.onFrame(socket, frame)).catch((e) => console.error('fake desktop', e))
      }
    })
  }

  private send(socket: Socket, frame: unknown): void {
    const body = Buffer.from(JSON.stringify(frame), 'utf8')
    const len = Buffer.alloc(4)
    len.writeUInt32LE(body.length)
    socket.write(Buffer.concat([len, body]))
  }

  private async onFrame(socket: Socket, frame: Record<string, unknown>): Promise<void> {
    if (frame.type === 'hello') {
      this.hellos.push(String(frame.origin))
      return
    }
    if (frame.type === 'pair') return this.pair(socket, frame)
    if (frame.type !== 'req') return this.send(socket, { type: 'error', code: 'bad' })
    const clientId = String(frame.clientId)
    const key = this.clients.get(clientId)
    if (!key) return this.send(socket, { type: 'error', code: 'notPaired' })
    let body: Record<string, unknown>
    try {
      body = JSON.parse(new TextDecoder().decode(await open(key, clientId, String(frame.n), String(frame.c))))
    } catch {
      return this.send(socket, { type: 'error', code: 'bad' })
    }
    const id = String(body.id)
    const now = Date.now()
    for (const [seen, at] of this.seenIds) if (now - at > 5 * 60_000) this.seenIds.delete(seen)
    if (Math.abs(now - Number(body.ts)) > 2 * 60_000 || this.seenIds.has(id)) {
      return this.reply(socket, key, clientId, { id, ok: false, error: 'bad' })
    }
    this.seenIds.set(id, now)
    this.ops.push(String(body.op))
    if (typeof body.url === 'string') this.urlOps.push(`${body.op} ${this.cleanHost(body.url)}`)
    await this.reply(socket, key, clientId, { id, ...this.handle(body) })
  }

  private async reply(socket: Socket, key: CryptoKey, clientId: string, body: Record<string, unknown>): Promise<void> {
    this.send(socket, { type: 'res', ...(await seal(key, clientId, new TextEncoder().encode(JSON.stringify(body)))) })
  }

  private async pair(socket: Socket, frame: Record<string, unknown>): Promise<void> {
    const desk = await newPairingKeys()
    const extPub = fromB64(String(frame.pub))
    const secret = await derivePairing(desk.privateKey, extPub, desk.publicRaw, false)
    this.lastPairCode = secret.code
    this.send(socket, { type: 'pairPending', pub: toB64(desk.publicRaw) })
    // The user "reads the code and clicks Allow" a moment later.
    await new Promise((r) => setTimeout(r, 400))
    if (this.approve === 'deny') return this.send(socket, { type: 'pairDenied' })
    const clientId = randomUUID()
    this.clients.set(clientId, await importSessionKey(secret.key))
    this.send(socket, { type: 'paired', clientId })
  }

  private handle(body: Record<string, unknown>): Record<string, unknown> {
    const op = body.op
    if (op === 'status') return { ok: true, state: this.state, device: 'Fake Mac' }
    if (op === 'unlock') {
      this.unlocks++
      if (this.state === 'locked') {
        if (body.prompt && this.unlockDelayMs > 0) setTimeout(() => (this.state = 'unlocked'), this.unlockDelayMs)
        else this.state = 'unlocked'
      }
      return { ok: true, method: body.prompt ? 'biometric' : 'window' }
    }
    if (this.state === 'noVault') return { ok: false, error: 'noVault' }
    if (this.state === 'locked') return { ok: false, error: 'locked' }
    const item = (id: unknown) => LOGINS.find((l) => l.id === id)
    switch (op) {
      case 'matches': {
        const host = this.cleanHost(body.url)
        if (!host) return { ok: false, error: 'bad' }
        const items = LOGINS.filter((l) => new URL(l.website).hostname === host).map((l) => ({ id: l.id, title: l.title, username: l.username, website: l.website, hasTotp: !!l.totpSecret }))
        return { ok: true, items }
      }
      case 'fill':
      case 'totp': {
        const l = item(body.itemId)
        if (!l) return { ok: false, error: 'notFound' }
        if (new URL(l.website).hostname !== this.cleanHost(body.url)) return { ok: false, error: 'noMatch' }
        if (op === 'totp') {
          if (!l.totpSecret) return { ok: false, error: 'notFound' }
          return { ok: true, code: totp(l.totpSecret), period: 30, remaining: 30 - (Math.floor(Date.now() / 1000) % 30) }
        }
        return { ok: true, username: l.username, password: l.password, ...(l.totpSecret ? { totp: totp(l.totpSecret) } : {}) }
      }
      case 'copy':
        if (!item(body.itemId)) return { ok: false, error: 'notFound' }
        this.copies.push({ itemId: String(body.itemId), field: String(body.field) })
        return { ok: true }
      case 'search': {
        const q = String(body.query).toLowerCase()
        const items = LOGINS.filter((l) => l.title.toLowerCase().includes(q) || l.username.toLowerCase().includes(q))
          .slice(0, 50)
          .map((l) => ({ id: l.id, title: l.title, username: l.username, website: l.website, hasTotp: !!l.totpSecret }))
        return { ok: true, items }
      }
      case 'identities':
        return { ok: true, items: IDENTITIES.map(({ id, kind, title, hint }) => ({ id, kind, title, hint })) }
      case 'fillIdentity': {
        const it = IDENTITIES.find((i) => i.id === body.itemId)
        if (!it) return { ok: false, error: 'notFound' }
        if (it.kind === 'card') {
          const a = this.approvals.get(it.id)
          if (!a) return { ok: false, error: 'confirm' }
          if (a === 'pending') return { ok: false, error: 'pending' }
          this.approvals.delete(it.id)
          if (a === 'denied') return { ok: false, error: 'cancelled' }
        }
        return { ok: true, kind: it.kind, values: it.values }
      }
      case 'confirm': {
        const id = String(body.itemId)
        this.confirms.push(id)
        this.approvals.set(id, 'pending')
        const answer = this.confirmAnswer
        setTimeout(() => this.approvals.set(id, answer === 'approve' ? 'approved' : 'denied'), 600)
        return { ok: true }
      }
      case 'generate':
        return { ok: true, password: randomBytes(15).toString('base64').replace(/[+/=]/g, 'x') }
      case 'save':
        this.saves.push({ url: String(body.url), username: String(body.username), password: String(body.password) })
        return { ok: true, result: 'prompted' }
      default:
        return { ok: false, error: 'bad' }
    }
  }

  /** Only http(s); the host alone is matched (the desktop drops path and query). */
  private cleanHost(url: unknown): string | null {
    try {
      const u = new URL(String(url))
      return u.protocol === 'http:' || u.protocol === 'https:' ? u.hostname : null
    } catch {
      return null
    }
  }
}
