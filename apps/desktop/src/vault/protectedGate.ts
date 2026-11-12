// Copying a protected note's content from the list asks first (design/README.md rule 6b):
// the `ProtectedUnlockSheet` (Touch ID / Windows Hello by itself when on, else the main
// password). Every copy asks again; nothing is remembered. Opening the item has its own
// gate (`protectedItemGate.ts`) in the detail pane.
import { reactive } from 'vue'

export const protectedGate = reactive({
  open: false,
  resolve: null as ((ok: boolean) => void) | null,
})

export function requestProtectedAccess(): Promise<boolean> {
  // A second request while one is open answers the first with no.
  protectedGate.resolve?.(false)
  return new Promise((resolve) => {
    protectedGate.resolve = resolve
    protectedGate.open = true
  })
}

export function finishProtectedAccess(ok: boolean): void {
  protectedGate.open = false
  protectedGate.resolve?.(ok)
  protectedGate.resolve = null
}

/** The vault locked: an open request is answered with no. */
export function resetProtectedAccess(): void {
  if (protectedGate.open || protectedGate.resolve) finishProtectedAccess(false)
}
