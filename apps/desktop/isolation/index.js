// Tauri isolation hook: every IPC message from the webview passes through here, in a
// separate sandboxed frame, before it reaches the Rust core. Only Qasa's own commands and
// Tauri's core plugins are let through; anything else is dropped. Add new commands here.
const ALLOWED = new Set([
  'get_prefs',
  'set_prefs',
  'clipboard_copy',
  'open_url',
  'vault_status',
  'vault_create',
  'vault_unlock',
  'vault_lock',
  'vault_verify_password',
  'vault_note_activity',
  'vault_list',
  'vault_get',
  'vault_save',
  'vault_delete',
  'vault_mark_used',
  'vault_change_password',
  'vault_wipe',
  'collections_list',
  'collection_create',
  'collection_rename',
  'collection_delete',
  'vault_health',
  'password_generate',
  'totp_parse',
  'totp_code',
  'totp_preview',
  'totp_from_image',
  'vault_export_csv',
  'vault_import_csv',
])

window.__TAURI_ISOLATION_HOOK__ = (payload) => {
  const cmd = payload && payload.cmd
  if (typeof cmd === 'string' && (ALLOWED.has(cmd) || cmd.startsWith('plugin:'))) {
    return payload
  }
  console.warn('Qasa isolation: blocked command', cmd)
  return null
}
