//! Copying secrets. The webview's clipboard API cannot read back reliably (WebKit asks
//! the user to paste), so copying runs here: write, then after `clear_after_ms` clear the
//! clipboard only if it still holds what Qasa wrote (iOS `PaymentClipboard` checks the
//! pasteboard's change count the same way).

use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

static GENERATION: AtomicU64 = AtomicU64::new(0);

#[tauri::command]
pub fn clipboard_copy(value: String, clear_after_ms: Option<u64>) -> Result<(), String> {
    let mut clipboard = arboard::Clipboard::new().map_err(|e| e.to_string())?;
    clipboard.set_text(value.clone()).map_err(|e| e.to_string())?;
    // A newer copy cancels the pending clear of an older one.
    let generation = GENERATION.fetch_add(1, Ordering::SeqCst) + 1;
    if let Some(ms) = clear_after_ms {
        std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(ms));
            if GENERATION.load(Ordering::SeqCst) != generation {
                return;
            }
            if let Ok(mut clipboard) = arboard::Clipboard::new() {
                if clipboard.get_text().ok().as_deref() == Some(value.as_str()) {
                    let _ = clipboard.clear();
                }
            }
        });
    }
    Ok(())
}
