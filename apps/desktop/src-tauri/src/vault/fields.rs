//! Field rules for the editor and detail view: thin commands over
//! `qasa_vault::validate`, so the UI has no second copy of the rules. Pure functions of
//! their input (no vault access, no keys), so they work while the editor is open without
//! touching the vault lock. New commands must also be added to `isolation/index.js`.

use qasa_vault::payload::VaultPayload;
use qasa_vault::validate::{self, CardBrand, CivilDate, FieldIssue};
use serde::Serialize;

/// Every issue with an item as the editor holds it, after normalizing (what a save
/// would check). Empty when the item can be saved.
#[tauri::command]
pub fn validate_item(payload: VaultPayload) -> Vec<FieldIssue> {
    validate::validate(&validate::normalize(&payload), super::today())
}

/// The stored canonical form (ISO dates, `MM/YY` expiry, grouped card number…). The
/// editor applies it when an item opens.
#[tauri::command]
pub fn normalize_item(payload: VaultPayload) -> VaultPayload {
    validate::normalize(&payload)
}

/// Card number as the user types: digits grouped by brand.
#[tauri::command]
pub fn format_card_number(raw: String) -> String {
    validate::format_card_number(&raw)
}

/// Card expiry as the user types: `MM/YY`.
#[tauri::command]
pub fn format_card_expiry(raw: String) -> String {
    validate::format_card_expiry(&raw)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CardBrandInfo {
    pub brand: CardBrand,
    /// `[min, max]` security code digits.
    pub security_code_digits: [u32; 2],
}

#[tauri::command]
pub fn card_brand(number: String) -> CardBrandInfo {
    let brand = CardBrand::detect(&number);
    let (min, max) = brand.security_code_digits();
    CardBrandInfo { brand, security_code_digits: [min, max] }
}

/// A stored or typed date (ISO, or a legacy form) as a calendar day, for showing it
/// localized. `None` when Qasa can't read it.
#[tauri::command]
pub fn parse_vault_date(raw: String) -> Option<CivilDate> {
    validate::parse_date(&raw)
}

/// Several dates at once (the detail view), so one item costs one IPC call.
#[tauri::command]
pub fn parse_vault_dates(raws: Vec<String>) -> Vec<Option<CivilDate>> {
    raws.iter().map(|r| validate::parse_date(r)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn issues_serialize_as_the_contract_says() {
        let payload: VaultPayload = serde_json::from_str(
            r#"{"variant":"card","fields":{"itemName":"c","accountHolder":"","number":"4111 1111 1111 1112","securityCode":"","expiryDate":"","note":"","color":"blue"}}"#,
        )
        .unwrap();
        let json = serde_json::to_value(validate_item(payload)).unwrap();
        assert_eq!(json[0], serde_json::json!({"field": "number", "problem": {"kind": "cardNumberChecksum"}}));
        assert_eq!(serde_json::to_value(card_brand("3782".into())).unwrap(), serde_json::json!({"brand": "amex", "securityCodeDigits": [4, 4]}));
        assert_eq!(serde_json::to_value(parse_vault_date("Nov 6, 2000".into())).unwrap(), serde_json::json!({"year": 2000, "month": 11, "day": 6}));
        assert_eq!(format_card_expiry("829".into()), "08/29");
    }
}
