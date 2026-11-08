// Field detection for login, signup, change-password and one-time-code forms. Pure DOM
// reads, no side effects, so Vitest can run it on happy-dom.
//
// Scope: one content script runs in every frame (manifest `all_frames`) and handles its own
// document, including open shadow roots. It also walks into same-origin child frames whose
// own copy did not run (frames written by script into about:blank, for example); a frame
// whose copy runs marks its window, and the parent leaves it alone.

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
  /** A hidden `autocomplete="username"` field, as change-password forms often have. */
  hiddenUsername?: HTMLInputElement
}

export interface Detection {
  groups: FieldGroup[]
  otpFields: HTMLInputElement[]
}

/** Set on a frame's window by its own content script (see header). */
export const FRAME_MARK = '__qasaContentFrame'

const TEXT_TYPES = new Set(['', 'text', 'email', 'tel', 'password', 'number'])
const USERNAME_HINT = /user|e-?mail|login|account|identifier|ident|phone|member/i
const OTP_HINT = /otp|totp|2fa|mfa|one.?time|security.?code|verification.?code|verify.?code|auth.?code|\bcode\b|token|pin/i
const STEP_HINT = /log.?in|sign.?in|next|continue|weiter|التالي|دخول|چوونەژوورەوە/i

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
  const out: HTMLInputElement[] = []
  const visit = (node: Document | ShadowRoot | Element) => {
    const walker = (node.ownerDocument ?? (node as Document)).createTreeWalker(node, 1 /* SHOW_ELEMENT */)
    for (let el = walker.currentNode as Element | null; el; el = walker.nextNode() as Element | null) {
      if (el.nodeType !== 1) continue
      if (isInput(el)) out.push(el)
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

/** Groups inputs by their form, or by shadow root / document when there is no form. */
function containerOf(input: HTMLInputElement): Element | ShadowRoot | Document {
  return input.form ?? (input.getRootNode() as ShadowRoot | Document)
}

function hasSubmitControl(container: Element | ShadowRoot | Document): boolean {
  return !!container.querySelector('button, input[type=submit], input[type=image], [role=button]')
}

function classify(container: FieldGroup['container'], inputs: HTMLInputElement[]): FieldGroup | null {
  const visible = inputs.filter(fillable)
  const passwords = visible.filter(isPasswordField)
  const hiddenUsername = inputs.find((i) => i.type === 'hidden' && autocompleteTokens(i).includes('username'))
  const textual = visible.filter((i) => !isPasswordField(i) && !isOtpField(i) && i.type !== 'number')

  if (passwords.length === 0) {
    // Step 1 of a multi-step sign-in: one username field and a button.
    const candidates = textual.filter(isUsernameLike)
    if (candidates.length !== 1 || textual.length > 2) return null
    const explicit = autocompleteTokens(candidates[0]).includes('username')
    const formText = 'tagName' in container ? `${container.id} ${container.className} ${container.getAttribute('action') ?? ''} ${container.textContent ?? ''}` : ''
    if (!hasSubmitControl(container) || (!explicit && !STEP_HINT.test(formText))) return null
    return { kind: 'usernameStep', container, username: candidates[0] }
  }

  const first = passwords[0]
  const before = textual.filter((i) => first.compareDocumentPosition(i) & 2 /* PRECEDING */)
  const username = before.filter(isUsernameLike).pop() ?? before.pop()
  const ac = passwords.map(autocompleteTokens)
  const current = passwords.filter((_, i) => ac[i].includes('current-password'))
  const fresh = passwords.filter((_, i) => ac[i].includes('new-password'))
  const looksOld = (i: HTMLInputElement) => /old|current|existing/i.test(hints(i))

  let group: FieldGroup
  if (current.length && fresh.length) {
    group = { kind: 'change', container, password: current[0], newPassword: fresh[0] }
  } else if (passwords.length >= 3) {
    group = { kind: 'change', container, password: passwords[0], newPassword: passwords[1] }
  } else if (passwords.length === 2 && (current[0] === passwords[0] || looksOld(passwords[0]))) {
    group = { kind: 'change', container, password: passwords[0], newPassword: passwords[1] }
  } else if (passwords.length === 2 || fresh.length) {
    group = { kind: 'signup', container, newPassword: fresh[0] ?? passwords[0] }
  } else {
    group = { kind: 'login', container, password: first }
  }
  if (username) group.username = username
  if (hiddenUsername) group.hiddenUsername = hiddenUsername
  return group
}

/** Finds every form group and one-time-code field under `root`. */
export function detect(root: Document | ShadowRoot | Element): Detection {
  const inputs = deepInputs(root)
  const byContainer = new Map<FieldGroup['container'], HTMLInputElement[]>()
  for (const input of inputs) {
    const c = containerOf(input)
    const list = byContainer.get(c)
    if (list) list.push(input)
    else byContainer.set(c, [input])
  }
  const groups: FieldGroup[] = []
  for (const [container, list] of byContainer) {
    const group = classify(container, list)
    if (group) groups.push(group)
  }
  const otpFields = inputs.filter((i) => fillable(i) && isOtpField(i))
  return { groups, otpFields }
}

/** The group a field belongs to, if any. */
export function groupOf(groups: FieldGroup[], field: Element): FieldGroup | undefined {
  return groups.find((g) => g.username === field || g.password === field || g.newPassword === field)
}

/** Fields that get a Qasa icon: where filling makes sense (not signup forms). */
export function iconFields(d: Detection): HTMLInputElement[] {
  const out: HTMLInputElement[] = []
  for (const g of d.groups) {
    if (g.kind === 'signup') continue
    if (g.username) out.push(g.username)
    if (g.password) out.push(g.password)
  }
  return [...out, ...d.otpFields]
}
