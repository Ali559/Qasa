// One short message at a time, bottom-centre, like the iOS copy flash.
import { reactive } from 'vue'

export const toast = reactive({ message: '', visible: false })
let timer: ReturnType<typeof setTimeout> | undefined

export function showToast(message: string, ms = 1800): void {
  toast.message = message
  toast.visible = true
  clearTimeout(timer)
  timer = setTimeout(() => (toast.visible = false), ms)
}
