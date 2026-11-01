use thiserror::Error;

const PREFIX_LEN: usize = 5;

#[derive(Debug, Error)]
pub enum PrefixError {
    #[error("prefix must be exactly {PREFIX_LEN} hex characters")]
    InvalidLength,
    #[error("prefix must contain only hex characters (a-f, 0-9)")]
    InvalidCharacters,
}

/// Validate and normalize a breach hash prefix to lowercase.
pub fn normalize_prefix(input: &str) -> Result<String, PrefixError> {
    let trimmed = input.trim().to_lowercase();
    if trimmed.len() != PREFIX_LEN {
        return Err(PrefixError::InvalidLength);
    }
    if !trimmed.chars().all(|c| c.is_ascii_hexdigit()) {
        return Err(PrefixError::InvalidCharacters);
    }
    Ok(trimmed)
}

pub fn prefix_length() -> usize {
    PREFIX_LEN
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_valid_prefix() {
        assert_eq!(normalize_prefix("5E884").unwrap(), "5e884");
    }

    #[test]
    fn rejects_short_prefix() {
        assert!(normalize_prefix("5e88").is_err());
    }

    #[test]
    fn rejects_non_hex() {
        assert!(normalize_prefix("5e88g").is_err());
    }
}
