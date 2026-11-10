//! The committed field-validation vectors must match what this crate computes.

mod validation;

use qasa_vault::payload::VaultPayload;
use qasa_vault::validate;

const VECTORS: &str = include_str!("validation_vectors.json");

#[test]
fn committed_vectors_match_the_implementation() {
    let committed: validation::ValidationVectors = serde_json::from_str(VECTORS).expect("validation_vectors.json parses");
    assert_eq!(
        committed,
        validation::build(),
        "tests/validation_vectors.json is stale: cargo run -p qasa-vault --example gen_validation_vectors > qasa-vault/tests/validation_vectors.json"
    );
}

#[test]
fn normalized_payloads_are_stable_and_keep_their_issues_or_fewer() {
    let v: validation::ValidationVectors = serde_json::from_str(VECTORS).unwrap();
    for case in &v.cases {
        let normalized = VaultPayload::from_stored_json(&serde_json::to_vec(&case.normalized).unwrap()).unwrap();
        assert_eq!(validate::normalize(&normalized), normalized, "{}: normalize is idempotent", case.name);
        let after: Vec<String> = validate::validate(&normalized, v.today).iter().map(|i| i.code()).collect();
        assert_eq!(after, case.issues, "{}: normalizing doesn't change the verdict", case.name);
    }
}
