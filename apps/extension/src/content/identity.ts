// Card, address, name and contact fields (docs/autofill-plan.md §3.1, §3.2). Pure DOM reads
// like detect.ts, so Vitest runs it on happy-dom. The rules match Android's
// `AutofillFieldClassifier`: the `autocomplete` token first, then names, ids and labels
// (en/ar/ckb), then the input's type.

/** The HTML `autocomplete` tokens Qasa fills outside login forms. */
export type IdentityType =
  | 'cc-number'
  | 'cc-name'
  | 'cc-exp'
  | 'cc-exp-month'
  | 'cc-exp-year'
  | 'cc-csc'
  | 'name'
  | 'given-name'
  | 'additional-name'
  | 'family-name'
  | 'email'
  | 'tel'
  | 'tel-national'
  | 'tel-country-code'
  | 'street-address'
  | 'address-line1'
  | 'address-line2'
  | 'address-level2'
  | 'postal-code'
  | 'country'
  | 'country-name'
  | 'organization'

export type IdentityElement = HTMLInputElement | HTMLSelectElement

export interface IdentityField {
  field: IdentityElement
  type: IdentityType
}

/** A card form, or a profile form (address, name, contact). One <form> can hold both. */
export interface IdentityGroup {
  kind: 'card' | 'profile'
  container: Element | ShadowRoot | Document
  fields: IdentityField[]
}

const TOKENS = new Set<string>([
  'cc-number', 'cc-name', 'cc-exp', 'cc-exp-month', 'cc-exp-year', 'cc-csc', 'name', 'given-name',
  'additional-name', 'family-name', 'email', 'tel', 'tel-national', 'tel-country-code', 'street-address',
  'address-line1', 'address-line2', 'address-level2', 'postal-code', 'country', 'country-name', 'organization',
])

/** Other spec tokens that map onto ours. */
const ALIASES: Record<string, IdentityType> = {
  'cc-given-name': 'cc-name',
  'cc-family-name': 'cc-name',
  'tel-local': 'tel-national',
  'address-level1': 'address-level2', // never filled: the region isn't stored; see `fillable`
}

export const isCardType = (t: IdentityType): boolean => t.startsWith('cc-')

interface Rule {
  type: IdentityType
  /** Matched against the text with separators removed (Latin) or as is (Arabic script). */
  keys: string[]
  /** Whole words only (`tel` is not in `hotel`). */
  words?: string[]
}

// Checked in order; the first hit wins, so "card name" is a card field and "username" is
// never a person's name.
const RULES: Rule[] = [
  { type: 'cc-csc', keys: ['securitycode', 'cardcode', 'cardverification', 'رمز الأمان', 'کۆدی ئاسایش'], words: ['cvc', 'cvv', 'csc', 'cvn', 'cvv2', 'cid'] },
  { type: 'cc-exp-month', keys: ['expmonth', 'expirymonth', 'expirationmonth', 'ccmonth', 'cardmonth', 'expirationdatemonth'] },
  { type: 'cc-exp-year', keys: ['expyear', 'expiryyear', 'expirationyear', 'ccyear', 'cardyear', 'expirationdateyear'] },
  { type: 'cc-exp', keys: ['expiry', 'expiration', 'expdate', 'ccexp', 'mmyy', 'validthru', 'تاريخ الانتهاء', 'بەسەرچوون'] },
  { type: 'cc-name', keys: ['cardholder', 'nameoncard', 'ccname', 'cardname', 'holdername', 'اسم حامل البطاقة', 'ناوی سەر کارت'] },
  { type: 'cc-number', keys: ['cardnumber', 'ccnumber', 'ccnum', 'cardnum', 'creditcard', 'debitcard', 'رقم البطاقة', 'ژمارەی کارت'] },
  { type: 'email', keys: ['email', 'e-mail', 'البريد', 'ئیمەیڵ'], words: ['mail'] },
  { type: 'tel-country-code', keys: ['countrycode', 'dialcode', 'phonecode', 'callingcode', 'phoneprefix', 'رمز الدولة'] },
  { type: 'tel', keys: ['phone', 'mobile', 'msisdn', 'telephone', 'هاتف', 'جوال', 'موبايل', 'مۆبایل', 'تەلەفۆن'], words: ['tel', 'cell'] },
  { type: 'given-name', keys: ['firstname', 'givenname', 'forename', 'الاسم الأول', 'ناوی یەکەم'], words: ['fname', 'first'] },
  { type: 'additional-name', keys: ['middlename'], words: ['mname', 'middle'] },
  { type: 'family-name', keys: ['lastname', 'surname', 'familyname', 'اسم العائلة', 'ناوی خێزان'], words: ['lname', 'last'] },
  { type: 'address-line2', keys: ['address2', 'addressline2', 'apartment', 'suite', 'الشقة', 'شوقە'], words: ['apt', 'unit', 'line2'] },
  { type: 'postal-code', keys: ['zipcode', 'postal', 'postcode', 'الرمز البريدي', 'کۆدی پۆست'], words: ['zip', 'plz'] },
  { type: 'address-level2', keys: ['city', 'town', 'locality', 'المدينة', 'شار'] },
  { type: 'country', keys: ['country', 'الدولة', 'البلد', 'وڵات'] },
  { type: 'street-address', keys: ['address1', 'addressline1', 'streetaddress', 'street', 'address', 'العنوان', 'ناونیشان'] },
  { type: 'organization', keys: ['company', 'organization', 'organisation', 'employer', 'الشركة', 'کۆمپانیا'] },
  { type: 'name', keys: ['fullname', 'yourname', 'الاسم الكامل', 'ناوی تەواو'], words: ['name', 'الاسم', 'ناو'] },
]

/** Login words: a field that says it is one is left to the login detection. */
const LOGIN_WORDS = /user.?name|user.?id|login|password|passwd|one.?time|otp|search|query|coupon|promo|captcha/i

const SKIPPED_TYPES = new Set(['hidden', 'submit', 'button', 'reset', 'checkbox', 'radio', 'file', 'image', 'range', 'color', 'password', 'search', 'date', 'time', 'datetime-local', 'week', 'month'])

function tokensOf(el: IdentityElement): string[] {
  return (el.getAttribute('autocomplete') ?? '').toLowerCase().split(/\s+/).filter(Boolean)
}

function labelText(el: IdentityElement): string {
  const labels = el.labels ? [...el.labels].map((l) => l.textContent ?? '') : []
  return labels.join(' ')
}

/** The text a field is described by: name, id, placeholder, aria-label and its labels. */
export function describe(el: IdentityElement): string {
  return [
    el.getAttribute('name') ?? '',
    el.id,
    el.getAttribute('placeholder') ?? '',
    el.getAttribute('aria-label') ?? '',
    el.getAttribute('data-testid') ?? '',
    labelText(el),
  ]
    .join(' ')
    .toLowerCase()
}

function matchRules(text: string): IdentityType | null {
  if (!text.trim()) return null
  const compact = text.replace(/[^\p{L}\p{N}]+/gu, '')
  const words = new Set(text.split(/[^\p{L}\p{N}]+/u).filter(Boolean))
  for (const rule of RULES) {
    // eslint-disable-next-line no-control-regex
    if (rule.keys.some((k) => (/[^\x00-\x7f]/.test(k) ? text.includes(k) : compact.includes(k)))) return rule.type
    if (rule.words?.some((w) => words.has(w))) return rule.type
  }
  return null
}

/** The field's identity type, or null when it isn't one (or says it is a login field). */
export function classifyIdentity(el: IdentityElement): IdentityType | null {
  if (el.disabled) return null
  const type = el.tagName === 'SELECT' ? 'select' : (el as HTMLInputElement).type.toLowerCase()
  if (SKIPPED_TYPES.has(type)) return null
  const tokens = tokensOf(el)
  if (tokens.includes('username') || tokens.includes('current-password') || tokens.includes('new-password') || tokens.includes('one-time-code')) return null
  for (const t of tokens) {
    if (TOKENS.has(t)) return t as IdentityType
    if (t in ALIASES && t !== 'address-level1') return ALIASES[t]
  }
  const text = describe(el)
  if (LOGIN_WORDS.test(text)) return null
  const byName = matchRules(text)
  if (byName) return byName
  if (type === 'email') return 'email'
  if (type === 'tel') return 'tel'
  return null
}

/**
 * Card and profile groups among `fields` (one container's inputs and selects). A card
 * group needs a card number; a profile group two profile fields, or one the page marked
 * with an `autocomplete` token, so a lone "name" box is not a profile form.
 */
export function identityGroups(container: IdentityGroup['container'], fields: IdentityElement[], loginFields: Set<Element>): IdentityGroup[] {
  const classified: IdentityField[] = []
  for (const field of fields) {
    if (loginFields.has(field)) continue
    const type = classifyIdentity(field)
    if (type) classified.push({ field, type })
  }
  const card = classified.filter((f) => isCardType(f.type))
  const profile = classified.filter((f) => !isCardType(f.type))
  const out: IdentityGroup[] = []
  if (card.some((f) => f.type === 'cc-number')) out.push({ kind: 'card', container, fields: card })
  const declared = profile.some((f) => tokensOf(f.field).some((t) => TOKENS.has(t)))
  if (profile.length >= 2 || (profile.length === 1 && declared)) out.push({ kind: 'profile', container, fields: profile })
  return out
}

// MARK: values

/** What the desktop's `fillIdentity` returns: values keyed by `autocomplete` token. */
export type IdentityValues = Partial<Record<IdentityType, string>>

const MONTHS = ['january', 'february', 'march', 'april', 'may', 'june', 'july', 'august', 'september', 'october', 'november', 'december']

/** Candidate texts for one field, best first. */
export function candidates(f: IdentityField, all: IdentityField[], v: IdentityValues): string[] {
  const el = f.field
  const select = el.tagName === 'SELECT'
  const max = select ? 0 : (el as HTMLInputElement).maxLength
  const hint = describe(el)
  const out = (...xs: Array<string | undefined>) => xs.filter((x): x is string => !!x)
  switch (f.type) {
    case 'cc-exp': {
      const [mm, yy] = (v['cc-exp'] ?? '').split('/')
      if (!mm || !yy) return []
      return /yyyy/.test(hint) || max >= 7 ? [`${mm}/20${yy}`] : [`${mm}/${yy}`]
    }
    case 'cc-exp-month': {
      const mm = v['cc-exp-month']
      if (!mm) return []
      const name = MONTHS[Number(mm) - 1] ?? ''
      return select ? [mm, String(Number(mm)), name, name.slice(0, 3)] : [mm]
    }
    case 'cc-exp-year': {
      const yyyy = v['cc-exp-year']
      if (!yyyy) return []
      return select ? [yyyy, yyyy.slice(2)] : max === 2 ? [yyyy.slice(2)] : [yyyy]
    }
    case 'tel':
      // With a separate country-code field, the number goes in without its code.
      return all.some((o) => o.type === 'tel-country-code') ? out(v['tel-national']) : out(v.tel, v['tel-national'])
    case 'tel-country-code':
      return out(v['tel-country-code'], v['tel-country-code']?.replace('+', ''))
    case 'street-address':
    case 'address-line1':
      return out(v['address-line1'] ?? v['street-address'])
    case 'country':
    case 'country-name':
      return select ? out(v.country, v['country-name']) : out(v['country-name'], v.country)
    case 'name':
      return out(v.name, [v['given-name'], v['family-name']].filter(Boolean).join(' ') || undefined)
    default:
      return out(v[f.type])
  }
}

/** The option of a <select> matching one of `wanted`, by value or text; numbers compare as numbers. */
export function optionFor(select: HTMLSelectElement, wanted: string[]): HTMLOptionElement | undefined {
  const options = [...select.options]
  const norm = (s: string) => s.trim().toLowerCase()
  for (const w of wanted.map(norm)) {
    const hit =
      options.find((o) => norm(o.value) === w || norm(o.text) === w) ??
      (/^\d+$/.test(w) ? options.find((o) => /^\d+$/.test(norm(o.value)) && Number(o.value) === Number(w)) : undefined)
    if (hit) return hit
  }
  // "03 - March", "IQ - Iraq"
  for (const w of wanted.map(norm)) {
    if (w.length < 2) continue
    const hit = options.find((o) => norm(o.text).startsWith(`${w} `) || norm(o.text).startsWith(`${w}-`))
    if (hit) return hit
  }
  return undefined
}
