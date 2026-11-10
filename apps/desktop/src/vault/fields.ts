// Field rules from the Rust core (src-tauri/src/vault/fields.rs over
// core/qasa-vault/src/validate.rs): validation, the stored canonical form, as-you-type
// card formatting and date reading. One implementation for every app; this file only
// turns a `Problem` into the localized message and formats a parsed date for display.
import { invoke } from '@tauri-apps/api/core'

import type { VaultPayload } from './types'

/** `qasa_vault::validate::Problem` as serde sends it (params inline, snake_case). */
export type Problem =
  | { kind: 'required' }
  | { kind: 'tooLong'; max: number }
  | { kind: 'digitsOnly' }
  | { kind: 'lettersDigitsOnly' }
  | { kind: 'cardNumberLength' }
  | { kind: 'cardNumberChecksum' }
  | { kind: 'securityCodeLength'; min: number; max: number }
  | { kind: 'expiryFormat' }
  | { kind: 'expiryMonth' }
  | { kind: 'expiryTooFar'; max_years: number }
  | { kind: 'dateInvalid' }
  | { kind: 'dateInFuture' }
  | { kind: 'dateTooOld' }
  | { kind: 'dateBeforeIssue' }
  | { kind: 'dateTooFar'; max_years: number }
  | { kind: 'emailInvalid' }
  | { kind: 'urlInvalid' }
  | { kind: 'phoneLength'; min: number; max: number }
  | { kind: 'phoneCountryMismatch' }
  | { kind: 'ibanInvalid' }
  | { kind: 'ibanChecksum' }
  | { kind: 'routingInvalid' }
  | { kind: 'countryInvalid' }
  | { kind: 'totpInvalid' }

/** One problem with one payload field; `field` is the payload's JSON key (`expiryDate`). */
export interface FieldIssue {
  field: string
  problem: Problem
}

/** A calendar day with no time zone (`CivilDate`). */
export interface CivilDate {
  year: number
  month: number
  day: number
}

export type CardBrand = 'visa' | 'mastercard' | 'amex' | 'discover' | 'dinersClub' | 'jcb' | 'unionPay' | 'maestro' | 'mir' | 'unknown'

export interface CardBrandInfo {
  brand: CardBrand
  /** `[min, max]` security code digits. */
  securityCodeDigits: [number, number]
}

type T = (key: string, ...args: Array<string | number>) => string

/** The localized message for a problem. */
export function issueMessage(problem: Problem, t: T): string {
  switch (problem.kind) {
    case 'tooLong':
      return t('validation.tooLong', problem.max)
    case 'securityCodeLength':
      return problem.min === problem.max
        ? t('validation.securityCodeExact', problem.min)
        : t('validation.securityCodeRange', problem.min, problem.max)
    case 'expiryTooFar':
    case 'dateTooFar':
      return t('validation.yearsAway', problem.max_years)
    case 'phoneLength':
      return t('validation.phoneLength', problem.min, problem.max)
    default:
      return t(`validation.${problem.kind}`)
  }
}

/** Issues keyed by field (the core returns at most one per field). */
export function issuesByField(issues: FieldIssue[]): Record<string, Problem> {
  return Object.fromEntries(issues.map((i) => [i.field, i.problem]))
}

/** A parsed date in the UI language ("Nov 6, 2000", "٦ تشرين الثاني ٢٠٠٠"). */
export function formatCivilDate(d: CivilDate, language: string): string {
  return new Date(d.year, d.month - 1, d.day).toLocaleDateString(language, { month: 'short', day: 'numeric', year: 'numeric' })
}

/** `YYYY-MM-DD`, the stored form. */
export function isoDate(d: CivilDate): string {
  return `${String(d.year).padStart(4, '0')}-${String(d.month).padStart(2, '0')}-${String(d.day).padStart(2, '0')}`
}

const tauriFields = {
  validate: (payload: VaultPayload) => invoke<FieldIssue[]>('validate_item', { payload }),
  normalize: (payload: VaultPayload) => invoke<VaultPayload>('normalize_item', { payload }),
  formatCardNumber: (raw: string) => invoke<string>('format_card_number', { raw }),
  formatCardExpiry: (raw: string) => invoke<string>('format_card_expiry', { raw }),
  cardBrand: (number: string) => invoke<CardBrandInfo>('card_brand', { number }),
  parseDate: (raw: string) => invoke<CivilDate | null>('parse_vault_date', { raw }),
  parseDates: (raws: string[]) => invoke<Array<CivilDate | null>>('parse_vault_dates', { raws }),
}

// Browser-only stand-ins for design work (see vault/mock.ts): no rules, simple formatting,
// ISO dates only. The app always uses the Rust core.
function mockParse(raw: string): CivilDate | null {
  const m = raw.trim().match(/^(\d{4})-(\d{2})-(\d{2})$/)
  return m ? { year: +m[1], month: +m[2], day: +m[3] } : null
}

const mockFields: typeof tauriFields = {
  async validate() {
    return []
  },
  async normalize(payload) {
    return JSON.parse(JSON.stringify(payload)) as VaultPayload
  },
  async formatCardNumber(raw) {
    return raw.replace(/\D/g, '').slice(0, 19).replace(/(\d{4})(?=\d)/g, '$1 ')
  },
  async formatCardExpiry(raw) {
    const d = raw.replace(/\D/g, '').replace(/^[2-9]/, '0$&').slice(0, 4)
    return d.length <= 2 ? d : `${d.slice(0, 2)}/${d.slice(2)}`
  },
  async cardBrand() {
    return { brand: 'unknown', securityCodeDigits: [3, 4] }
  },
  async parseDate(raw) {
    return mockParse(raw)
  },
  async parseDates(raws) {
    return raws.map(mockParse)
  },
}

export const fieldsApi: typeof tauriFields = '__TAURI_INTERNALS__' in window ? tauriFields : mockFields
