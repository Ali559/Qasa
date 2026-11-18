// R2 (docs/autofill-hardening.md): what the page says a new password must look like, sent
// with `generate` so the desktop's shared generator makes one that fits. The desktop
// resolves the length and rules; this only reads the fields.
import type { FieldGroup } from './detect'

export interface GenerateConstraints {
  /** The smallest `maxlength` of the new fields. */
  maxLength?: number
  /** The largest `minlength` of the new fields. */
  minLength?: number
  /** The `passwordrules` attribute (Apple's syntax), when not empty. */
  rules?: string
}

function isNumeric(field: HTMLInputElement): boolean {
  const mode = (field.getAttribute('inputmode') ?? '').toLowerCase()
  if (mode === 'numeric' || mode === 'decimal') return true
  const pattern = field.getAttribute('pattern') ?? ''
  return /^\^?(\\d|\[0-9\])(\*|\+|\{\d+(,\d*)?\})?\$?$/.test(pattern)
}

/** The fields a strong password goes into: a new password and its confirm, or, on a field
 *  R1 calls a login (R7), that one password field. */
export function strongPasswordFields(group: FieldGroup): HTMLInputElement[] {
  const fields = group.kind === 'login' ? [group.password] : [group.newPassword, group.confirmPassword]
  return fields.filter((f): f is HTMLInputElement => !!f)
}

export function generateConstraints(group: FieldGroup): GenerateConstraints {
  const fields = strongPasswordFields(group)
  const out: GenerateConstraints = {}
  const max = fields.map((f) => f.maxLength).filter((n) => n > 0)
  if (max.length) out.maxLength = Math.min(...max)
  const min = fields.map((f) => f.minLength).filter((n) => n > 0)
  if (min.length) out.minLength = Math.max(...min)
  const rules = fields.map((f) => (f.getAttribute('passwordrules') ?? '').trim()).find(Boolean)
  if (rules) out.rules = rules.slice(0, 1000)
  else if (fields.some(isNumeric)) out.rules = 'required: digit'
  return out
}
