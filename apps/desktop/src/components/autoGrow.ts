// `v-auto-grow`: a textarea that grows with its text, like the iOS notes field
// (`TextField(axis: .vertical)`): never shorter than its CSS min-height, never taller than its
// CSS max-height (then it scrolls). Runs on input and whenever the bound value changes.
import type { Directive } from 'vue'

function fit(el: HTMLTextAreaElement): void {
  el.style.height = 'auto'
  // scrollHeight excludes the border; the chrome uses border-box sizing.
  const border = el.offsetHeight - el.clientHeight
  el.style.height = `${el.scrollHeight + border}px`
}

const listeners = new WeakMap<HTMLTextAreaElement, () => void>()

export const vAutoGrow: Directive<HTMLTextAreaElement> = {
  mounted(el) {
    const onInput = () => fit(el)
    listeners.set(el, onInput)
    el.addEventListener('input', onInput)
    requestAnimationFrame(() => fit(el))
  },
  updated(el) {
    fit(el)
  },
  unmounted(el) {
    const onInput = listeners.get(el)
    if (onInput) el.removeEventListener('input', onInput)
    listeners.delete(el)
  },
}
