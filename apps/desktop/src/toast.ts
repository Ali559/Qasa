// One short message at a time, bottom-centre (components/ToastHost.vue), like the iOS
// copy flash. A new message replaces the one showing and restarts its timer.
import { reactive } from 'vue'

/** How long a toast stays up by default. */
export const TOAST_MS = 2500

/** `success` (the default: saved, copied…) is the accent pill with a check; `error` says
 *  something didn't work, without the check. */
export type ToastTone = 'success' | 'error'

export const toast = reactive({ message: '', visible: false, tone: 'success' as ToastTone, seq: 0 })
let timer: ReturnType<typeof setTimeout> | undefined

export function showToast(message: string, options: { tone?: ToastTone; ms?: number } = {}): void {
  toast.message = message
  toast.tone = options.tone ?? 'success'
  toast.visible = true
  toast.seq++
  clearTimeout(timer)
  timer = setTimeout(() => (toast.visible = false), options.ms ?? TOAST_MS)
}
