/// Split a full SHA-256 hex hash (64 chars) into (prefix, suffix).
pub fn split_hash(hash: &str) -> Option<(String, String)> {
    let normalized = hash.trim().to_lowercase();
    if normalized.len() != 64 || !normalized.chars().all(|c| c.is_ascii_hexdigit()) {
        return None;
    }
    let (prefix, suffix) = normalized.split_at(5);
    Some((prefix.to_string(), suffix.to_string()))
}

/// Parse one line from an ingest file. Returns (prefix, suffix, source).
pub fn parse_hash_line(line: &str, default_source: &str) -> Option<(String, String, String)> {
    let line = line.trim();
    if line.is_empty() || line.starts_with('#') {
        return None;
    }

    // Support "hash" or "hash,source" or "hash source"
    let (hash_part, source) = if let Some((hash, src)) = line.split_once(',') {
        (hash.trim(), src.trim())
    } else if let Some((hash, src)) = line.split_once('\t') {
        (hash.trim(), src.trim())
    } else {
        (line, default_source)
    };

    let (prefix, suffix) = split_hash(hash_part)?;
    let source = if source.is_empty() {
        default_source.to_string()
    } else {
        source.to_string()
    };
    Some((prefix, suffix, source))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_full_hash() {
        let hash = "5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8";
        let (prefix, suffix) = split_hash(hash).unwrap();
        assert_eq!(prefix, "5e884");
        assert_eq!(suffix.len(), 59);
    }

    #[test]
    fn parses_line_with_source() {
        let (prefix, suffix, source) =
            parse_hash_line("5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8,hibp-2024", "local")
                .unwrap();
        assert_eq!(prefix, "5e884");
        assert_eq!(suffix.len(), 59);
        assert_eq!(source, "hibp-2024");
    }
}
