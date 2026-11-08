// Copying secrets. Card and bank fields clear after 30 s, 2FA codes after their own
// lifetime (iOS `PaymentClipboard`, `TOTPPasteboard`). The Rust core clears only if the
// clipboard still holds what Qasa put there, so a newer copy by the user is never wiped.
import { invoke } from '@tauri-apps/api/core'

export async function copySecret(value: string, clearAfterMs?: number): Promise<void> {
  if ('__TAURI_INTERNALS__' in window) {
    await invoke('clipboard_copy', { value, clearAfterMs: clearAfterMs ?? null })
  } else {
    await navigator.clipboard.writeText(value)
  }
}

/** 30 seconds, for card numbers, security codes and bank account numbers. */
export const PAYMENT_CLIPBOARD_MS = 30_000
