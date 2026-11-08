// Save / update capture. On a form submit (submit event, Enter in a field, or a click on a
// submit-looking button) we read the username and the password being used or set, and ask
// the service worker to send `save`; the desktop then asks the user. Step 1 of a
// multi-step sign-in only hands its username to the service worker, which keeps it in
// memory for this tab until step 2.
import type { FieldGroup } from './detect'
import { wasFilledWith } from './fill'

export type SaveMessage =
  | { kind: 'save'; username: string; password: string }
  | { kind: 'rememberUsername'; username: string }

const SUBMIT_TEXT = /log.?in|sign.?in|sign.?up|register|create|submit|save|change|update|continue|next|send|دخول|تسجيل|حفظ|التالي|چوونەژوورەوە|تۆمار|پاشەکەوت/i

/** What submitting this group should send, or null (nothing typed, or just our own fill). */
export function saveMessageFor(group: FieldGroup): SaveMessage | null {
  const username = (group.username?.value || group.hiddenUsername?.value || '').trim()
  if (group.kind === 'usernameStep') return username ? { kind: 'rememberUsername', username } : null
  const field = group.kind === 'login' ? group.password : group.newPassword
  const password = field?.value ?? ''
  if (!field || !password) return null
  if (wasFilledWith(field, password)) return null
  return { kind: 'save', username, password }
}

/** A click on this element submits its form, as far as we can tell. */
export function isSubmitControl(el: Element): boolean {
  const button = el.closest('button, input[type=submit], input[type=image], [role=button]')
  if (!button) return false
  if (button.tagName === 'INPUT') return true
  const type = button.getAttribute('type')
  if (button.tagName === 'BUTTON' && (type === null || type === 'submit')) return true
  return SUBMIT_TEXT.test(`${button.textContent ?? ''} ${button.getAttribute('aria-label') ?? ''} ${button.id}`)
}

/** The group whose container holds `el`, crossing shadow roots upwards. */
export function groupContaining(groups: FieldGroup[], el: Element): FieldGroup | undefined {
  for (let node: Node | null = el; node; node = node.parentNode ?? (node as ShadowRoot).host ?? null) {
    const hit = groups.find((g) => g.container === node)
    if (hit) return hit
  }
  return undefined
}

/** Remembers what was last sent so a submit seen as both click and submit goes once. */
export class SaveDeduper {
  private last = ''
  private at = 0

  shouldSend(message: SaveMessage, now = Date.now()): boolean {
    const key = JSON.stringify(message)
    if (key === this.last && now - this.at < 3_000) return false
    this.last = key
    this.at = now
    return true
  }
}
