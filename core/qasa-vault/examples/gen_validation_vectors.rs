//! Writes the field-validation golden vectors to stdout:
//!
//!     cargo run -p qasa-vault --example gen_validation_vectors > qasa-vault/tests/validation_vectors.json
//!
//! `tests/validation_vectors.rs` fails if the file and this generator disagree; the iOS and
//! Android test suites run the same cases through their own payload types and the FFI.

#[path = "../tests/validation/mod.rs"]
mod validation;

fn main() {
    println!("{}", serde_json::to_string_pretty(&validation::build()).unwrap());
}
