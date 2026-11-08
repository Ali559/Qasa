//! The committed golden vectors must match what this crate computes.

mod common;

const VECTORS: &str = include_str!("vectors.json");

#[test]
fn committed_vectors_match_the_implementation() {
    let committed: common::Vectors = serde_json::from_str(VECTORS).expect("vectors.json parses");
    let fresh = common::build();
    assert_eq!(
        committed, fresh,
        "tests/vectors.json is stale: cargo run -p qasa-vault --example gen_vectors > qasa-vault/tests/vectors.json"
    );
}

#[test]
fn every_aead_vector_decrypts() {
    let v: common::Vectors = serde_json::from_str(VECTORS).unwrap();
    for case in &v.aead {
        let key: [u8; 32] = hex::decode(&case.key_hex).unwrap().try_into().unwrap();
        let blob = hex::decode(&case.blob_hex).unwrap();
        let plain = qasa_vault::decrypt(&blob, &key).unwrap();
        assert_eq!(String::from_utf8(plain).unwrap(), case.plaintext, "{}", case.name);
    }
}
