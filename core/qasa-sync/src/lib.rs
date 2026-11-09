//! Qasa Wi‑Fi vault sync (docs/desktop-extension-plan.md §6), shared by the desktop and,
//! through UniFFI, the phones.
//!
//! - [`channel`]: a short one-time code, stretched with Argon2id, drives SPAKE2; both
//!   sides prove they derived the same key before anything else is said, then talk over
//!   AES-256-GCM with per-direction keys and counters. A wrong code learns nothing and
//!   gets nothing.
//! - [`session`]: who has which vault, who adopts whose account, and the exchange of
//!   encrypted item snapshots. Item contents stay encrypted with the vault key throughout:
//!   the channel adds a second layer, it never sees plaintext items.
//! - [`merge`]: last writer wins per item, with tombstones.
//!
//! Discovery (Bonjour / mDNS) and sockets stay platform-native: this crate runs over any
//! `Read + Write` stream.

pub mod channel;
pub mod merge;
pub mod session;
pub mod wire;

pub use channel::{generate_code, Channel, Role, SyncError};
pub use merge::{merge, MergePlan};
pub use session::{run, Adoption, Outcome, SyncStore};
pub use wire::{Intro, Snapshot, VaultMeta, WireCollection, WireItem};

/// Bumped for any incompatible protocol change; peers with a different version refuse.
/// 2: folder names cross encrypted (`WireCollection::name_enc`).
pub const PROTOCOL_VERSION: u32 = 2;
/// The Bonjour / mDNS service type.
pub const SERVICE_TYPE: &str = "_qasa-sync._tcp";
