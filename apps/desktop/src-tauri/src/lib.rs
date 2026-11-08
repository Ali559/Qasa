//! The Qasa desktop core process. The webview only draws screens: keys, the vault and
//! every network call live here (docs/desktop-extension-plan.md §3).

mod prefs;

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![prefs::get_prefs, prefs::set_prefs])
        .run(tauri::generate_context!())
        .expect("error while running Qasa");
}
