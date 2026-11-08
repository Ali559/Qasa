import { createCipheriv, createECDH } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

import {
  derivePairing,
  fromB64,
  importPrivate,
  importSessionKey,
  newPairingKeys,
  newRequestId,
  open,
  openReply,
  parseFrame,
  seal,
  sealRequest,
  toB64,
} from '@ext/shared/protocol'

const vector = JSON.parse(readFileSync(new URL('../vectors/bridge-pairing.json', import.meta.url), 'utf8'))
const utf8 = new TextEncoder()

describe('pairing vector (test/vectors/bridge-pairing.json)', () => {
  it('derives the vector key and code from the extension side', async () => {
    const extPub = fromB64(vector.extPub)
    const deskPub = fromB64(vector.deskPub)
    const priv = await importPrivate(fromB64(vector.extPriv), extPub)
    const secret = await derivePairing(priv, extPub, deskPub)
    expect(toB64(secret.key)).toBe(vector.key)
    expect(secret.code).toBe(vector.code)
    expect(secret.code).toMatch(/^\d{6}$/)
  })

  it('derives the same values from the desktop side', async () => {
    const extPub = fromB64(vector.extPub)
    const deskPub = fromB64(vector.deskPub)
    const priv = await importPrivate(fromB64(vector.deskPriv), deskPub)
    const secret = await derivePairing(priv, extPub, deskPub, false)
    expect(toB64(secret.key)).toBe(vector.key)
    expect(secret.code).toBe(vector.code)
  })

  it('has public keys that belong to its private keys', () => {
    for (const side of ['ext', 'desk']) {
      const ecdh = createECDH('prime256v1')
      ecdh.setPrivateKey(Buffer.from(vector[`${side}Priv`], 'base64'))
      expect(ecdh.getPublicKey('base64', 'uncompressed')).toBe(vector[`${side}Pub`])
    }
  })

  it('opens the AES-GCM sample, and seals it to the same bytes', async () => {
    const s = vector.aesGcm
    const key = await importSessionKey(fromB64(s.key))
    const plain = await open(key, s.aad, s.nonce, s.ciphertext)
    expect(new TextDecoder().decode(plain)).toBe(s.plaintext)
    const sealed = await seal(key, s.aad, utf8.encode(s.plaintext), fromB64(s.nonce))
    expect(sealed.c).toBe(s.ciphertext)
  })

  it('rejects the sample under a different AAD (clientId)', async () => {
    const s = vector.aesGcm
    const key = await importSessionKey(fromB64(s.key))
    await expect(open(key, 'someone-else', s.nonce, s.ciphertext)).rejects.toThrow()
  })
})

describe('pairing with fresh keys', () => {
  it('gives both sides the same key and code', async () => {
    const ext = await newPairingKeys()
    const desk = await newPairingKeys()
    const a = await derivePairing(ext.privateKey, ext.publicRaw, desk.publicRaw)
    const b = await derivePairing(desk.privateKey, ext.publicRaw, desk.publicRaw, false)
    expect(toB64(a.key)).toBe(toB64(b.key))
    expect(a.code).toBe(b.code)
    expect(ext.publicRaw).toHaveLength(65)
    expect(ext.publicRaw[0]).toBe(4)
  })

  it('refuses public keys of the wrong length', async () => {
    const ext = await newPairingKeys()
    await expect(derivePairing(ext.privateKey, ext.publicRaw, new Uint8Array(33))).rejects.toThrow('bad public key')
  })
})

describe('requests', () => {
  it('seals a request that Node can open with the same key and AAD', async () => {
    const raw = crypto.getRandomValues(new Uint8Array(32))
    const key = await importSessionKey(raw)
    const { id, frame } = await sealRequest(key, 'client-1', 'matches', { url: 'https://example.com/login' }, 'abcdef0123456789', 42)
    expect(frame.type).toBe('req')
    expect(frame.clientId).toBe('client-1')
    const nonce = Buffer.from(frame.n, 'base64')
    expect(nonce).toHaveLength(12)
    const body = Buffer.from(frame.c, 'base64')
    const { createDecipheriv } = await import('node:crypto')
    const d = createDecipheriv('aes-256-gcm', Buffer.from(raw), nonce)
    d.setAAD(Buffer.from('client-1'))
    d.setAuthTag(body.subarray(body.length - 16))
    const plain = JSON.parse(Buffer.concat([d.update(body.subarray(0, body.length - 16)), d.final()]).toString('utf8'))
    expect(plain).toEqual({ id, ts: 42, op: 'matches', url: 'https://example.com/login' })
  })

  it('opens a reply sealed by Node', async () => {
    const raw = crypto.getRandomValues(new Uint8Array(32))
    const key = await importSessionKey(raw)
    const nonce = crypto.getRandomValues(new Uint8Array(12))
    const c = createCipheriv('aes-256-gcm', Buffer.from(raw), nonce)
    c.setAAD(Buffer.from('client-1'))
    const ct = Buffer.concat([c.update(JSON.stringify({ id: 'x1', ok: false, error: 'locked' })), c.final(), c.getAuthTag()])
    const reply = await openReply(key, 'client-1', { type: 'res', n: toB64(nonce), c: ct.toString('base64') })
    expect(reply).toEqual({ id: 'x1', ok: false, error: 'locked' })
  })

  it('makes 16-hex-character ids', () => {
    const ids = new Set(Array.from({ length: 200 }, newRequestId))
    expect(ids.size).toBe(200)
    for (const id of ids) expect(id).toMatch(/^[0-9a-f]{16}$/)
  })
})

describe('parseFrame', () => {
  it('accepts the known frames', () => {
    expect(parseFrame({ type: 'res', n: 'a', c: 'b' })).toEqual({ type: 'res', n: 'a', c: 'b' })
    expect(parseFrame({ type: 'error', code: 'appNotRunning' })).toEqual({ type: 'error', code: 'appNotRunning' })
    expect(parseFrame({ type: 'pairDenied', extra: 1 })).toEqual({ type: 'pairDenied' })
  })

  it('drops anything else', () => {
    for (const f of [null, 'x', { type: 'res', n: 1 }, { type: 'paired' }, { type: 'hello' }, { type: 'error' }]) {
      expect(parseFrame(f)).toBeNull()
    }
  })
})
