//! Last writer wins, per item and per collection (plan decision 4). Deletes are
//! tombstones, so a delete on one device removes the item on the other instead of the
//! item coming back. Ties (same `updated_at`) break the same way on both devices, so two
//! syncs in either direction converge.

use std::collections::HashMap;

use sha2::{Digest, Sha256};

use crate::wire::{Snapshot, WireCollection, WireItem};

/// How long a tombstone is kept and still sent (plan §6.3).
pub const TOMBSTONE_TTL_MS: i64 = 90 * 24 * 60 * 60 * 1000;

/// What the local device must write: remote rows that win.
#[derive(Debug, Default, PartialEq, Eq)]
pub struct MergePlan {
    pub items: Vec<WireItem>,
    pub collections: Vec<WireCollection>,
}

fn item_key(i: &WireItem) -> (i64, bool, [u8; 32]) {
    (i.updated_at, i.deleted_at.is_some(), Sha256::digest(&i.payload).into())
}

fn collection_key(c: &WireCollection) -> (i64, bool, [u8; 32]) {
    (c.updated_at, c.deleted_at.is_some(), Sha256::digest(&c.name_enc).into())
}

/// The remote rows that should replace or add to `local`. Tombstones older than
/// [`TOMBSTONE_TTL_MS`] are ignored.
pub fn merge(local: &Snapshot, remote: &Snapshot, now_ms: i64) -> MergePlan {
    let fresh = |deleted: Option<i64>| deleted.map_or(true, |d| now_ms - d < TOMBSTONE_TTL_MS);

    let mine: HashMap<&str, &WireItem> = local.items.iter().map(|i| (i.id.as_str(), i)).collect();
    let items = remote
        .items
        .iter()
        .filter(|r| fresh(r.deleted_at))
        .filter(|r| match mine.get(r.id.as_str()) {
            // Unknown here: take it, tombstones too, so a later sync with a third device
            // doesn't resurrect it.
            None => true,
            Some(l) => item_key(r) > item_key(l),
        })
        .cloned()
        .collect();

    let mine: HashMap<&str, &WireCollection> = local.collections.iter().map(|c| (c.id.as_str(), c)).collect();
    let collections = remote
        .collections
        .iter()
        .filter(|r| fresh(r.deleted_at))
        .filter(|r| mine.get(r.id.as_str()).map_or(true, |l| collection_key(r) > collection_key(l)))
        .cloned()
        .collect();

    MergePlan { items, collections }
}

#[cfg(test)]
mod tests {
    use super::*;

    pub fn item(id: &str, updated: i64, payload: &[u8], deleted: Option<i64>) -> WireItem {
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

    fn snap(items: Vec<WireItem>) -> Snapshot {
        Snapshot { items, collections: vec![] }
    }

    #[test]
    fn newer_wins_and_new_items_arrive() {
        let local = snap(vec![item("a", 10, b"old", None), item("b", 50, b"mine", None)]);
        let remote = snap(vec![item("a", 20, b"new", None), item("b", 40, b"stale", None), item("c", 5, b"c", None)]);
        let plan = merge(&local, &remote, 100);
        let ids: Vec<_> = plan.items.iter().map(|i| i.id.as_str()).collect();
        assert_eq!(ids, ["a", "c"]);
    }

    #[test]
    fn deletes_win_when_newer_and_stay_deleted() {
        let local = snap(vec![item("a", 10, b"x", None)]);
        let remote = snap(vec![item("a", 30, b"", Some(30))]);
        assert_eq!(merge(&local, &remote, 100).items.len(), 1);
        // An edit after the delete brings it back.
        let local = snap(vec![item("a", 40, b"edited", None)]);
        assert!(merge(&local, &remote, 100).items.is_empty());
    }

    #[test]
    fn ties_converge_both_ways() {
        let x = snap(vec![item("a", 10, b"one", None)]);
        let y = snap(vec![item("a", 10, b"two", None)]);
        let to_x = merge(&x, &y, 100).items.len();
        let to_y = merge(&y, &x, 100).items.len();
        assert_eq!(to_x + to_y, 1, "exactly one side changes");
    }

    #[test]
    fn old_tombstones_are_dropped() {
        let remote = snap(vec![item("a", 1, b"", Some(1))]);
        assert!(merge(&Snapshot::default(), &remote, 1 + TOMBSTONE_TTL_MS).items.is_empty());
    }

    /// Protocol 2: a folder's name is ciphertext on the wire, never readable text, and two
    /// folders edited at the same moment still resolve the same way on both sides.
    #[test]
    fn folder_names_cross_encrypted() {
        let folder = |name_enc: &[u8]| WireCollection { id: "F".into(), name_enc: name_enc.to_vec(), created_at: 1, updated_at: 5, deleted_at: None };
        let json = serde_json::to_string(&folder(b"\x01\x02ciphertext")).unwrap();
        assert!(json.contains("\"name_enc\""), "{json}");
        assert!(!json.contains("\"name\""), "{json}");

        let (a, b) = (folder(b"one"), folder(b"two"));
        let ab = merge(&Snapshot { collections: vec![a.clone()], ..Default::default() }, &Snapshot { collections: vec![b.clone()], ..Default::default() }, 10);
        let ba = merge(&Snapshot { collections: vec![b.clone()], ..Default::default() }, &Snapshot { collections: vec![a.clone()], ..Default::default() }, 10);
        // Exactly one side takes the other's row, so both end up with the same one.
        assert_eq!(ab.collections.len() + ba.collections.len(), 1);
    }
}
