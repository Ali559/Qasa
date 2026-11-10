use std::collections::HashMap;

use qasa_sync::{Intro, PROTOCOL_VERSION};

use super::*;

const PASSWORD: &str = "correct horse battery";

/// An in-memory vault, as the platforms implement `SyncStore`.
#[derive(Default)]
struct Mem {
    account_id: Option<String>,
    vault: Option<VaultMeta>,
    items: HashMap<String, WireItem>,
    collections: HashMap<String, WireCollection>,
    now: i64,
}

impl SyncStore for Mem {
    fn intro(&self) -> Result<Intro, SyncError> {
        Ok(Intro {
            protocol: PROTOCOL_VERSION,
            device_name: "Ali's iPhone".into(),
            platform: "ios".into(),
            account_id: self.account_id.clone(),
            vault: self.vault.clone(),
        })
    }
    fn snapshot(&self) -> Result<Snapshot, SyncError> {
        Ok(Snapshot { items: self.items.values().cloned().collect(), collections: self.collections.values().cloned().collect() })
    }
    fn adopt(&mut self, a: &Adoption) -> Result<(), SyncError> {
        if self.vault.is_some() {
            return Err(SyncError::Store("has a vault".into()));
        }
        self.account_id = a.account_id.clone();
        self.vault = Some(a.vault.clone());
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
        self.now
    }
}

fn item(id: &str, updated: i64, payload: &[u8]) -> WireItem {
    WireItem {
        id: id.into(),
        kind: "login".into(),
        envelope: payload.to_vec(),
        payload: payload.to_vec(),
        schema_version: 1,
        created_at: 1,
        updated_at: updated,
        last_used_at: None,
        collection_id: Some("F".into()),
        deleted_at: None,
    }
}

/// A phone with a real vault (keys from `PASSWORD`) and two items in one folder.
fn phone(salt: [u8; 32]) -> (Mem, [u8; 32]) {
    let root = qasa_vault::derive_root_key(PASSWORD, &salt).unwrap();
    let enc = *root.subkeys().enc();
    let mut m = Mem {
        account_id: Some("acct-1".into()),
        vault: Some(VaultMeta { salt: salt.to_vec(), kdf_version: 1, verifier: root.verifier().to_vec() }),
        now: 1_000,
        ..Default::default()
    };
    m.items.insert("A".into(), item("A", 10, b"secret-a"));
    m.items.insert("B".into(), item("B", 10, b"secret-b"));
    m.collections.insert("F".into(), WireCollection { id: "F".into(), name_enc: b"name".to_vec(), created_at: 1, updated_at: 1, deleted_at: None });
    (m, enc)
}

#[test]
fn a_new_device_takes_the_vault_and_account() {
    let (src, enc) = phone([7; 32]);
    let file = create(&src, &enc, None).unwrap();

    let mut fresh = Mem { now: 2_000, ..Default::default() };
    let r = restore(&file, PASSWORD, &mut fresh).unwrap();
    assert!(r.adopted);
    assert_eq!(r.items_in_backup, 2);
    assert_eq!(r.written, 3, "two items and one folder");
    assert_eq!(r.info.device_name, "Ali's iPhone");
    assert_eq!(r.info.created_at, 1_000);
    assert_eq!(fresh.account_id.as_deref(), Some("acct-1"));
    assert_eq!(fresh.vault, src.vault);
    assert_eq!(fresh.items["A"], src.items["A"], "rows arrive exactly as stored");
}

#[test]
fn the_file_shows_only_its_header() {
    let (src, enc) = phone([7; 32]);
    let file = create(&src, &enc, None).unwrap();
    let text = String::from_utf8_lossy(&file);
    assert!(text.starts_with("QASA-BACKUP\n"));
    for secret in ["acct-1", "secret-a", "c2VjcmV0LWE", "verifier", "items"] {
        assert!(!text.contains(secret), "{secret} leaked");
    }
    let i = info(&file).unwrap();
    assert_eq!((i.format, i.platform.as_str(), i.salt.as_slice()), (1, "ios", &[7u8; 32][..]));
}

#[test]
fn a_wrong_password_or_a_changed_byte_is_refused() {
    let (src, enc) = phone([7; 32]);
    let file = create(&src, &enc, None).unwrap();
    let mut fresh = Mem::default();
    assert_eq!(restore(&file, "nope", &mut fresh), Err(BackupError::WrongPassword));
    assert!(fresh.vault.is_none(), "nothing written");

    // The header is bound to the body: changing the device name breaks the seal.
    let mut tampered = file.clone();
    let at = tampered.windows(5).position(|w| w == b"iPhon").unwrap();
    tampered[at] = b'X';
    assert_eq!(restore(&tampered, PASSWORD, &mut fresh), Err(BackupError::WrongPassword));

    let last = file.len() - 1;
    let mut flipped = file.clone();
    flipped[last] ^= 1;
    assert_eq!(restore(&flipped, PASSWORD, &mut fresh), Err(BackupError::WrongPassword));
}

#[test]
fn the_same_vault_merges_and_keeps_newer_rows() {
    let (src, enc) = phone([7; 32]);
    let file = create(&src, &enc, None).unwrap();

    let (mut device, _) = phone([7; 32]);
    device.items.insert("A".into(), item("A", 50, b"edited-later"));
    device.items.remove("B");
    let r = restore(&file, PASSWORD, &mut device).unwrap();
    assert!(!r.adopted);
    assert_eq!(r.written, 1, "only B comes back");
    assert_eq!(device.items["A"].payload, b"edited-later");
    assert_eq!(device.account_id.as_deref(), Some("acct-1"));
}

#[test]
fn a_different_vault_is_refused() {
    let (src, enc) = phone([7; 32]);
    let file = create(&src, &enc, None).unwrap();
    let (mut other, _) = phone([9; 32]);
    let before = other.items.clone();
    assert_eq!(restore(&file, PASSWORD, &mut other), Err(BackupError::DifferentVault));
    assert_eq!(other.items, before);
}

#[test]
fn no_vault_no_backup() {
    assert_eq!(create(&Mem::default(), &[0; 32], None), Err(BackupError::NoVault));
}

#[test]
fn junk_and_newer_files_are_named_as_such() {
    assert_eq!(info(b"hello"), Err(BackupError::NotABackup));
    assert_eq!(info(b"QASA-BACKUP\n\x00\x00\x00\x02{}"), Err(BackupError::NotABackup));

    let header = br#"{"format":2,"created_at":1,"device_name":"x","platform":"ios","kdf_version":1,"salt":""}"#;
    let mut newer = MAGIC.to_vec();
    newer.extend_from_slice(&(header.len() as u32).to_be_bytes());
    newer.extend_from_slice(header);
    newer.extend_from_slice(&[0u8; 40]);
    assert_eq!(info(&newer), Err(BackupError::NewerVersion));
}

#[test]
fn ids_are_compared_in_one_case() {
    // Android stores lowercase ids; the backup and the merge use uppercase.
    let (mut src, enc) = phone([7; 32]);
    src.items.insert("c".into(), item("c", 10, b"lower"));
    let file = create(&src, &enc, None).unwrap();
    let (mut device, _) = phone([7; 32]);
    device.items.insert("C".into(), item("C", 99, b"newer"));
    restore(&file, PASSWORD, &mut device).unwrap();
    assert_eq!(device.items["C"].payload, b"newer");
}

#[test]
fn schedule() {
    let day = DAY_MS;
    assert!(!is_due(Frequency::Off, None, 0));
    assert!(is_due(Frequency::Daily, None, 0));
    assert!(!is_due(Frequency::Daily, Some(0), day - 1));
    assert!(is_due(Frequency::Daily, Some(0), day));
    assert!(!is_due(Frequency::Weekly, Some(0), 6 * day));
    assert!(is_due(Frequency::Weekly, Some(0), 7 * day));
    assert!(!is_due(Frequency::Monthly, Some(0), 29 * day));
    assert!(is_due(Frequency::Monthly, Some(0), 30 * day));
    assert!(is_due(Frequency::Weekly, Some(10 * day), day), "clock went backwards");
    assert_eq!(Frequency::parse("weekly"), Frequency::Weekly);
    assert_eq!(Frequency::parse("hourly"), Frequency::Off);
}

#[test]
fn reminder() {
    let month = REMINDER_AFTER_MS;
    assert!(!should_remind(None, 0, None, 0), "empty vault");
    assert!(should_remind(None, 1, None, 0));
    assert!(!should_remind(Some(0), 1, None, month - 1));
    assert!(should_remind(Some(0), 1, None, month));
    assert!(!should_remind(None, 1, Some(100), 50), "snoozed");
    assert!(should_remind(None, 1, Some(100), 100));
}

#[test]
fn file_names() {
    assert_eq!(file_name("Ali's iPhone"), "Qasa backup - Ali's iPhone.qasabackup");
    assert_eq!(file_name("  a/b\\c:d  "), "Qasa backup - a b c d.qasabackup");
    assert_eq!(file_name("..."), "Qasa backup - device.qasabackup");
    assert_eq!(file_name("ئایفۆنی عەلی"), "Qasa backup - ئایفۆنی عەلی.qasabackup");
    assert_eq!(file_name(&"x".repeat(80)).len(), "Qasa backup - .qasabackup".len() + 40);
}

#[test]
fn the_restore_key_travels_inside_the_seal() {
    let (src, enc) = phone([7; 32]);
    let key = new_restore_key();
    let file = create(&src, &enc, Some(&key)).unwrap();
    let text = String::from_utf8_lossy(&file);
    assert!(!text.contains("restore_key"));

    let mut fresh = Mem::default();
    let r = restore(&file, PASSWORD, &mut fresh).unwrap();
    assert_eq!(r.restore_key.as_deref(), Some(&key[..]));
    assert_eq!(restore_key_hash(&key).len(), 32);
    assert_ne!(new_restore_key(), key);

    // A backup made without one restores with none.
    let plain = create(&src, &enc, None).unwrap();
    assert_eq!(restore(&plain, PASSWORD, &mut Mem::default()).unwrap().restore_key, None);
}

#[test]
fn restore_key_hash_is_plain_sha256() {
    // The server hashes the key it receives with SHA-256; both sides must agree.
    assert_eq!(
        restore_key_hash(b"abc").iter().map(|b| format!("{b:02x}")).collect::<String>(),
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    );
}
