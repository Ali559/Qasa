// Opens a website in the user's default browser (never inside Qasa's window).
import { invoke } from '@tauri-apps/api/core'

export async function openUrl(url: string): Promise<void> {
  if (!/^https?:\/\//i.test(url)) return
  if ('__TAURI_INTERNALS__' in window) await invoke('open_url', { url })
  else window.open(url, '_blank', 'noopener')
}
