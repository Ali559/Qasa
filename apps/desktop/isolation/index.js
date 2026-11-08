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
  'vault_biometric_status',
  'vault_set_biometric',
  'vault_unlock_biometric',
  'vault_verify_biometric',
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
  'account_status',
  'account_bootstrap',
  'account_refresh_license',
  'account_sync_locale',
  'devices_list',
  'device_unlink',
  'devices_unlink_others',
  'account_clear_local',
  'account_delete',
  'pricing_get',
  'payment_create_intent',
  'payment_status',
  'payment_simulate_stub',
  'subscription_get',
  'subscription_set_auto_renew',
  'subscription_cancel',
  'payment_method_remove',
  'notifications_list',
  'notifications_unread_count',
  'notification_open',
  'notifications_read_all',
  'notification_delete',
])

window.__TAURI_ISOLATION_HOOK__ = (payload) => {
  const cmd = payload && payload.cmd
  if (typeof cmd === 'string' && (ALLOWED.has(cmd) || cmd.startsWith('plugin:'))) {
    return payload
  }
  console.warn('Qasa isolation: blocked command', cmd)
  return null
}
