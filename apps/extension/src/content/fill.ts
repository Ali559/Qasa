// Writing values into page fields so that framework-controlled inputs (React, Vue, Angular)
// see the change. Only ever called after a user action (a trusted menu click or the popup's
// Fill button).

/** Fields we filled, with a digest of the value we put there: a submit that still carries
 *  that exact value is not offered for saving again. Only the digest is kept, so the
 *  secret itself does not outlive the fill in this script. */
const filledByUs = new WeakMap<HTMLInputElement, number>()

/** cyrb53, a fast 53-bit string hash: enough to tell "unchanged" apart without keeping the value. */
function digest(value: string): number {
  let h1 = 0xdeadbeef
  let h2 = 0x41c6ce57
  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i)
    h1 = Math.imul(h1 ^ c, 2654435761)
    h2 = Math.imul(h2 ^ c, 1597334677)
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909)
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909)
  return 4294967296 * (2097151 & h2) + (h1 >>> 0)
}

/**
 * Sets the value through the native `value` setter of the field's own realm (React keeps
 * its own tracker on the instance, which would swallow a plain assignment), then fires
 * `input` and `change` so the framework updates its state.
 */
export function setFieldValue(field: HTMLInputElement, value: string): void {
  const win = field.ownerDocument.defaultView ?? window
  const proto = (win as typeof window).HTMLInputElement?.prototype ?? HTMLInputElement.prototype
  const setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set
  field.focus({ preventScroll: true })
  if (setter) setter.call(field, value)
  else field.value = value
  field.dispatchEvent(new Event('input', { bubbles: true, composed: true }))
  field.dispatchEvent(new Event('change', { bubbles: true, composed: true }))
  filledByUs.set(field, digest(value))
}

export function wasFilledWith(field: HTMLInputElement, value: string): boolean {
  return filledByUs.get(field) === digest(value)
}
