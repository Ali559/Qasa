//! Writes the cross-platform golden vectors to stdout:
//!
//!     cargo run -p qasa-vault --example gen_vectors > qasa-vault/tests/vectors.json
//!
//! `tests/vectors.rs` fails if the file and this generator disagree, and the iOS and
//! Android test suites check the same file, so all three implementations agree.

#[path = "../tests/common/mod.rs"]
mod common;

fn main() {
    println!("{}", serde_json::to_string_pretty(&common::build()).unwrap());
}
