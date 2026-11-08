// Vault tools in the Rust core (src-tauri/src/vault/tools.rs): health, generator, 2FA, CSV.
import { invoke } from '@tauri-apps/api/core'

export interface HealthReport {
  totalPasswords: number
  reusedCount: number
  weakCount: number
  breachedCount: number
  score: number
  reusedIds: string[]
  weakIds: string[]
  breachedIds: string[]
  breachCounts: Record<string, number>
  breachLocked: boolean
  breachCheckFailed: boolean
}

export interface GeneratorOptions {
  length: number
  uppercase: boolean
  lowercase: boolean
  digits: boolean
  symbols: boolean
}

export interface Generated {
  password: string
  strengthKey: string
  strengthFilled: number
}

export interface TotpSetup {
  uri: string
  issuer: string | null
  accountName: string | null
}

export interface TotpCode {
  code: string
  grouped: string
  secondsRemaining: number
  period: number
}

/** The iOS generator slider range and default (core `generator.rs`). */
export const GENERATOR_MIN = 8
export const GENERATOR_MAX = 32
export const GENERATOR_DEFAULT = 16

const tauriTools = {
  health: () => invoke<HealthReport>('vault_health'),
  generate: (options: GeneratorOptions) => invoke<Generated>('password_generate', { options }),
  totpParse: (input: string, scanned = false) => invoke<TotpSetup>('totp_parse', { input, scanned }),
  totpCode: (id: string) => invoke<TotpCode>('totp_code', { id }),
  totpPreview: (uri: string) => invoke<TotpCode>('totp_preview', { uri }),
  totpFromImage: () => invoke<TotpSetup | null>('totp_from_image'),
  exportCsv: () => invoke<{ cancelled: boolean; exported: number; failures: number }>('vault_export_csv'),
  importCsv: () => invoke<{ cancelled: boolean; imported: number; skipped: number }>('vault_import_csv'),
}

// Browser-only stand-ins for design work (see vault/mock.ts). Not cryptographic.
const mockTools: typeof tauriTools = {
  async health() {
    return {
      totalPasswords: 8, reusedCount: 2, weakCount: 2, breachedCount: 0, score: 64,
      reusedIds: [], weakIds: [], breachedIds: [], breachCounts: {}, breachLocked: true, breachCheckFailed: false,
    }
  },
  async generate(o) {
    const pool = [o.uppercase && 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', o.lowercase && 'abcdefghijklmnopqrstuvwxyz', o.digits && '0123456789', o.symbols && '!@#$%^&*()_+-=']
      .filter(Boolean)
      .join('')
    if (!pool) throw { code: 'invalid', message: 'NoClasses' }
    const bytes = crypto.getRandomValues(new Uint32Array(o.length))
    const password = [...bytes].map((b) => pool[b % pool.length]).join('')
    const filled = o.length >= 20 ? 5 : o.length >= 14 ? 4 : o.length >= 10 ? 2 : 1
    const strengthKey = filled === 5 ? 'gen.veryStrong' : filled === 4 ? 'gen.strong' : filled === 2 ? 'gen.fair' : 'gen.weak'
    return { password, strengthKey, strengthFilled: filled }
  },
  async totpParse(input) {
    return { uri: input.startsWith('otpauth://') ? input : `otpauth://totp/Qasa?secret=${input.replace(/\s/g, '').toUpperCase()}`, issuer: null, accountName: null }
  },
  async totpCode() {
    const s = Math.floor(Date.now() / 1000)
    return { code: '492039', grouped: '492 039', secondsRemaining: 30 - (s % 30), period: 30 }
  },
  async totpPreview() {
    const s = Math.floor(Date.now() / 1000)
    return { code: '492039', grouped: '492 039', secondsRemaining: 30 - (s % 30), period: 30 }
  },
  async totpFromImage() {
    return null
  },
  async exportCsv() {
    return { cancelled: false, exported: 10, failures: 0 }
  },
  async importCsv() {
    return { cancelled: true, imported: 0, skipped: 0 }
  },
}

export const toolsApi: typeof tauriTools = '__TAURI_INTERNALS__' in window ? tauriTools : mockTools
