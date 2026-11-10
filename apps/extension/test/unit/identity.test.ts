// @vitest-environment happy-dom
// Card and profile detection (identity.ts) and the values each field takes.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'

import { detect, iconFields, identityGroupOf } from '@ext/content/detect'
import { candidates, classifyIdentity, optionFor, type IdentityValues } from '@ext/content/identity'

function loadFixture(name: string): void {
  const html = readFileSync(join(process.cwd(), 'test/fixtures', name), 'utf8')
  document.body.innerHTML = /<body>([\s\S]*)<\/body>/.exec(html)![1].replace(/<script[\s\S]*?<\/script>/g, '')
}

const el = (id: string) => document.getElementById(id) as HTMLInputElement
const input = (attrs: Record<string, string>, label?: string) => {
  const i = document.createElement('input')
  for (const [k, v] of Object.entries(attrs)) i.setAttribute(k, v)
  if (label) {
    const l = document.createElement('label')
    l.textContent = label
    l.append(i)
    document.body.append(l)
  } else document.body.append(i)
  return i
}

beforeEach(() => {
  document.body.innerHTML = ''
})

describe('classifyIdentity', () => {
  it('reads autocomplete tokens first, with sections', () => {
    expect(classifyIdentity(input({ autocomplete: 'shipping postal-code', name: 'first_name' }))).toBe('postal-code')
    expect(classifyIdentity(input({ autocomplete: 'billing cc-number' }))).toBe('cc-number')
    expect(classifyIdentity(input({ autocomplete: 'section-a tel-national' }))).toBe('tel-national')
  })

  it('reads names, ids and labels', () => {
    expect(classifyIdentity(input({ name: 'firstName' }))).toBe('given-name')
    expect(classifyIdentity(input({ id: 'last-name' }))).toBe('family-name')
    expect(classifyIdentity(input({ name: 'exp_month' }))).toBe('cc-exp-month')
    expect(classifyIdentity(input({ placeholder: 'MM / YY' }))).toBe('cc-exp')
    expect(classifyIdentity(input({ name: 'cardholder-name' }))).toBe('cc-name')
    expect(classifyIdentity(input({}, 'Postal code'))).toBe('postal-code')
    expect(classifyIdentity(input({}, 'Company'))).toBe('organization')
    expect(classifyIdentity(input({}, 'المدينة'))).toBe('address-level2')
    expect(classifyIdentity(input({}, 'ژمارەی مۆبایل'))).toBe('tel')
  })

  it('leaves login, search and unrelated fields alone', () => {
    expect(classifyIdentity(input({ name: 'username' }))).toBeNull()
    expect(classifyIdentity(input({ type: 'password', name: 'cvc' }))).toBeNull()
    expect(classifyIdentity(input({ type: 'search', name: 'city' }))).toBeNull()
    expect(classifyIdentity(input({ name: 'coupon_code' }))).toBeNull()
    expect(classifyIdentity(input({ name: 'hotel' }))).toBeNull()
    expect(classifyIdentity(input({ autocomplete: 'email', name: 'login' }))).toBe('email')
  })
})

describe('checkout page', () => {
  it('finds one card group and one profile group in the same form', () => {
    loadFixture('checkout.html')
    const { identity, groups } = detect(document)
    expect(groups).toHaveLength(0)
    expect(identity.map((g) => g.kind)).toEqual(['card', 'profile'])
    const card = identity[0].fields.map((f) => [f.field.id, f.type])
    expect(card).toEqual([
      ['ccnumber', 'cc-number'],
      ['ccname', 'cc-name'],
      ['ccmonth', 'cc-exp-month'],
      ['ccyear', 'cc-exp-year'],
      ['cvc', 'cc-csc'],
      ['hidden-cc', 'cc-csc'],
    ])
    const profile = identity[1].fields.map((f) => [f.field.id, f.type])
    expect(profile).toEqual([
      ['fullname', 'name'],
      ['email', 'email'],
      ['phone', 'tel'],
      ['street', 'street-address'],
      ['line2', 'address-line2'],
      ['city', 'address-level2'],
      ['zip', 'postal-code'],
      ['country', 'country'],
    ])
    expect(identityGroupOf(identity, el('city'))?.kind).toBe('profile')
    // Inputs get an icon; selects don't.
    const icons = iconFields(detect(document)).map((f) => f.id)
    expect(icons).toContain('ccnumber')
    expect(icons).toContain('zip')
    expect(icons).not.toContain('country')
  })

  it('a login form keeps its email as the username, not a profile field', () => {
    loadFixture('login.html')
    expect(detect(document).identity).toEqual([])
  })

  it('a lone name box is not a profile form', () => {
    input({ name: 'name' })
    expect(detect(document).identity).toEqual([])
  })
})

describe('values', () => {
  const card: IdentityValues = { 'cc-number': '4242424242424242', 'cc-exp': '03/29', 'cc-exp-month': '03', 'cc-exp-year': '2029', 'cc-csc': '123' }
  const profile: IdentityValues = { tel: '+9647501234567', 'tel-national': '07501234567', 'tel-country-code': '+964', country: 'IQ', 'country-name': 'Iraq', 'street-address': '100 Salim St' }

  it('picks select options by value, text or number', () => {
    loadFixture('checkout.html')
    const { identity } = detect(document)
    const [cardGroup, profileGroup] = identity
    const month = cardGroup.fields.find((f) => f.type === 'cc-exp-month')!
    expect(optionFor(month.field as HTMLSelectElement, candidates(month, cardGroup.fields, card))?.text).toBe('03')
    const year = cardGroup.fields.find((f) => f.type === 'cc-exp-year')!
    expect(optionFor(year.field as HTMLSelectElement, candidates(year, cardGroup.fields, card))?.text).toBe('2029')
    const country = profileGroup.fields.find((f) => f.type === 'country')!
    expect(optionFor(country.field as HTMLSelectElement, candidates(country, profileGroup.fields, profile))?.value).toBe('IQ')
  })

  it('formats expiry and phone for the field', () => {
    const exp = { field: input({ placeholder: 'MM/YYYY', autocomplete: 'cc-exp' }), type: 'cc-exp' as const }
    expect(candidates(exp, [exp], card)).toEqual(['03/2029'])
    const short = { field: input({ autocomplete: 'cc-exp' }), type: 'cc-exp' as const }
    expect(candidates(short, [short], card)).toEqual(['03/29'])
    const year2 = { field: input({ autocomplete: 'cc-exp-year', maxlength: '2' }), type: 'cc-exp-year' as const }
    expect(candidates(year2, [year2], card)).toEqual(['29'])
    const tel = { field: input({ type: 'tel' }), type: 'tel' as const }
    const code = { field: input({ autocomplete: 'tel-country-code' }), type: 'tel-country-code' as const }
    expect(candidates(tel, [tel], profile)[0]).toBe('+9647501234567')
    expect(candidates(tel, [code, tel], profile)).toEqual(['07501234567'])
    const countryText = { field: input({ name: 'country' }), type: 'country' as const }
    expect(candidates(countryText, [countryText], profile)[0]).toBe('Iraq')
    expect(optionFor(Object.assign(document.createElement('select'), { innerHTML: '<option>03 - March</option>' }), ['03'])?.text).toBe('03 - March')
  })
})
