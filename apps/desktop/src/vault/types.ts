// Vault types as the Rust core sends them (core/qasa-vault/src/payload.rs, UI shape:
// {"variant": …, "fields": {…}}). Field names and defaults mirror iOS `VaultEnums.swift`.
import type { FieldIssue } from './fields'

export type ItemKind = 'login' | 'secureNote' | 'payment' | 'personalInfo' | 'idDocument'

export type NoteColor = 'mint' | 'gold' | 'blue' | 'purple' | 'red' | 'gray'
export type Honorific = 'Mr' | 'Mrs' | 'Ms' | 'Mx' | 'Miss' | 'None of these'
export type EmailKind = 'Personal' | 'Work'
export type PhoneKind = 'Cell' | 'Work' | 'Work Cell' | 'Work Fax' | 'Fax' | 'Home Phone' | 'Other'

export interface LoginFields {
  itemName: string
  email: string
  username: string
  password: string
  website: string
  note: string
  collectionId?: string
  totp?: string
}
export interface SecureNoteFields {
  title: string
  content: string
  color: NoteColor
  collectionId?: string
  protectItem: boolean
}
export interface CardFields {
  itemName: string
  accountHolder: string
  number: string
  securityCode: string
  expiryDate: string
  note: string
  color: NoteColor
}
export interface BankAccountFields {
  itemName: string
  accountHolder: string
  accountNumber: string
  routingNumber: string
  countryCode: string
  bankName: string
}
export interface NameFields {
  type: Honorific
  firstName: string
  lastName: string
  middleName: string
  defaultUsername: string
  birthDate: string
  birthPlace: string
}
export interface PersonalEmailFields {
  itemName: string
  type: EmailKind
  email: string
}
export interface PhoneFields {
  itemName: string
  type: PhoneKind
  countryCode: string
  number: string
}
export interface AddressFields {
  itemName: string
  address: string
  city: string
  zipCode: string
  countryCode: string
  recipient: string
  building: string
  floor: string
  apartment: string
  doorCode: string
  phoneItemId?: string
}
export interface CompanyFields {
  companyName: string
  jobTitle: string
}
export interface WebsiteFields {
  itemName: string
  websiteURL: string
}
export interface IdDocumentFields {
  name: string
  countryCode: string
  number: string
  issueDate: string
  expiryDate: string
  issuePlace: string
}

export type VaultPayload =
  | { variant: 'login'; fields: LoginFields }
  | { variant: 'secureNote'; fields: SecureNoteFields }
  | { variant: 'card'; fields: CardFields }
  | { variant: 'bankAccount'; fields: BankAccountFields }
  | { variant: 'name'; fields: NameFields }
  | { variant: 'personalEmail'; fields: PersonalEmailFields }
  | { variant: 'phone'; fields: PhoneFields }
  | { variant: 'address'; fields: AddressFields }
  | { variant: 'company'; fields: CompanyFields }
  | { variant: 'website'; fields: WebsiteFields }
  | { variant: 'idCard'; fields: IdDocumentFields }
  | { variant: 'passport'; fields: IdDocumentFields }
  | { variant: 'driverLicense'; fields: IdDocumentFields }

export type Variant = VaultPayload['variant']

/** iOS `VaultListRow`, plus the payload variant for the row icon. */
export interface ItemSummary {
  id: string
  kind: ItemKind
  variant: Variant
  displayName: string
  subtitle: string
  createdAt: number
  updatedAt: number
  lastUsedAt: number | null
  collectionId: string | null
  hasPrimaryCopy: boolean
  hasTotp: boolean
  /** A login's website host (bridge `host_of`), for its bundled site icon; else null. */
  siteHost: string | null
  /** A note or card's colour (iOS `organizationColor`), for its row tile; else null. */
  color: NoteColor | null
}

export interface Collection {
  id: string
  name: string
  createdAt: number
  itemCount: number
}

/** Touch ID / Windows Hello state (src-tauri/src/vault/biometrics.rs). */
export interface BiometricStatus {
  kind: 'none' | 'touchId' | 'windowsHello'
  enabled: boolean
  /** Enabled but not sealed: sealed on the next main-password unlock (iOS wording). */
  sealed: boolean
  /** The main password is due again: biometric unlock is refused until it is typed. */
  masterPasswordDue: boolean
  /** Days between required main-password unlocks (Rust `MASTER_PASSWORD_INTERVAL_DAYS`). */
  masterPasswordIntervalDays: number
}

export interface VaultStatus {
  exists: boolean
  unlocked: boolean
  unsupportedKdfVersion: number | null
}

/** Errors from the Rust core: switch on `code`, never show `message` to users. */
export interface CommandError {
  code:
    | 'locked'
    | 'noVault'
    | 'vaultExists'
    | 'wrongPassword'
    | 'weakPassword'
    | 'unsupportedKdf'
    | 'notFound'
    | 'invalid'
    | 'invalidFields'
    | 'storage'
    | 'corrupt'
    | 'biometricInvalidated'
    | 'cancelled'
    | 'biometricUnavailable'
    | 'masterPasswordDue'
  message: string
  /** With `invalidFields`: what to fix (nothing was saved). */
  issues?: FieldIssue[]
}

export function isCommandError(e: unknown): e is CommandError {
  return typeof e === 'object' && e !== null && 'code' in e && 'message' in e
}

/** The kind each variant belongs to (iOS `VaultPayload.kind`). */
export const VARIANT_KIND: Record<Variant, ItemKind> = {
  login: 'login',
  secureNote: 'secureNote',
  card: 'payment',
  bankAccount: 'payment',
  name: 'personalInfo',
  personalEmail: 'personalInfo',
  phone: 'personalInfo',
  address: 'personalInfo',
  company: 'personalInfo',
  website: 'personalInfo',
  idCard: 'idDocument',
  passport: 'idDocument',
  driverLicense: 'idDocument',
}
