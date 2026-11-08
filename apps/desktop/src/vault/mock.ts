// An in-memory stand-in for the Rust core, used only when the UI runs in a plain browser
// (`bun run dev`) for design work. No crypto: never ships in the Tauri app, where the
// real commands are always used. Seeded like the iOS `-screenshotVault` launch argument.
import type { Collection, CommandError, ItemSummary, VaultPayload, VaultStatus } from './types'
import { VARIANT_KIND } from './types'

let password: string | null = null
let unlocked = false
const items = new Map<string, { payload: VaultPayload; createdAt: number; updatedAt: number; lastUsedAt: number | null }>()
const collections = new Map<string, { name: string; createdAt: number }>()

function fail(code: CommandError['code']): never {
  throw { code, message: code } satisfies CommandError
}

function requireUnlocked() {
  if (!unlocked) fail('locked')
}

/** Plain-JSON copy, as the real IPC boundary makes (Vue proxies can't be structured-cloned). */
function clone<T>(v: T): T {
  return JSON.parse(JSON.stringify(v)) as T
}

function uuid() {
  return crypto.randomUUID().toUpperCase()
}

function displayName(p: VaultPayload): string {
  const f = p.fields as unknown as Record<string, string>
  switch (p.variant) {
    case 'secureNote':
      return p.fields.title
    case 'name':
      return [p.fields.firstName, p.fields.lastName].filter(Boolean).join(' ')
    case 'personalEmail':
      return p.fields.itemName || p.fields.email
    case 'phone':
      return p.fields.itemName || p.fields.number
    case 'company':
      return p.fields.companyName
    case 'idCard':
    case 'passport':
    case 'driverLicense':
      return p.fields.name
    default:
      return f.itemName ?? ''
  }
}

function subtitle(p: VaultPayload): string {
  switch (p.variant) {
    case 'login':
      return p.fields.email || p.fields.username
    case 'secureNote':
      return p.fields.protectItem ? 'Protected' : p.fields.content.slice(0, 40)
    case 'card': {
      const last4 = p.fields.number.replace(/\D/g, '').slice(-4)
      return last4 ? `•••• ${last4}` : 'Card'
    }
    case 'bankAccount':
      return p.fields.bankName || p.fields.accountNumber
    case 'name':
      return p.fields.defaultUsername
    case 'personalEmail':
      return p.fields.email
    case 'phone':
      return `+964 ${p.fields.number}`
    case 'address':
      return [p.fields.city, p.fields.countryCode].filter(Boolean).join(', ')
    case 'company':
      return p.fields.jobTitle
    case 'website':
      return p.fields.websiteURL
    default:
      return p.fields.number
  }
}

function summary(id: string): ItemSummary {
  const it = items.get(id)!
  const p = it.payload
  const name = displayName(p).trim()
  const collectionId =
    p.variant === 'login' || p.variant === 'secureNote' ? (p.fields.collectionId ?? null) : null
  return {
    id,
    kind: VARIANT_KIND[p.variant],
    variant: p.variant,
    displayName: name || 'Untitled',
    subtitle: subtitle(p),
    createdAt: it.createdAt,
    updatedAt: it.updatedAt,
    lastUsedAt: it.lastUsedAt,
    collectionId,
    hasPrimaryCopy: true,
    hasTotp: p.variant === 'login' && !!p.fields.totp,
  }
}

function login(itemName: string, extra: Partial<VaultPayload['fields']> & Record<string, unknown>): VaultPayload {
  return {
    variant: 'login',
    fields: { itemName, email: '', username: '', password: '', website: '', note: '', ...extra },
  } as VaultPayload
}

function seed() {
  const now = Date.now()
  const personal = uuid()
  const work = uuid()
  const bank = uuid()
  collections.set(work, { name: 'Work', createdAt: now - 3000 })
  collections.set(personal, { name: 'Personal', createdAt: now - 2000 })
  collections.set(bank, { name: 'Bank', createdAt: now - 1000 })
  const samples: VaultPayload[] = [
    login('Facebook', { email: 'sara.ahmed@gmail.com', password: 'Hawler-Dusk-2024', website: 'https://facebook.com', collectionId: personal }),
    login('Instagram', { username: 'sara.ahmed', password: 'Hawler-Dusk-2024', website: 'https://instagram.com', collectionId: personal }),
    login('FIB', { username: '0750 123 4821', password: 'Tz8#qLm2vR!x', website: 'https://fib.iq', collectionId: bank }),
    login('Gmail', { email: 'sara.ahmed@gmail.com', password: 'sara1234', website: 'https://mail.google.com', collectionId: personal }),
    login('Asiacell', { username: '0770 555 1290', password: 'k9V$wQ1z!pA3', website: 'https://asiacell.com' }),
    login('Snapchat', { username: 'sara.ahmed', password: 'snapqasa9', website: 'https://snapchat.com' }),
    login('Netflix', { email: 'sara.ahmed@gmail.com', password: 'r7N@eB4u#Lx2', website: 'https://netflix.com' }),
    login('Slack', { email: 'sara@company.iq', password: 'fR5!mZ8q@Kd1', website: 'https://slack.com', collectionId: work }),
    {
      variant: 'card',
      fields: { itemName: 'FIB Card', accountHolder: 'Sara Ahmed', number: '4111111111111111', securityCode: '123', expiryDate: '08/29', note: '', color: 'blue' },
    },
    { variant: 'secureNote', fields: { title: 'Home Wi‑Fi', content: 'Network: Ahmed-Home', color: 'mint', protectItem: false } },
  ]
  samples.forEach((payload, i) => {
    items.set(uuid(), { payload, createdAt: now - i * 60_000, updatedAt: now - i * 60_000, lastUsedAt: i < 3 ? now - i * 3_600_000 : null })
  })
}

const delay = <T>(value: T) => new Promise<T>((resolve) => setTimeout(() => resolve(value), 120))

export const mockVaultApi = {
  async status(): Promise<VaultStatus> {
    return delay({ exists: password !== null, unlocked, unsupportedKdfVersion: null })
  },
  async create(pw: string) {
    if (password !== null) fail('vaultExists')
    if ([...pw].length < 8) fail('weakPassword')
    password = pw
    unlocked = true
    seed()
    await delay(undefined)
  },
  async unlock(pw: string) {
    if (password === null) fail('noVault')
    if (pw !== password && pw.trim() !== password) fail('wrongPassword')
    unlocked = true
    await delay(undefined)
  },
  async verifyPassword(pw: string) {
    return pw === password || pw.trim() === password
  },
  async lock() {
    unlocked = false
  },
  async noteActivity() {},
  async list() {
    requireUnlocked()
    return delay({ items: [...items.keys()].map(summary), unreadable: 0 })
  },
  async get(id: string) {
    requireUnlocked()
    const it = items.get(id) ?? fail('notFound')
    return delay(clone(it.payload))
  },
  async save(payload: VaultPayload, id?: string) {
    requireUnlocked()
    const now = Date.now()
    const existing = id ? items.get(id) : undefined
    const key = existing ? id! : uuid()
    items.set(key, { payload: clone(payload), createdAt: existing?.createdAt ?? now, updatedAt: now, lastUsedAt: existing?.lastUsedAt ?? null })
    return delay(summary(key))
  },
  async remove(id: string) {
    requireUnlocked()
    if (!items.delete(id)) fail('notFound')
  },
  async markUsed(id: string) {
    const it = items.get(id)
    if (it) it.lastUsedAt = it.updatedAt = Date.now()
  },
  async changePassword(current: string, next: string) {
    requireUnlocked()
    if (current !== password) fail('wrongPassword')
    if ([...next.trim()].length < 8) fail('weakPassword')
    password = next.trim()
    await delay(undefined)
  },
  async wipe() {
    password = null
    unlocked = false
    items.clear()
    collections.clear()
  },
  async collections(): Promise<Collection[]> {
    requireUnlocked()
    const counts = new Map<string, number>()
    for (const id of items.keys()) {
      const c = summary(id).collectionId
      if (c) counts.set(c, (counts.get(c) ?? 0) + 1)
    }
    return delay(
      [...collections.entries()]
        .sort((a, b) => a[1].createdAt - b[1].createdAt)
        .map(([id, c]) => ({ id, name: c.name, createdAt: c.createdAt, itemCount: counts.get(id) ?? 0 })),
    )
  },
  async createCollection(name: string): Promise<Collection> {
    const id = uuid()
    const createdAt = Date.now()
    collections.set(id, { name: name.trim(), createdAt })
    return { id, name: name.trim(), createdAt, itemCount: 0 }
  },
  async renameCollection(id: string, name: string) {
    const c = collections.get(id) ?? fail('notFound')
    c.name = name.trim()
  },
  async deleteCollection(id: string) {
    collections.delete(id)
    for (const it of items.values()) {
      if ((it.payload.variant === 'login' || it.payload.variant === 'secureNote') && it.payload.fields.collectionId === id) {
        delete it.payload.fields.collectionId
      }
    }
  },
}
