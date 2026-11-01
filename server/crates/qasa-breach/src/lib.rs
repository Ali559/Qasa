use thiserror::Error;

pub mod ingest;
pub mod prefix;

pub use ingest::{parse_hash_line, split_hash};
pub use prefix::{normalize_prefix, PrefixError};

/// A candidate hash suffix returned for k-anonymity lookup.
#[derive(Debug, Clone, serde::Serialize)]
pub struct BreachCandidate {
    pub suffix: String,
    pub source: String,
}

#[derive(Debug, Error)]
pub enum BreachError {
    #[error("invalid prefix: {0}")]
    InvalidPrefix(#[from] PrefixError),
}
