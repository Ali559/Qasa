use uuid::Uuid;

/// Generate a new time-ordered UUID v7 primary key.
pub fn new_id() -> Uuid {
    Uuid::now_v7()
}

/// Build a masked public ID for admin display, e.g. `qs_8f92a1c3...3a1c`.
pub fn new_public_id() -> String {
    mask_public_id(Uuid::now_v7())
}

pub fn mask_public_id(id: Uuid) -> String {
    let hex = id.simple().to_string();
    format!("qs_{}...{}", &hex[..8], &hex[hex.len() - 4..])
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn public_id_has_expected_format() {
        let id = Uuid::now_v7();
        let masked = mask_public_id(id);
        assert!(masked.starts_with("qs_"));
        assert!(masked.contains("..."));
    }
}
