// Protected notes ask again before showing or copying their content (iOS
// `unlockIfNeeded`, which asks for Face ID or the device passcode). Until Touch ID /
// Windows Hello are on, the desktop asks for the main password. Either way, once per unlock.
import { reactive } from 'vue'

import { vaultApi } from './api'
import { isCommandError } from './types'

export const protectedGate = reactive({
  open: false,
  /** True once the user confirmed for this unlocked session. */
  confirmed: false,
  resolve: null as ((ok: boolean) => void) | null,
})

/** Set by the UI so the Touch ID / Hello prompt shows a localized reason. */
export const protectedReason = { text: 'Unlock protected note' }

export async function requestProtectedAccess(): Promise<boolean> {
  if (protectedGate.confirmed) return true
  // Touch ID / Windows Hello first when it's on and sealed (iOS asks for Face ID).
  try {
    const bio = await vaultApi.biometricStatus()
    if (bio.enabled && bio.sealed) {
      if (await vaultApi.verifyBiometric(protectedReason.text)) {
        protectedGate.confirmed = true
        return true
      }
    }
  } catch (e) {
    // A dismissed prompt means no; an invalidated seal falls back to the password.
    if (isCommandError(e) && e.code === 'cancelled') return false
  }
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
