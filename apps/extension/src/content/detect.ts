// Field detection for login, signup, change-password and one-time-code forms. Pure DOM
// reads, no side effects, so Vitest can run it on happy-dom.
//
// Scope: one content script runs in every frame (manifest `all_frames`) and handles its own
// document, including open shadow roots. It also walks into same-origin child frames whose
// own copy did not run (frames written by script into about:blank, for example); a frame
// whose copy runs marks its window, and the parent leaves it alone.

import { identityGroups, type IdentityElement, type IdentityGroup } from './identity'

export type FormKind = 'login' | 'signup' | 'change' | 'usernameStep'

export interface FieldGroup {
  kind: FormKind
  /** The <form>, or the shadow root / document holding a formless group. */
  container: Element | ShadowRoot | Document
  username?: HTMLInputElement
  /** The current password: what a login fills. */
  password?: HTMLInputElement
  /** Signup and change-password: the password being set. */
  newPassword?: HTMLInputElement
  /** The "type it again" field after [newPassword], when the form has one. */
  confirmPassword?: HTMLInputElement
  /** A hidden `autocomplete="username"` field, as change-password forms often have. */
  hiddenUsername?: HTMLInputElement
}

export interface Detection {
  groups: FieldGroup[]
  /** One-time-code fields; a split code (one box per digit) is listed by its first box. */
  otpFields: HTMLInputElement[]
  /** Split one-time codes: runs of adjacent one-character boxes, filled a digit each. */
  otpRuns: HTMLInputElement[][]
  /** Card and profile forms (identity.ts). */
  identity: IdentityGroup[]
}

/** Set on a frame's window by its own content script (see header). */
export const FRAME_MARK = '__qasaContentFrame'

const TEXT_TYPES = new Set(['', 'text', 'email', 'tel', 'password', 'number'])
const USERNAME_HINT = /user|e-?mail|login|account|identifier|ident|phone|member/i
const OTP_HINT = /otp|totp|2fa|mfa|one.?time|security.?code|verification.?code|verify.?code|auth.?code|\bcode\b|token|pin/i
const STEP_HINT = /log.?in|sign.?in|sign.?up|register|create|next|continue|weiter|التالي|دخول|تسجيل|إنشاء|چوونەژوورەوە|تۆمار|دروستکردن/i

// R1 (docs/autofill-hardening.md): words that mark a password field as the current one or
// a new one. Latin words are matched on hint text split at camelCase, `_` and `-`.
const CURRENT_WORDS = /\b(current|old|existing)\b|الحالية|القديمة|ئێستا|کۆن/i
const NEW_WORDS =
  /\b(new|create|choose|set|reset|confirm\w*|repeat\w*|again|retype|re ?enter|verif\w*)\b|جديدة|تأكيد|إعادة|أعد|نوێ|دووبارە|دڵنیاکردنەوە|پشتڕاستکردنەوە/i
/** `pw2`, `password2`, `pass_2`: the second of two password fields. */
const SECOND_SUFFIX = /(?:pw|pwd|pass|passwd|password)[_-]?2$/i
/** Submit or heading text of a sign-up form (R1.6) and of a login form (which wins). The
 *  bare Arabic تسجيل is not a sign-up word: تسجيل الدخول is "log in"; التسجيل is. "Continue",
 *  "Next" and "Submit" say nothing either way. Kurdish ک / ی and Arabic ك / ي both occur. */
const SIGNUP_WORDS =
  /sign.?up|register|\bcreate\s+(?:an?\s+|your\s+|a\s+new\s+|new\s+)?account\b|\bjoin\b|[إا]نشاء\s+حساب|حساب\s+جديد|التسجيل|تۆمار[\s\u200c]?[کك]ردن|دروست[\s\u200c]?[کك]ردن[یي]\s+هەژمار/i
const LOGIN_WORDS = /log.?in|sign.?in|دخول|چوونەژوورەوە/i
/** Fields only a sign-up form asks for (R1.6): given / family / full name, phone, date of birth. */
const SIGNUP_FIELD_TOKENS = /^(name|given-name|family-name|additional-name|tel|tel-national|tel-local|bday|bday-day|bday-month|bday-year)$/
const SIGNUP_FIELD_HINT = /first.?name|last.?name|given.?name|family.?name|full.?name|surname|birth|\bdob\b|phone|mobile|الاسم الكامل|ناوی تەواو/i
const EMAIL_HINT = /e-?mail|البريد|ئیمەیڵ/i
const ACCOUNT_NAME_HINT = /user.?name|\buser\b|\blogin\b|nick.?name|اسم المستخدم|ناوی بەکارهێنەر/i

/** Inputs that were `type=password` when first seen stay passwords after a reveal toggle. */
const wasPassword = new WeakSet<HTMLInputElement>()

function isInput(el: Element): el is HTMLInputElement {
  return el.tagName === 'INPUT'
}

function autocompleteTokens(input: HTMLInputElement): string[] {
  return (input.getAttribute('autocomplete') ?? '').toLowerCase().split(/\s+/).filter(Boolean)
}

function hints(input: HTMLInputElement): string {
  return [
    input.name,
    input.id,
    input.getAttribute('placeholder') ?? '',
    input.getAttribute('aria-label') ?? '',
    input.getAttribute('data-testid') ?? '',
  ].join(' ')
}

/** The text of the field's <label>s (wrapping or `for`) and `aria-labelledby` targets. */
function labelText(input: HTMLInputElement): string {
  const parts: string[] = []
  try {
    for (const label of Array.from(input.labels ?? [])) parts.push(label.textContent ?? '')
  } catch {
    // `labels` unsupported: the wrapping label below still counts.
  }
  const wrapping = input.closest('label')
  if (wrapping && !parts.length) parts.push(wrapping.textContent ?? '')
  const ids = input.getAttribute('aria-labelledby')
  if (ids) {
    const root = input.getRootNode() as Document | ShadowRoot
    for (const id of ids.split(/\s+/)) parts.push(root.getElementById?.(id)?.textContent ?? '')
  }
  return parts.join(' ')
}

/** Hints plus label text, with camelCase, `_` and `-` split into words. */
function words(input: HTMLInputElement): string {
  return `${hints(input)} ${labelText(input)}`.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_\-.]+/g, ' ')
}

export function isPasswordField(input: HTMLInputElement): boolean {
  if (input.type === 'password') {
    wasPassword.add(input)
    return true
  }
  return wasPassword.has(input) && input.type === 'text'
}

/** `autocomplete="one-time-code"`, or an otp/2fa/code-looking name with 4–8 characters. */
export function isOtpField(input: HTMLInputElement): boolean {
  if (autocompleteTokens(input).includes('one-time-code')) return true
  if (input.type === 'password' || !TEXT_TYPES.has(input.type)) return false
  const max = input.maxLength
  return max >= 4 && max <= 8 && OTP_HINT.test(`${input.name} ${input.id}`)
}

function isUsernameLike(input: HTMLInputElement): boolean {
  const ac = autocompleteTokens(input)
  if (ac.includes('username') || ac.includes('email')) return true
  if (ac.includes('off') && !USERNAME_HINT.test(hints(input))) return false
  return input.type === 'email' || USERNAME_HINT.test(hints(input))
}

/** Every <input> under `root` in tree order, through open shadow roots and script-less
 *  same-origin frames. */
export function deepInputs(root: Document | ShadowRoot | Element): HTMLInputElement[] {
  return deepFields(root).filter(isInput)
}

/** Every <input> and <select> under `root`, the same way as [deepInputs]. */
export function deepFields(root: Document | ShadowRoot | Element): IdentityElement[] {
  const out: IdentityElement[] = []
  const visit = (node: Document | ShadowRoot | Element) => {
    const walker = (node.ownerDocument ?? (node as Document)).createTreeWalker(node, 1 /* SHOW_ELEMENT */)
    for (let el = walker.currentNode as Element | null; el; el = walker.nextNode() as Element | null) {
      if (el.nodeType !== 1) continue
      if (isInput(el) || el.tagName === 'SELECT') out.push(el as IdentityElement)
      if (el.shadowRoot) visit(el.shadowRoot)
      if (el.tagName === 'IFRAME' || el.tagName === 'FRAME') {
        const doc = sameOriginChild(el as HTMLIFrameElement)
        if (doc?.documentElement) visit(doc.documentElement)
      }
    }
  }
  visit(root)
  return out
}

/** A child frame's document, when it is same-origin and has no content script of its own. */
export function sameOriginChild(frame: HTMLIFrameElement): Document | null {
  try {
    const win = frame.contentWindow as (Window & { [FRAME_MARK]?: boolean }) | null
    const doc = frame.contentDocument
    if (!win || !doc || win[FRAME_MARK]) return null
    return doc
  } catch {
    return null // cross-origin: that frame's own script handles it
  }
}

function fillable(input: HTMLInputElement): boolean {
  return input.type !== 'hidden' && TEXT_TYPES.has(input.type) && !input.disabled
}

/** Drawn at all: not `display: none` / `visibility: hidden` (itself or an ancestor), not
 *  `hidden`, not pushed off the page's top or left edge (the usual honeypot). Layout-free
 *  environments (no box sizes at all) only get the style checks. */
export function isRendered(input: HTMLElement): boolean {
  if (!input.isConnected) return false
  const check = (input as HTMLElement & { checkVisibility?: (o?: object) => boolean }).checkVisibility
  if (typeof check === 'function') {
    if (!check.call(input, { visibilityProperty: true })) return false
  } else {
    for (let node: Element | null = input; node; node = node.parentElement ?? ((node.getRootNode() as ShadowRoot).host ?? null)) {
      if ((node as HTMLElement).hidden) return false
      const style = node.ownerDocument.defaultView?.getComputedStyle(node)
      if (style && (style.display === 'none' || (node === input && (style.visibility === 'hidden' || style.visibility === 'collapse')))) return false
    }
  }
  const rect = input.getBoundingClientRect()
  const win = input.ownerDocument.defaultView
  if (win && (rect.width > 0 || rect.height > 0) && (rect.right + win.scrollX <= 0 || rect.bottom + win.scrollY <= 0)) return false
  return true
}

/** The nearest <form> around a field, crossing shadow hosts (a web component per field
 *  still belongs to the page's form); without one, the field's document. */
export function containerOf(input: IdentityElement): Element | ShadowRoot | Document {
  if (input.form) return input.form
  for (let node: Element | null = input; node; ) {
    if (node.tagName === 'FORM') return node
    if (node.parentElement) node = node.parentElement
    else {
      const root = node.getRootNode() as ShadowRoot | Document
      node = 'host' in root ? root.host : null
    }
  }
  return input.ownerDocument
}

type Role = 'current' | 'new' | null

/** R1 step 1–2: what the field's own markers say. */
function markedRole(input: HTMLInputElement): Role {
  const ac = autocompleteTokens(input)
  if (ac.includes('current-password')) return 'current'
  if (ac.includes('new-password')) return 'new'
  const text = words(input)
  if (CURRENT_WORDS.test(text)) return 'current'
  if (NEW_WORDS.test(text) || SECOND_SUFFIX.test(input.name) || SECOND_SUFFIX.test(input.id)) return 'new'
  return null
}

/** Submit controls and headings of a form (not of a whole document: a page header's
 *  "Sign up" link says nothing about the login form under it). */
function formText(container: FieldGroup['container'], selector: string): string {
  if (!('tagName' in container)) return ''
  return Array.from(container.querySelectorAll(selector))
    .map((el) => `${el.textContent ?? ''} ${el.getAttribute('value') ?? ''} ${el.getAttribute('aria-label') ?? ''}`)
    .join(' ')
}

/** R1.6: one unmarked password is new on a sign-up form. */
function looksLikeSignup(container: FieldGroup['container'], textual: HTMLInputElement[], username?: HTMLInputElement): boolean {
  const submits = formText(container, 'button:not([type=button]):not([type=reset]), input[type=submit]')
  if (LOGIN_WORDS.test(submits)) return false
  const asksProfile = textual.some((i) => {
    if (i === username) return false
    if (autocompleteTokens(i).some((t) => SIGNUP_FIELD_TOKENS.test(t))) return true
    return i.type === 'tel' || SIGNUP_FIELD_HINT.test(words(i))
  })
  if (asksProfile) return true
  // A login asks for a username or an email; a sign-up often asks for both.
  const isEmail = (i: HTMLInputElement) => i.type === 'email' || autocompleteTokens(i).includes('email') || EMAIL_HINT.test(words(i))
  const isAccountName = (i: HTMLInputElement) => !isEmail(i) && (autocompleteTokens(i).includes('username') || ACCOUNT_NAME_HINT.test(words(i)))
  if (textual.some(isEmail) && textual.some(isAccountName)) return true
  if (SIGNUP_WORDS.test(submits)) return true
  const headings = formText(container, 'h1, h2, h3, legend')
  return !LOGIN_WORDS.test(headings) && SIGNUP_WORDS.test(headings)
}

/** R1: the role of each password field, in document order. */
export function passwordRoles(passwords: HTMLInputElement[], isSignup: () => boolean): Array<'current' | 'new'> {
  const marked: Role[] = passwords.map(markedRole)
  const roles = [...marked]
  // R1.3: the field right after a marked new one is its confirm.
  for (let i = 1; i < roles.length; i++) if (roles[i] === null && marked[i - 1] === 'new') roles[i] = 'new'
  const anyCurrent = roles.includes('current')
  return roles.map((role, i) => {
    if (role) return role
    if (anyCurrent) return 'new'
    if (passwords.length === 1) return isSignup() ? 'new' : 'current' // R1.6
    if (passwords.length === 2) return 'new' // R1.4
    return i === 0 ? 'current' : 'new' // R1.5
  })
}

function hasSubmitControl(container: Element | ShadowRoot | Document): boolean {
  return !!container.querySelector('button, input[type=submit], input[type=image], [role=button]')
}

function classify(container: FieldGroup['container'], inputs: HTMLInputElement[]): FieldGroup | null {
  const visible = inputs.filter((i) => fillable(i) && isRendered(i))
  const passwords = visible.filter(isPasswordField)
  const hiddenUsername = inputs.find((i) => i.type === 'hidden' && autocompleteTokens(i).includes('username'))
  const textual = visible.filter((i) => !isPasswordField(i) && !isOtpField(i) && i.type !== 'number')

  if (passwords.length === 0) {
    // Step 1 of a multi-step sign-in or sign-up: one username field and a button.
    const candidates = textual.filter(isUsernameLike)
    if (candidates.length !== 1 || textual.length > 2) return null
    const explicit = autocompleteTokens(candidates[0]).includes('username')
    const text = 'tagName' in container ? `${container.id} ${container.className} ${container.getAttribute('action') ?? ''} ${container.textContent ?? ''}` : ''
    if (!hasSubmitControl(container) || (!explicit && !STEP_HINT.test(text))) return null
    return { kind: 'usernameStep', container, username: candidates[0] }
  }

  const first = passwords[0]
  const before = textual.filter((i) => first.compareDocumentPosition(i) & 2 /* PRECEDING */)
  // Only a username-looking field, or, inside a real <form>, the text field right before
  // the password; never any text field of the page (a header search box).
  const inForm = 'tagName' in container
  const username = before.filter(isUsernameLike).pop() ?? (inForm ? before.filter((i) => !/search|query|\bq\b/i.test(words(i))).pop() : undefined)
  const roles = passwordRoles(passwords, () => looksLikeSignup(container, textual, username))
  const current = passwords.filter((_, i) => roles[i] === 'current')
  const fresh = passwords.filter((_, i) => roles[i] === 'new')

  let group: FieldGroup
  if (!fresh.length) group = { kind: 'login', container, password: current[0] ?? first }
  else if (current.length) group = { kind: 'change', container, password: current[0], newPassword: fresh[0], confirmPassword: fresh[1] }
  else group = { kind: 'signup', container, newPassword: fresh[0], confirmPassword: fresh[1] }
  if (username) group.username = username
  if (hiddenUsername) group.hiddenUsername = hiddenUsername
  return group
}

/** Split one-time codes: 4–8 adjacent rendered one-character boxes of the same container. */
function otpRunsOf(inputs: HTMLInputElement[]): HTMLInputElement[][] {
  const runs: HTMLInputElement[][] = []
  let run: HTMLInputElement[] = []
  const flush = () => {
    if (run.length >= 4 && run.length <= 8) runs.push(run)
    run = []
  }
  for (const i of inputs) {
    const box = fillable(i) && i.type !== 'password' && i.maxLength === 1 && isRendered(i)
    if (!box) {
      if (i.type !== 'hidden') flush()
      continue
    }
    if (run.length && containerOf(run[0]) !== containerOf(i)) flush()
    run.push(i)
  }
  flush()
  return runs
}

/** Finds every form group and one-time-code field under `root`. */
export function detect(root: Document | ShadowRoot | Element): Detection {
  const all = deepFields(root)
  const inputs = all.filter(isInput)
  const byContainer = new Map<FieldGroup['container'], IdentityElement[]>()
  for (const field of all) {
    const c = containerOf(field)
    const list = byContainer.get(c)
    if (list) list.push(field)
    else byContainer.set(c, [field])
  }
  const groups: FieldGroup[] = []
  const identity: IdentityGroup[] = []
  for (const [container, list] of byContainer) {
    const group = classify(container, list.filter(isInput))
    if (group) groups.push(group)
    // Fields a login form uses are never also a profile's (an email that is the username).
    const loginFields = new Set<Element>(group ? [group.username, group.password, group.newPassword].filter((f): f is HTMLInputElement => !!f) : [])
    const otp = list.filter((f) => isInput(f) && isOtpField(f))
    for (const f of otp) loginFields.add(f)
    identity.push(...identityGroups(container, list, loginFields))
  }
  const otpRuns = otpRunsOf(inputs)
  const otpFields = inputs.filter((i) => fillable(i) && isOtpField(i) && isRendered(i))
  for (const run of otpRuns) if (!otpFields.includes(run[0])) otpFields.push(run[0])
  return { groups, otpFields, otpRuns, identity }
}

/** The card or profile group a field belongs to, if any. */
export function identityGroupOf(groups: IdentityGroup[], field: Element): IdentityGroup | undefined {
  return groups.find((g) => g.fields.some((f) => f.field === field))
}

/** The group a field belongs to, if any. */
export function groupOf(groups: FieldGroup[], field: Element): FieldGroup | undefined {
  return groups.find((g) => g.username === field || g.password === field || g.newPassword === field || g.confirmPassword === field)
}

/** Fields that get a Qasa icon: where filling makes sense, and the new-password field of a
 *  signup or change-password form (its menu suggests a strong password, design rule 6c). A
 *  signup form's username gets none: there is nothing saved to fill there. Card and profile
 *  fields get one too, except selects (no room for it). */
export function iconFields(d: Detection): HTMLInputElement[] {
  const out: HTMLInputElement[] = []
  for (const g of d.groups) {
    if (g.kind !== 'signup') {
      if (g.username) out.push(g.username)
      if (g.password) out.push(g.password)
    }
    if (g.newPassword) out.push(g.newPassword)
  }
  for (const g of d.identity) {
    for (const f of g.fields) if (isInput(f.field) && !out.includes(f.field)) out.push(f.field)
  }
  return [...out, ...d.otpFields]
}
