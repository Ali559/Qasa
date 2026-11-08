//! One sync session over an open [`Channel`]: introductions, the vault decision, the
//! snapshot exchange, and the merge.
//!
//! The decision is computed the same way on both sides from both intros:
//! - one side has no vault: it adopts the other's vault and account (a new device); the
//!   account id is only ever taken from inside the authenticated channel (this closes the
//!   rebinding hole in design.md §12.9);
//! - both have the same vault (same salt and verifier): both merge;
//! - different vaults: refuse, nothing changes;
//! - neither has a vault: refuse.

use std::io::{Read, Write};

use crate::channel::{Channel, Role, SyncError};
use crate::merge::{merge, MergePlan};
use crate::wire::{Intro, Message, Snapshot, VaultMeta};
use crate::PROTOCOL_VERSION;

/// What a new device takes from the other side.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Adoption {
    pub account_id: Option<String>,
    pub vault: VaultMeta,
}

/// The platform's vault, as the session needs it.
pub trait SyncStore {
    fn intro(&self) -> Result<Intro, SyncError>;
    /// Every item and collection, tombstones included, payloads in the canonical shape.
    fn snapshot(&self) -> Result<Snapshot, SyncError>;
    /// A new device: store the peer's vault key material and account id. The vault stays
    /// locked; the user unlocks with the same main password.
    fn adopt(&mut self, adoption: &Adoption) -> Result<(), SyncError>;
    /// Writes the winning remote rows. Returns how many were written.
    fn apply(&mut self, plan: &MergePlan) -> Result<u32, SyncError>;
    fn now_ms(&self) -> i64;
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Outcome {
    pub peer_name: String,
    pub peer_platform: String,
    /// This device took the other's vault (it had none).
    pub adopted: bool,
    /// Rows written here.
    pub received: u32,
    /// Rows the other device wrote.
    pub sent: u32,
}

enum Plan {
    Adopt(Adoption),
    Give,
    Merge,
}

fn decide(mine: &Intro, theirs: &Intro) -> Result<Plan, SyncError> {
    if theirs.protocol != PROTOCOL_VERSION {
        return Err(SyncError::Version);
    }
    match (&mine.vault, &theirs.vault) {
        (None, None) => Err(SyncError::NoVault),
        (None, Some(v)) => Ok(Plan::Adopt(Adoption { account_id: theirs.account_id.clone(), vault: v.clone() })),
        (Some(_), None) => Ok(Plan::Give),
        (Some(a), Some(b)) if a == b => Ok(Plan::Merge),
        _ => Err(SyncError::DifferentVault),
    }
}

fn send<S: Read + Write>(ch: &mut Channel<S>, m: &Message) -> Result<(), SyncError> {
    ch.send(&serde_json::to_vec(m).map_err(|_| SyncError::Protocol)?)
}

fn recv<S: Read + Write>(ch: &mut Channel<S>) -> Result<Message, SyncError> {
    let m: Message = serde_json::from_slice(&ch.recv()?).map_err(|_| SyncError::Protocol)?;
    if let Message::Refuse { reason } = &m {
        return Err(match reason.as_str() {
            "different_vault" => SyncError::DifferentVault,
            "no_vault" => SyncError::NoVault,
            "version" => SyncError::Version,
            other => SyncError::Refused(other.to_string()),
        });
    }
    Ok(m)
}

fn refuse_reason(e: &SyncError) -> &'static str {
    match e {
        SyncError::DifferentVault => "different_vault",
        SyncError::NoVault => "no_vault",
        SyncError::Version => "version",
        _ => "error",
    }
}

/// Runs a whole session. `stream` is the connected socket; `code` the pairing code.
pub fn run<S: Read + Write>(stream: S, role: Role, code: &str, store: &mut dyn SyncStore) -> Result<Outcome, SyncError> {
    let mut ch = Channel::open(stream, role, code)?;

    // Intros: host first.
    let mine = store.intro()?;
    let theirs = match role {
        Role::Host => {
            send(&mut ch, &Message::Intro(mine.clone()))?;
            match recv(&mut ch)? {
                Message::Intro(i) => i,
                _ => return Err(SyncError::Protocol),
            }
        }
        Role::Join => {
            let theirs = match recv(&mut ch)? {
                Message::Intro(i) => i,
                _ => return Err(SyncError::Protocol),
            };
            send(&mut ch, &Message::Intro(mine.clone()))?;
            theirs
        }
    };

    let plan = match decide(&mine, &theirs) {
        Ok(p) => p,
        Err(e) => {
            let _ = send(&mut ch, &Message::Refuse { reason: refuse_reason(&e).into() });
            return Err(e);
        }
    };

    if let Plan::Adopt(adoption) = &plan {
        store.adopt(adoption)?;
    }

    // Snapshots: host first, then joiner (sequential, so large ones can't deadlock).
    let local = match plan {
        Plan::Adopt(_) => Snapshot::default(),
        _ => store.snapshot()?,
    };
    let remote = match role {
        Role::Host => {
            send(&mut ch, &Message::Snapshot(local.clone()))?;
            match recv(&mut ch)? {
                Message::Snapshot(s) => s,
                _ => return Err(SyncError::Protocol),
            }
        }
        Role::Join => {
            let remote = match recv(&mut ch)? {
                Message::Snapshot(s) => s,
                _ => return Err(SyncError::Protocol),
            };
            send(&mut ch, &Message::Snapshot(local.clone()))?;
            remote
        }
    };

    let received = store.apply(&merge(&local, &remote, store.now_ms()))?;

    // Report and hear back how much each side took.
    let sent = match role {
        Role::Host => {
            send(&mut ch, &Message::Done { applied: received })?;
            match recv(&mut ch)? {
                Message::Done { applied } => applied,
                _ => return Err(SyncError::Protocol),
            }
        }
        Role::Join => {
            let applied = match recv(&mut ch)? {
                Message::Done { applied } => applied,
                _ => return Err(SyncError::Protocol),
            };
            send(&mut ch, &Message::Done { applied: received })?;
            applied
        }
    };

    Ok(Outcome {
        peer_name: theirs.device_name,
        peer_platform: theirs.platform,
        adopted: matches!(plan, Plan::Adopt(_)),
        received,
        sent,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::channel::tests::socket_pair;
    use crate::wire::{WireCollection, WireItem};
    use std::collections::HashMap;
    use std::thread;

    /// An in-memory vault.
    #[derive(Clone, Default)]
    struct Mem {
        name: String,
        account: Option<String>,
        meta: Option<VaultMeta>,
        items: HashMap<String, WireItem>,
        collections: HashMap<String, WireCollection>,
    }

    impl SyncStore for Mem {
        fn intro(&self) -> Result<Intro, SyncError> {
            Ok(Intro {
                protocol: PROTOCOL_VERSION,
                device_name: self.name.clone(),
                platform: "test".into(),
                account_id: self.account.clone(),
                vault: self.meta.clone(),
            })
        }
        fn snapshot(&self) -> Result<Snapshot, SyncError> {
            Ok(Snapshot { items: self.items.values().cloned().collect(), collections: self.collections.values().cloned().collect() })
        }
        fn adopt(&mut self, a: &Adoption) -> Result<(), SyncError> {
            self.account = a.account_id.clone();
            self.meta = Some(a.vault.clone());
            Ok(())
        }
        fn apply(&mut self, plan: &MergePlan) -> Result<u32, SyncError> {
            for i in &plan.items {
                self.items.insert(i.id.clone(), i.clone());
            }
            for c in &plan.collections {
                self.collections.insert(c.id.clone(), c.clone());
            }
            Ok((plan.items.len() + plan.collections.len()) as u32)
        }
        fn now_ms(&self) -> i64 {
            1_000
        }
    }

    fn meta(n: u8) -> VaultMeta {
        VaultMeta { salt: vec![n; 32], kdf_version: 1, verifier: vec![n; 32] }
    }

    fn item(id: &str, updated: i64, payload: &[u8], deleted: Option<i64>) -> WireItem {
        WireItem {
            id: id.into(),
            kind: "login".into(),
            envelope: payload.to_vec(),
            payload: payload.to_vec(),
            schema_version: 1,
            created_at: 1,
            updated_at: updated,
            last_used_at: None,
            collection_id: None,
            deleted_at: deleted,
        }
    }

    fn pair(host: Mem, join: Mem, code_h: &str, code_j: &str) -> (Result<Outcome, SyncError>, Mem, Result<Outcome, SyncError>, Mem) {
        let (a, b) = socket_pair();
        let (ch, cj) = (code_h.to_string(), code_j.to_string());
        let h = thread::spawn(move || {
            let mut host = host;
            let r = run(a, Role::Host, &ch, &mut host);
            (r, host)
        });
        let mut join = join;
        let rj = run(b, Role::Join, &cj, &mut join);
        let (rh, host) = h.join().unwrap();
        (rh, host, rj, join)
    }

    #[test]
    fn new_device_adopts_vault_account_and_items() {
        let mut phone = Mem { name: "iPhone".into(), account: Some("acct".into()), meta: Some(meta(1)), ..Default::default() };
        phone.items.insert("a".into(), item("a", 10, b"secret", None));
        let desktop = Mem { name: "Mac".into(), ..Default::default() };
        let (rh, _, rj, desktop) = pair(phone, desktop, "111111", "111111");
        let (rh, rj) = (rh.unwrap(), rj.unwrap());
        assert!(rj.adopted && !rh.adopted);
        assert_eq!((rj.received, rh.sent), (1, 1));
        assert_eq!(desktop.account.as_deref(), Some("acct"));
        assert_eq!(desktop.meta, Some(meta(1)));
        assert_eq!(desktop.items["a"].payload, b"secret");
    }

    #[test]
    fn same_vault_merges_both_ways() {
        let mut a = Mem { name: "A".into(), account: Some("x".into()), meta: Some(meta(1)), ..Default::default() };
        let mut b = a.clone();
        b.name = "B".into();
        a.items.insert("1".into(), item("1", 20, b"a-newer", None));
        b.items.insert("1".into(), item("1", 10, b"b-older", None));
        b.items.insert("2".into(), item("2", 5, b"only-b", None));
        a.items.insert("3".into(), item("3", 30, b"", Some(30)));
        b.items.insert("3".into(), item("3", 1, b"was-here", None));
        let (rh, a, rj, b) = pair(a, b, "222222", "222222");
        rh.unwrap();
        rj.unwrap();
        assert_eq!(a.items["2"].payload, b"only-b");
        assert_eq!(b.items["1"].payload, b"a-newer");
        assert!(b.items["3"].deleted_at.is_some(), "the delete reached B");
        assert_eq!(a.items.len(), b.items.len());
    }

    #[test]
    fn different_vaults_are_refused_and_untouched() {
        let mut a = Mem { name: "A".into(), meta: Some(meta(1)), ..Default::default() };
        a.items.insert("1".into(), item("1", 1, b"a", None));
        let b = Mem { name: "B".into(), meta: Some(meta(2)), ..Default::default() };
        let (rh, a, rj, b) = pair(a, b, "333333", "333333");
        assert_eq!(rh.unwrap_err(), SyncError::DifferentVault);
        assert_eq!(rj.unwrap_err(), SyncError::DifferentVault);
        assert!(b.items.is_empty());
        assert_eq!(a.items.len(), 1);
    }

    #[test]
    fn wrong_code_moves_nothing_and_rebinds_nothing() {
        let a = Mem { name: "A".into(), account: Some("victim".into()), meta: Some(meta(1)), ..Default::default() };
        let b = Mem { name: "attacker".into(), account: Some("attacker".into()), ..Default::default() };
        let (rh, _, rj, b) = pair(a, b, "444444", "000000");
        assert!(rh.is_err() && rj.is_err());
        assert_eq!(b.account.as_deref(), Some("attacker"));
        assert!(b.meta.is_none());
    }
}
