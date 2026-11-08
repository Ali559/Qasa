// Tauri isolation hook: every IPC message from the webview passes through here, in a
// separate sandboxed frame, before it reaches the Rust core. Only Qasa's own commands and
// the core plugin are let through; anything else is dropped.
const ALLOWED = new Set(['get_prefs', 'set_prefs'])

window.__TAURI_ISOLATION_HOOK__ = (payload) => {
  const cmd = payload && payload.cmd
  if (typeof cmd === 'string' && (ALLOWED.has(cmd) || cmd.startsWith('plugin:'))) {
    return payload
  }
  console.warn('Qasa isolation: blocked command', cmd)
  return null
}
