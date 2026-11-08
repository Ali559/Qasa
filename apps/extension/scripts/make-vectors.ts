// Writes test/vectors/bridge-pairing.json, the cross-language vector for the bridge
// pairing (plan §8.2.1). Values are computed with Node's own crypto (createECDH, hkdfSync,
// AES-GCM), independently of src/shared/protocol.ts; test/unit/protocol.test.ts then
// checks protocol.ts against this file, and the desktop's Rust test reads the same file.
import { createCipheriv, createECDH, createHash, hkdfSync } from 'node:crypto'
import { writeFileSync } from 'node:fs'

// Fixed scalars from a hash, so the file is reproducible.
const scalar = (label: string) => createHash('sha256').update(label).digest()

const ext = createECDH('prime256v1')
ext.setPrivateKey(scalar('qasa-bridge-vector-extension'))
const desk = createECDH('prime256v1')
desk.setPrivateKey(scalar('qasa-bridge-vector-desktop'))

const extPub = ext.getPublicKey(undefined, 'uncompressed')
const deskPub = desk.getPublicKey(undefined, 'uncompressed')
const shared = ext.computeSecret(deskPub)
const okm = Buffer.from(hkdfSync('sha256', shared, Buffer.concat([extPub, deskPub]), 'qasa-bridge-v1', 36))
const key = okm.subarray(0, 32)
const code = String(okm.readUInt32BE(32) % 1_000_000).padStart(6, '0')

const clientId = '6f1c2a7e-0b4d-4c55-9a51-3f2e8d7c1b90'
const nonce = Buffer.from('000102030405060708090a0b', 'hex')
const plaintext = JSON.stringify({ id: '0123456789abcdef', ts: 1767225600000, op: 'status' })
const cipher = createCipheriv('aes-256-gcm', key, nonce)
cipher.setAAD(Buffer.from(clientId, 'utf8'))
const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final(), cipher.getAuthTag()])

const b64 = (b: Buffer) => b.toString('base64')
const vector = {
  description:
    'Bridge pairing v1 (docs/desktop-extension-plan.md §8.2.1). Keys are P-256: priv = 32-byte scalar, pub = 65-byte uncompressed point. ' +
    'sharedSecret = ECDH x-coordinate. okm = HKDF-SHA256(sharedSecret, salt = extPub||deskPub, info = "qasa-bridge-v1", 36). ' +
    'key = okm[0..32]; code = u32be(okm[32..36]) % 1000000, 6 digits. aesGcm.ciphertext = ciphertext || 16-byte tag, aad = UTF-8 clientId.',
  extPriv: b64(ext.getPrivateKey()),
  extPub: b64(extPub),
  deskPriv: b64(desk.getPrivateKey()),
  deskPub: b64(deskPub),
  sharedSecret: b64(shared),
  okm: b64(okm),
  key: b64(key),
  code,
  aesGcm: {
    key: b64(key),
    nonce: b64(nonce),
    aad: clientId,
    plaintext,
    ciphertext: b64(ciphertext),
  },
}
writeFileSync(new URL('../test/vectors/bridge-pairing.json', import.meta.url), JSON.stringify(vector, null, 2) + '\n')
console.log('code', code)
