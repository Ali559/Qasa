//! Site icons (docs/site-icons.md): a login's tile shows its site's own favicon, asked only
//! of that site over HTTPS and never of a third party. Icons are cached per device,
//! encrypted with the vault key (`cache`), and fetched lazily by `queue` while the vault is
//! unlocked and the "Website icons" setting is on. Without one the tile shows the calm
//! letter (`letter`).

pub mod cache;
pub mod decode;
pub mod fetch;
pub mod host;
pub mod pick;
pub mod queue;

#[cfg(test)]
mod tests;

pub use host::host_of;

/// The tile letter for an item name: its first letter or digit, upper-cased, or `?`. The
/// UI draws it (`siteLetter` in `src/vault/present.ts`); this copy is held to the same
/// vectors so every platform agrees.
#[cfg_attr(not(test), allow(dead_code))]
pub fn letter(name: &str) -> String {
    name.trim().chars().find(|c| c.is_alphanumeric()).map(|c| c.to_uppercase().collect()).unwrap_or_else(|| "?".into())
}
