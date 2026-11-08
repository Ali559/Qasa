// How vault items are shown: detail fields, copy actions, names, colours, dates. Ported
// from iOS `VaultEnums.swift` (`detailFields`, `copyActions`, `primaryCopy`,
// `displayName`), `Country.swift`, `SiteLogo.swift` and `VaultDateFormat`.
import type { EmailKind, Honorific, ItemKind, NoteColor, PhoneKind, Variant, VaultPayload } from './types'

type T = (key: string, ...args: Array<string | number>) => string

// MARK: countries

export interface Country {
  code: string
  name: string
  dialCode: string
}

/** iOS `Country.all`, sorted by English name. */
export const COUNTRIES: Country[] = [
  ['AU', 'Australia', '+61'], ['BH', 'Bahrain', '+973'], ['BR', 'Brazil', '+55'], ['CA', 'Canada', '+1'],
  ['CN', 'China', '+86'], ['EG', 'Egypt', '+20'], ['FR', 'France', '+33'], ['DE', 'Germany', '+49'],
  ['IN', 'India', '+91'], ['IR', 'Iran', '+98'], ['IQ', 'Iraq', '+964'], ['IT', 'Italy', '+39'],
  ['JP', 'Japan', '+81'], ['JO', 'Jordan', '+962'], ['KW', 'Kuwait', '+965'], ['MX', 'Mexico', '+52'],
  ['NL', 'Netherlands', '+31'], ['NO', 'Norway', '+47'], ['PK', 'Pakistan', '+92'], ['PL', 'Poland', '+48'],
  ['QA', 'Qatar', '+974'], ['SA', 'Saudi Arabia', '+966'], ['KR', 'South Korea', '+82'], ['ES', 'Spain', '+34'],
  ['SE', 'Sweden', '+46'], ['CH', 'Switzerland', '+41'], ['TR', 'Turkey', '+90'], ['AE', 'United Arab Emirates', '+971'],
  ['GB', 'United Kingdom', '+44'], ['US', 'United States', '+1'],
].map(([code, name, dialCode]) => ({ code, name, dialCode }))

export function dialCode(code: string): string {
  return COUNTRIES.find((c) => c.code === code)?.dialCode ?? `+${code}`
}

export function countryName(code: string): string {
  return COUNTRIES.find((c) => c.code === code)?.name ?? code
}

// MARK: pickers

export const NOTE_COLORS: NoteColor[] = ['mint', 'gold', 'blue', 'purple', 'red', 'gray']

/** iOS `NoteColor.color`. Brand-independent swatches, same in light and dark. */
export const NOTE_COLOR_VALUE: Record<NoteColor, string> = {
  mint: '#2BB58A',
  gold: 'var(--gold)',
  blue: '#3B82F6',
  purple: '#A855F7',
  red: 'var(--danger)',
  gray: 'var(--text-secondary)',
}

export const HONORIFICS: Array<{ value: Honorific; key: string }> = [
  { value: 'Mr', key: 'honorific.mr' },
  { value: 'Mrs', key: 'honorific.mrs' },
  { value: 'Ms', key: 'honorific.ms' },
  { value: 'Mx', key: 'honorific.mx' },
  { value: 'Miss', key: 'honorific.miss' },
  { value: 'None of these', key: 'honorific.none' },
]

export const EMAIL_KINDS: Array<{ value: EmailKind; key: string }> = [
  { value: 'Personal', key: 'emailKind.personal' },
  { value: 'Work', key: 'emailKind.work' },
]

export const PHONE_KINDS: Array<{ value: PhoneKind; key: string }> = [
  { value: 'Cell', key: 'phoneKind.cell' },
  { value: 'Work', key: 'phoneKind.work' },
  { value: 'Work Cell', key: 'phoneKind.workCell' },
  { value: 'Work Fax', key: 'phoneKind.workFax' },
  { value: 'Fax', key: 'phoneKind.fax' },
  { value: 'Home Phone', key: 'phoneKind.homePhone' },
  { value: 'Other', key: 'phoneKind.other' },
]

function label<V extends string>(list: Array<{ value: V; key: string }>, value: V, t: T): string {
  const found = list.find((x) => x.value === value)
  return found ? t(found.key) : value
}

// MARK: kinds and variants

export const KIND_TAB_KEY: Record<ItemKind, string> = {
  login: 'vault.kind.login.tab',
  secureNote: 'vault.kind.secureNote.tab',
  payment: 'vault.kind.payment.tab',
  personalInfo: 'vault.kind.personalInfo.tab',
  idDocument: 'vault.kind.idDocument.tab',
}

export const KIND_ADD_KEY: Record<ItemKind, string> = {
  login: 'vault.kind.login.add',
  secureNote: 'vault.kind.secureNote.add',
  payment: 'vault.kind.payment.add',
  personalInfo: 'vault.kind.personalInfo.add',
  idDocument: 'vault.kind.idDocument.add',
}

/** iOS `VaultItemKind.subtypes`, as the variants they create, with their title keys. */
export const KIND_SUBTYPES: Partial<Record<ItemKind, Array<{ variant: Variant; key: string }>>> = {
  payment: [
    { variant: 'card', key: 'vault.subtype.card' },
    { variant: 'bankAccount', key: 'vault.subtype.bank' },
  ],
  personalInfo: [
    { variant: 'name', key: 'vault.subtype.name' },
    { variant: 'personalEmail', key: 'vault.subtype.email' },
    { variant: 'phone', key: 'vault.subtype.phone' },
    { variant: 'address', key: 'vault.subtype.address' },
    { variant: 'company', key: 'vault.subtype.company' },
    { variant: 'website', key: 'vault.subtype.website' },
  ],
  idDocument: [
    { variant: 'idCard', key: 'vault.subtype.idCard' },
    { variant: 'passport', key: 'vault.subtype.passport' },
    { variant: 'driverLicense', key: 'vault.subtype.driverLicense' },
  ],
}

/** Editor titles (iOS `VaultItemEditor.title`). */
export const VARIANT_TYPE_KEY: Record<Variant, string> = {
  login: 'vault.type.login',
  secureNote: 'vault.type.secureNote',
  card: 'vault.type.card',
  bankAccount: 'vault.type.bankAccount',
  name: 'vault.type.name',
  personalEmail: 'vault.type.email',
  phone: 'vault.type.phone',
  address: 'vault.type.address',
  company: 'vault.type.company',
  website: 'vault.type.website',
  idCard: 'vault.type.idCard',
  passport: 'vault.type.passport',
  driverLicense: 'vault.type.driverLicense',
}

/** A blank payload with the iOS field defaults. */
export function emptyPayload(variant: Variant): VaultPayload {
  const id = { name: '', countryCode: 'IQ', number: '', issueDate: '', expiryDate: '', issuePlace: '' }
  switch (variant) {
    case 'login':
      return { variant, fields: { itemName: '', email: '', username: '', password: '', website: '', note: '' } }
    case 'secureNote':
      return { variant, fields: { title: '', content: '', color: 'mint', protectItem: false } }
    case 'card':
      return { variant, fields: { itemName: '', accountHolder: '', number: '', securityCode: '', expiryDate: '', note: '', color: 'blue' } }
    case 'bankAccount':
      return { variant, fields: { itemName: '', accountHolder: '', accountNumber: '', routingNumber: '', countryCode: 'IQ', bankName: '' } }
    case 'name':
      return { variant, fields: { type: 'None of these', firstName: '', lastName: '', middleName: '', defaultUsername: '', birthDate: '', birthPlace: '' } }
    case 'personalEmail':
      return { variant, fields: { itemName: '', type: 'Personal', email: '' } }
    case 'phone':
      return { variant, fields: { itemName: '', type: 'Cell', countryCode: 'IQ', number: '' } }
    case 'address':
      return { variant, fields: { itemName: '', address: '', city: '', zipCode: '', countryCode: 'IQ', recipient: '', building: '', floor: '', apartment: '', doorCode: '' } }
    case 'company':
      return { variant, fields: { companyName: '', jobTitle: '' } }
    case 'website':
      return { variant, fields: { itemName: '', websiteURL: '' } }
    case 'idCard':
    case 'passport':
    case 'driverLicense':
      return { variant, fields: { ...id } }
  }
}

/** iOS `VaultPayload.displayName`: the editor saves only when this isn't blank. */
export function displayName(p: VaultPayload): string {
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
      return p.fields.itemName
  }
}

// MARK: detail and copy

export interface DetailField {
  label: string
  value: string
  secret?: boolean
  multiline?: boolean
}

/** iOS `VaultPayload.detailFields`; empty values are dropped. */
export function detailFields(p: VaultPayload, t: T): DetailField[] {
  let fields: DetailField[]
  switch (p.variant) {
    case 'login': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.email'), value: f.email },
        { label: t('field.username'), value: f.username },
        { label: t('field.password'), value: f.password, secret: true },
        { label: t('field.website'), value: f.website },
        { label: t('field.note'), value: f.note, multiline: true },
      ]
      break
    }
    case 'secureNote': {
      const f = p.fields
      fields = [
        { label: t('field.title'), value: f.title },
        { label: t('field.content'), value: f.content, secret: f.protectItem, multiline: true },
        { label: t('field.protected'), value: f.protectItem ? t('common.yes') : t('common.no') },
      ]
      break
    }
    case 'card': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.accountHolder'), value: f.accountHolder },
        { label: t('field.number'), value: f.number, secret: true },
        { label: t('field.securityCode'), value: f.securityCode, secret: true },
        { label: t('field.expiryDate'), value: f.expiryDate },
        { label: t('field.note'), value: f.note, multiline: true },
      ]
      break
    }
    case 'bankAccount': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.accountHolder'), value: f.accountHolder },
        { label: t('field.accountNumber'), value: f.accountNumber, secret: true },
        { label: t('field.routingNumber'), value: f.routingNumber },
        { label: t('field.bankName'), value: f.bankName },
        { label: t('vault.country'), value: countryName(f.countryCode) },
      ]
      break
    }
    case 'name': {
      const f = p.fields
      fields = [
        { label: t('field.type'), value: f.type === 'None of these' ? '' : label(HONORIFICS, f.type, t) },
        { label: t('field.firstName'), value: f.firstName },
        { label: t('field.middleName'), value: f.middleName },
        { label: t('field.lastName'), value: f.lastName },
        { label: t('field.defaultUsername'), value: f.defaultUsername },
        { label: t('field.birthDate'), value: f.birthDate },
        { label: t('field.birthPlace'), value: f.birthPlace },
      ]
      break
    }
    case 'personalEmail': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.type'), value: label(EMAIL_KINDS, f.type, t) },
        { label: t('field.email'), value: f.email },
      ]
      break
    }
    case 'phone': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.type'), value: label(PHONE_KINDS, f.type, t) },
        { label: t('vault.country'), value: countryName(f.countryCode) },
        { label: t('field.phoneNumber'), value: `${dialCode(f.countryCode)} ${f.number}` },
      ]
      break
    }
    case 'address': {
      const f = p.fields
      fields = [
        { label: t('field.itemName'), value: f.itemName },
        { label: t('field.recipient'), value: f.recipient },
        { label: t('field.address'), value: f.address, multiline: true },
        { label: t('field.building'), value: f.building },
        { label: t('field.floor'), value: f.floor },
        { label: t('field.apartment'), value: f.apartment },
        { label: t('field.doorCode'), value: f.doorCode },
        { label: t('field.city'), value: f.city },
        { label: t('field.zipCode'), value: f.zipCode },
        { label: t('vault.country'), value: countryName(f.countryCode) },
      ]
      break
    }
    case 'company':
      fields = [
        { label: t('field.company'), value: p.fields.companyName },
        { label: t('field.jobTitle'), value: p.fields.jobTitle },
      ]
      break
    case 'website':
      fields = [
        { label: t('field.itemName'), value: p.fields.itemName },
        { label: t('field.website'), value: p.fields.websiteURL },
      ]
      break
    default: {
      const f = p.fields
      fields = [
        { label: t('field.name'), value: f.name },
        { label: t('vault.country'), value: countryName(f.countryCode) },
        { label: t('field.number'), value: f.number },
        { label: t('field.issueDate'), value: f.issueDate },
        { label: t('field.expiryDate'), value: f.expiryDate },
      ]
      if (p.variant === 'passport' && f.issuePlace) fields.push({ label: t('field.issuePlace'), value: f.issuePlace })
    }
  }
  return fields.filter((f) => f.value !== '')
}

export interface CopyAction {
  label: string
  value: string
  /** Card and bank fields: the clipboard clears after 30 s (iOS `PaymentClipboard`). */
  payment?: boolean
}

/** iOS `VaultPayload.copyActions`; empty values are dropped. */
export function copyActions(p: VaultPayload, t: T): CopyAction[] {
  let actions: CopyAction[]
  switch (p.variant) {
    case 'login':
      actions = [
        { label: t('copy.email'), value: p.fields.email },
        { label: t('copy.username'), value: p.fields.username },
        { label: t('copy.password'), value: p.fields.password },
        { label: t('copy.website'), value: p.fields.website },
      ]
      break
    case 'secureNote':
      actions = [{ label: t('copy.content'), value: p.fields.content }]
      break
    case 'card':
      actions = [
        { label: t('copy.number'), value: p.fields.number, payment: true },
        { label: t('copy.accountHolder'), value: p.fields.accountHolder, payment: true },
        { label: t('copy.expiry'), value: p.fields.expiryDate, payment: true },
        { label: t('copy.securityCode'), value: p.fields.securityCode, payment: true },
      ]
      break
    case 'bankAccount':
      actions = [
        { label: t('copy.accountNumber'), value: p.fields.accountNumber, payment: true },
        { label: t('copy.routingNumber'), value: p.fields.routingNumber, payment: true },
        { label: t('copy.accountHolder'), value: p.fields.accountHolder, payment: true },
      ]
      break
    case 'name':
      actions = [
        { label: t('copy.name'), value: [p.fields.firstName, p.fields.middleName, p.fields.lastName].filter(Boolean).join(' ') },
        { label: t('copy.username'), value: p.fields.defaultUsername },
      ]
      break
    case 'personalEmail':
      actions = [{ label: t('copy.email'), value: p.fields.email }]
      break
    case 'phone':
      actions = p.fields.number ? [{ label: t('copy.phone'), value: `${dialCode(p.fields.countryCode)}${p.fields.number}` }] : []
      break
    case 'address':
      actions = [
        { label: t('copy.address'), value: p.fields.address },
        { label: t('copy.city'), value: p.fields.city },
        { label: t('copy.zip'), value: p.fields.zipCode },
      ]
      break
    case 'company':
      actions = [
        { label: t('copy.company'), value: p.fields.companyName },
        { label: t('copy.jobTitle'), value: p.fields.jobTitle },
      ]
      break
    case 'website':
      actions = [{ label: t('copy.website'), value: p.fields.websiteURL }]
      break
    default:
      actions = [
        { label: t('copy.name'), value: p.fields.name },
        { label: t('copy.number'), value: p.fields.number },
      ]
  }
  return actions.filter((a) => a.value !== '')
}

/** The row's copy button (iOS `primaryCopy`). */
export function primaryCopy(p: VaultPayload): CopyAction | null {
  const pick = (value: string, payment = false) => (value ? { label: '', value, payment } : null)
  switch (p.variant) {
    case 'login':
      return pick(p.fields.password)
    case 'secureNote':
      return pick(p.fields.title)
    case 'card':
      return pick(p.fields.number, true)
    case 'bankAccount':
      return pick(p.fields.accountNumber, true)
    case 'name':
      return pick([p.fields.firstName, p.fields.lastName].filter(Boolean).join(' '))
    case 'personalEmail':
      return pick(p.fields.email)
    case 'phone':
      return p.fields.number ? pick(`${dialCode(p.fields.countryCode)}${p.fields.number}`) : null
    case 'address':
      return pick(p.fields.address)
    case 'company':
      return pick(p.fields.companyName)
    case 'website':
      return pick(p.fields.websiteURL)
    default:
      return pick(p.fields.number)
  }
}

export function isProtectedNote(p: VaultPayload): boolean {
  return p.variant === 'secureNote' && p.fields.protectItem
}

export function organizationColor(p: VaultPayload): NoteColor | null {
  if (p.variant === 'secureNote' || p.variant === 'card') return p.fields.color
  return null
}

export function websiteUrl(p: VaultPayload): string | null {
  const raw = p.variant === 'login' ? p.fields.website : p.variant === 'website' ? p.fields.websiteURL : ''
  const trimmed = raw.trim()
  if (!trimmed) return null
  return /^[a-z][a-z0-9+.-]*:/i.test(trimmed) ? trimmed : `https://${trimmed}`
}

/** iOS stores these English words in the envelope; show them localized. */
export function localizedSubtitle(subtitle: string, t: T): string {
  if (subtitle === 'Protected') return t('field.protected')
  if (subtitle === 'Card') return t('vault.subtype.card')
  return subtitle
}

// MARK: site logo

const KNOWN_BRANDS: Array<[string, string]> = [
  ['facebook', '#1877F2'], ['messenger', '#0084FF'], ['whatsapp', '#25D366'], ['instagram', '#D62976'],
  ['gmail', '#EA4335'], ['google', '#4285F4'], ['youtube', '#FF0000'], ['snapchat', '#F7C600'],
  ['tiktok', '#111111'], ['telegram', '#229ED9'], ['viber', '#7360F2'], ['twitter', '#1D9BF0'],
  ['linkedin', '#0A66C2'], ['apple', '#333333'], ['icloud', '#3693F3'], ['microsoft', '#00A4EF'],
  ['outlook', '#0078D4'], ['amazon', '#FF9900'], ['netflix', '#E50914'], ['spotify', '#1DB954'],
  ['github', '#24292F'], ['fib', '#0E7A5A'], ['fastpay', '#E2136E'], ['superqi', '#F57C20'],
  ['zain', '#6B2C91'], ['asiacell', '#E30613'], ['korek', '#00843D'],
]
const FALLBACK_COLORS = ['#7C6CF2', '#E07A5F', '#3FA7C9', '#2F9E6C', '#D4537E', '#B7791F', '#5B7CF0']

/** iOS `SiteLogo`: first letter on the brand colour, with the same hash as Android. */
export function siteLogo(name: string): { letter: string; color: string } {
  const trimmed = name.trim()
  const key = trimmed.toLowerCase()
  const first = [...trimmed].find((c) => /[\p{L}\p{N}]/u.test(c))
  const letter = first ? first.toUpperCase() : '?'
  const brand = KNOWN_BRANDS.find(([k]) => key.includes(k))
  if (brand) return { letter, color: brand[1] }
  // Java String.hashCode (UTF-16 units, 32-bit wrap) + floorMod.
  let hash = 0
  for (let i = 0; i < key.length; i++) hash = (Math.imul(hash, 31) + key.charCodeAt(i)) | 0
  const n = FALLBACK_COLORS.length
  return { letter, color: FALLBACK_COLORS[((hash % n) + n) % n] }
}

// MARK: dates

/** iOS `VaultDateFormat.display`: "just now", minutes, hours, then an absolute date. */
export function displayDate(ms: number | null, t: T, language: string, now = Date.now()): string {
  if (ms === null) return t('vault.never')
  const seconds = (now - ms) / 1000
  const absolute = () =>
    new Date(ms).toLocaleString(language, { year: 'numeric', month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' })
  if (seconds < 0) return absolute()
  if (seconds < 60) return t('date.justNow')
  if (seconds < 3600) {
    const m = Math.floor(seconds / 60)
    return m === 1 ? t('date.minuteAgo') : t('date.minutesAgo', m)
  }
  if (seconds < 86400) {
    const h = Math.floor(seconds / 3600)
    return h === 1 ? t('date.hourAgo') : t('date.hoursAgo', h)
  }
  return absolute()
}

/** iOS `VaultListRow.section`: the uppercased first letter, or "#". */
export function letterSection(name: string): string {
  const first = [...name.trim()][0]
  if (!first) return '#'
  return /\p{L}/u.test(first) ? first.toUpperCase() : '#'
}
