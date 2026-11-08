// Protected notes ask again before showing or copying their content (iOS
// `unlockIfNeeded`, which asks for Face ID or the device passcode). Until Touch ID /
// Windows Hello arrive (phase 3) the desktop asks for the main password, once per unlock.
import { reactive } from 'vue'

export const protectedGate = reactive({
  open: false,
  /** True once the user confirmed for this unlocked session. */
  confirmed: false,
  resolve: null as ((ok: boolean) => void) | null,
})

export function requestProtectedAccess(): Promise<boolean> {
  if (protectedGate.confirmed) return Promise.resolve(true)
  return new Promise((resolve) => {
    protectedGate.resolve = resolve
    protectedGate.open = true
  })
}

export function finishProtectedAccess(ok: boolean): void {
  if (ok) protectedGate.confirmed = true
  protectedGate.open = false
  protectedGate.resolve?.(ok)
  protectedGate.resolve = null
}

/** Forget the confirmation when the vault locks. */
export function resetProtectedAccess(): void {
  protectedGate.confirmed = false
}
