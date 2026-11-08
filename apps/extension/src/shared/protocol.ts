// The extension side of the desktop bridge, wire protocol v1 (docs/desktop-extension-plan.md
// §8.2.1). Pure functions over WebCrypto, so the same code runs in the service worker and
// in Vitest, and the Rust side can be checked against test/vectors/bridge-pairing.json.

/** Native messaging host name; the host manifest the desktop installs uses the same. */
export const NATIVE_HOST = 'com.qasa_app.desktop'

/** HKDF `info` for the pairing derivation. */
export const PAIRING_INFO = 'qasa-bridge-v1'

const subtle = globalThis.crypto.subtle
const utf8 = new TextEncoder()
const utf8Decode = new TextDecoder()

// MARK: base64 (standard alphabet, padded, as in every other frame field)

export function toB64(bytes: Uint8Array): string {
  let s = ''
  for (const b of bytes) s += String.fromCharCode(b)
  return btoa(s)
}

export function fromB64(text: string): Uint8Array<ArrayBuffer> {
  const s = atob(text)
  const out = new Uint8Array(s.length)
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i)
  return out
}

function concat(a: Uint8Array, b: Uint8Array): Uint8Array<ArrayBuffer> {
  const out = new Uint8Array(a.length + b.length)
  out.set(a, 0)
  out.set(b, a.length)
  return out
}

// MARK: pairing

/** A fresh P-256 key pair. The private key never leaves WebCrypto (not extractable). */
export async function newPairingKeys(): Promise<{ privateKey: CryptoKey; publicRaw: Uint8Array<ArrayBuffer> }> {
  const pair = await subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits'])
  const publicRaw = new Uint8Array(await subtle.exportKey('raw', pair.publicKey))
  return { privateKey: pair.privateKey, publicRaw }
}

export interface PairingSecret {
  /** AES-256-GCM key, 32 raw bytes. */
  key: Uint8Array<ArrayBuffer>
  /** The 6-digit matching code both sides show. */
  code: string
}

/**
 * Derives the shared key and matching code (§8.2.1 step 2):
 * okm = HKDF-SHA256(ikm = ECDH x-coordinate, salt = extPub ‖ deskPub, info, 36 bytes),
 * key = okm[0..32], code = big-endian u32(okm[32..36]) mod 1 000 000, zero-padded.
 * Either side can call it: `ownPrivate` is the caller's key, the publics are always in
 * extension-then-desktop order.
 */
export async function derivePairing(
  ownPrivate: CryptoKey,
  extPub: Uint8Array<ArrayBuffer>,
  deskPub: Uint8Array<ArrayBuffer>,
  peerIsDesktop = true,
): Promise<PairingSecret> {
  if (extPub.length !== 65 || deskPub.length !== 65) throw new Error('bad public key')
  const peer = await subtle.importKey(
    'raw',
    peerIsDesktop ? deskPub : extPub,
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    [],
  )
  // 256 bits = the raw x-coordinate of the shared point (the standard ECDH secret).
  const shared = await subtle.deriveBits({ name: 'ECDH', public: peer }, ownPrivate, 256)
  const ikm = await subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits'])
  const okm = new Uint8Array(
    await subtle.deriveBits(
      { name: 'HKDF', hash: 'SHA-256', salt: concat(extPub, deskPub), info: utf8.encode(PAIRING_INFO) },
      ikm,
      36 * 8,
    ),
  )
  const n = new DataView(okm.buffer, 32, 4).getUint32(0, false)
  return { key: okm.slice(0, 32), code: String(n % 1_000_000).padStart(6, '0') }
}

/** Imports a P-256 private scalar (32 bytes) with its public point; used by tests and vectors. */
export async function importPrivate(scalar: Uint8Array, publicRaw: Uint8Array): Promise<CryptoKey> {
  const b64url = (b: Uint8Array) => toB64(b).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return subtle.importKey(
    'jwk',
    { kty: 'EC', crv: 'P-256', d: b64url(scalar), x: b64url(publicRaw.slice(1, 33)), y: b64url(publicRaw.slice(33, 65)) },
    { name: 'ECDH', namedCurve: 'P-256' },
    false,
    ['deriveBits'],
  )
}

// MARK: requests

/** The AES-GCM key for a stored pairing. Not extractable once imported. */
export function importSessionKey(raw: Uint8Array<ArrayBuffer>): Promise<CryptoKey> {
  if (raw.length !== 32) throw new Error('bad key length')
  return subtle.importKey('raw', raw, 'AES-GCM', false, ['encrypt', 'decrypt'])
}

/** AES-256-GCM with AAD = UTF-8 clientId; `c` is ciphertext followed by the 16-byte tag. */
export async function seal(
  key: CryptoKey,
  clientId: string,
  plaintext: Uint8Array<ArrayBuffer>,
  nonce: Uint8Array<ArrayBuffer> = crypto.getRandomValues(new Uint8Array(12)),
): Promise<{ n: string; c: string }> {
  const c = await subtle.encrypt({ name: 'AES-GCM', iv: nonce, additionalData: utf8.encode(clientId), tagLength: 128 }, key, plaintext)
  return { n: toB64(nonce), c: toB64(new Uint8Array(c)) }
}

/** Throws when the tag, nonce or AAD doesn't check out. */
export async function open(key: CryptoKey, clientId: string, n: string, c: string): Promise<Uint8Array<ArrayBuffer>> {
  const nonce = fromB64(n)
  if (nonce.length !== 12) throw new Error('bad nonce')
  const plain = await subtle.decrypt(
    { name: 'AES-GCM', iv: nonce, additionalData: utf8.encode(clientId), tagLength: 128 },
    key,
    fromB64(c),
  )
  return new Uint8Array(plain)
}

/** 16 random lowercase hex characters (8 bytes), unique per request. */
export function newRequestId(): string {
  return [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, '0')).join('')
}

export type Op = 'status' | 'unlock' | 'matches' | 'fill' | 'totp' | 'copy' | 'search' | 'generate' | 'save'

/** Encrypts one request into its `req` frame. */
export async function sealRequest(
  key: CryptoKey,
  clientId: string,
  op: Op,
  args: Record<string, unknown>,
  id = newRequestId(),
  ts = Date.now(),
): Promise<{ id: string; frame: ReqFrame }> {
  const body = utf8.encode(JSON.stringify({ ...args, id, ts, op }))
  return { id, frame: { type: 'req', clientId, ...(await seal(key, clientId, body)) } }
}

/** Decrypts a `res` frame's body. */
export async function openReply(key: CryptoKey, clientId: string, frame: ResFrame): Promise<ReplyBody> {
  const body = JSON.parse(utf8Decode.decode(await open(key, clientId, frame.n, frame.c))) as unknown
  if (!body || typeof body !== 'object' || typeof (body as ReplyBody).id !== 'string') throw new Error('bad reply')
  return body as ReplyBody
}

// MARK: frames

export interface ReqFrame {
  type: 'req'
  clientId: string
  n: string
  c: string
}

export interface ResFrame {
  type: 'res'
  n: string
  c: string
}

/** Every frame the extension can receive from nm-host or the desktop. */
export type InFrame =
  | ResFrame
  | { type: 'pairPending'; pub: string }
  | { type: 'paired'; clientId: string }
  | { type: 'pairDenied' }
  | { type: 'pairExpired' }
  | { type: 'error'; code: string; n?: string }

export type ReplyError = 'locked' | 'noVault' | 'notFound' | 'noMatch' | 'bad'

export interface ReplyBody {
  id: string
  ok: boolean
  error?: ReplyError
  [field: string]: unknown
}

/** Parses an incoming frame, rejecting anything that isn't one of the known shapes. */
export function parseFrame(raw: unknown): InFrame | null {
  if (!raw || typeof raw !== 'object') return null
  const f = raw as Record<string, unknown>
  const str = (k: string) => typeof f[k] === 'string'
  switch (f.type) {
    case 'res':
      return str('n') && str('c') ? (f as unknown as InFrame) : null
    case 'pairPending':
      return str('pub') ? (f as unknown as InFrame) : null
    case 'paired':
      return str('clientId') ? (f as unknown as InFrame) : null
    case 'pairDenied':
    case 'pairExpired':
      return { type: f.type }
    case 'error':
      return str('code') ? { type: 'error', code: f.code as string, ...(str('n') ? { n: f.n as string } : {}) } : null
    default:
      return null
  }
}

/** Only http(s) pages are sent to the desktop (§8.2.1 "Clean URLs"). */
export function isWebUrl(url: string | undefined): url is string {
  if (!url) return false
  try {
    const u = new URL(url)
    return u.protocol === 'https:' || u.protocol === 'http:'
  } catch {
    return false
  }
}
