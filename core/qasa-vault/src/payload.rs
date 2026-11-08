//! Vault item payloads. Mirrors iOS `VaultEnums.swift` field for field.
//!
//! Three JSON shapes exist:
//! - **Stored** (what gets encrypted): the iOS synthesized-Codable shape,
//!   `{"login":{"_0":{…}}}`, so a desktop vault is byte-compatible with iOS.
//! - **Android**: `{"type":"login", …}` with `honorific` / `emailType` / `phoneType`
//!   instead of `type`. Read, never written.
//! - **UI** (the desktop's IPC): `{"variant":"login","fields":{…}}`, the serde form of
//!   [`VaultPayload`]. Adjacent tagging avoids the `type` field clash Android hit.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use unicode_segmentation::UnicodeSegmentation;

use crate::country::dial_code;

// MARK: - Enums

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase")]
pub enum ItemKind {
    #[default]
    Login,
    SecureNote,
    Payment,
    PersonalInfo,
    IdDocument,
}

impl ItemKind {
    pub fn as_str(self) -> &'static str {
        match self {
            ItemKind::Login => "login",
            ItemKind::SecureNote => "secureNote",
            ItemKind::Payment => "payment",
            ItemKind::PersonalInfo => "personalInfo",
            ItemKind::IdDocument => "idDocument",
        }
    }

    /// Unknown kinds read as login, as on iOS (`VaultItemKind(rawValue:) ?? .login`).
    pub fn parse(raw: &str) -> Self {
        match raw {
            "secureNote" => ItemKind::SecureNote,
            "payment" => ItemKind::Payment,
            "personalInfo" => ItemKind::PersonalInfo,
            "idDocument" => ItemKind::IdDocument,
            _ => ItemKind::Login,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "lowercase")]
pub enum NoteColor {
    #[default]
    Mint,
    Gold,
    Blue,
    Purple,
    Red,
    Gray,
}

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum Honorific {
    #[serde(rename = "Mr", alias = "mr")]
    Mr,
    #[serde(rename = "Mrs", alias = "mrs")]
    Mrs,
    #[serde(rename = "Ms", alias = "ms")]
    Ms,
    #[serde(rename = "Mx", alias = "mx")]
    Mx,
    #[serde(rename = "Miss", alias = "miss")]
    Miss,
    #[default]
    #[serde(rename = "None of these", alias = "none")]
    None,
}

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum EmailKind {
    #[default]
    #[serde(rename = "Personal", alias = "personal")]
    Personal,
    #[serde(rename = "Work", alias = "work")]
    Work,
}

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Default)]
pub enum PhoneKind {
    #[default]
    #[serde(rename = "Cell", alias = "cell")]
    Cell,
    #[serde(rename = "Work", alias = "work")]
    Work,
    #[serde(rename = "Work Cell", alias = "workCell")]
    WorkCell,
    #[serde(rename = "Work Fax", alias = "workFax")]
    WorkFax,
    #[serde(rename = "Fax", alias = "fax")]
    Fax,
    #[serde(rename = "Home Phone", alias = "homePhone")]
    HomePhone,
    #[serde(rename = "Other", alias = "other")]
    Other,
}

// MARK: - Field structs

fn iq() -> String {
    "IQ".to_string()
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct LoginFields {
    pub item_name: String,
    pub email: String,
    pub username: String,
    pub password: String,
    pub website: String,
    pub note: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub collection_id: Option<String>,
    /// Normalised `otpauth://totp/…` URI. `None` and `""` both mean no code.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub totp: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct SecureNoteFields {
    pub title: String,
    pub content: String,
    pub color: NoteColor,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub collection_id: Option<String>,
    pub protect_item: bool,
}

impl Default for SecureNoteFields {
    fn default() -> Self {
        Self { title: String::new(), content: String::new(), color: NoteColor::Mint, collection_id: None, protect_item: false }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct CardFields {
    pub item_name: String,
    pub account_holder: String,
    pub number: String,
    pub security_code: String,
    pub expiry_date: String,
    pub note: String,
    pub color: NoteColor,
}

impl Default for CardFields {
    fn default() -> Self {
        Self {
            item_name: String::new(),
            account_holder: String::new(),
            number: String::new(),
            security_code: String::new(),
            expiry_date: String::new(),
            note: String::new(),
            color: NoteColor::Blue,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct BankAccountFields {
    pub item_name: String,
    pub account_holder: String,
    pub account_number: String,
    pub routing_number: String,
    pub country_code: String,
    pub bank_name: String,
}

impl Default for BankAccountFields {
    fn default() -> Self {
        Self {
            item_name: String::new(),
            account_holder: String::new(),
            account_number: String::new(),
            routing_number: String::new(),
            country_code: iq(),
            bank_name: String::new(),
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct NameFields {
    #[serde(rename = "type")]
    pub honorific: Honorific,
    pub first_name: String,
    pub last_name: String,
    pub middle_name: String,
    pub default_username: String,
    pub birth_date: String,
    pub birth_place: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct PersonalEmailFields {
    pub item_name: String,
    #[serde(rename = "type")]
    pub kind: EmailKind,
    pub email: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct PhoneFields {
    pub item_name: String,
    #[serde(rename = "type")]
    pub kind: PhoneKind,
    pub country_code: String,
    pub number: String,
}

impl Default for PhoneFields {
    fn default() -> Self {
        Self { item_name: String::new(), kind: PhoneKind::Cell, country_code: iq(), number: String::new() }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct AddressFields {
    pub item_name: String,
    pub address: String,
    pub city: String,
    pub zip_code: String,
    pub country_code: String,
    pub recipient: String,
    pub building: String,
    pub floor: String,
    pub apartment: String,
    pub door_code: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub phone_item_id: Option<String>,
}

impl Default for AddressFields {
    fn default() -> Self {
        Self {
            item_name: String::new(),
            address: String::new(),
            city: String::new(),
            zip_code: String::new(),
            country_code: iq(),
            recipient: String::new(),
            building: String::new(),
            floor: String::new(),
            apartment: String::new(),
            door_code: String::new(),
            phone_item_id: None,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct CompanyFields {
    pub company_name: String,
    pub job_title: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct WebsiteFields {
    pub item_name: String,
    #[serde(rename = "websiteURL")]
    pub website_url: String,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase", default)]
pub struct IdDocumentFields {
    pub name: String,
    pub country_code: String,
    pub number: String,
    pub issue_date: String,
    pub expiry_date: String,
    /// Passport only.
    pub issue_place: String,
}

impl Default for IdDocumentFields {
    fn default() -> Self {
        Self {
            name: String::new(),
            country_code: iq(),
            number: String::new(),
            issue_date: String::new(),
            expiry_date: String::new(),
            issue_place: String::new(),
        }
    }
}

// MARK: - Payload

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(tag = "variant", content = "fields", rename_all = "camelCase")]
pub enum VaultPayload {
    Login(LoginFields),
    SecureNote(SecureNoteFields),
    Card(CardFields),
    BankAccount(BankAccountFields),
    Name(NameFields),
    PersonalEmail(PersonalEmailFields),
    Phone(PhoneFields),
    Address(AddressFields),
    Company(CompanyFields),
    Website(WebsiteFields),
    IdCard(IdDocumentFields),
    Passport(IdDocumentFields),
    DriverLicense(IdDocumentFields),
}

/// The encrypted list metadata. iOS `VaultEnvelope`.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Envelope {
    pub display_name: String,
    pub subtitle: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub collection_id: Option<String>,
}

#[derive(Debug, PartialEq, Eq)]
pub struct PayloadParseError;

impl std::fmt::Display for PayloadParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("unreadable vault item")
    }
}

impl std::error::Error for PayloadParseError {}

const VARIANTS: [&str; 13] = [
    "login",
    "secureNote",
    "card",
    "bankAccount",
    "name",
    "personalEmail",
    "phone",
    "address",
    "company",
    "website",
    "idCard",
    "passport",
    "driverLicense",
];

impl VaultPayload {
    pub fn variant(&self) -> &'static str {
        match self {
            VaultPayload::Login(_) => "login",
            VaultPayload::SecureNote(_) => "secureNote",
            VaultPayload::Card(_) => "card",
            VaultPayload::BankAccount(_) => "bankAccount",
            VaultPayload::Name(_) => "name",
            VaultPayload::PersonalEmail(_) => "personalEmail",
            VaultPayload::Phone(_) => "phone",
            VaultPayload::Address(_) => "address",
            VaultPayload::Company(_) => "company",
            VaultPayload::Website(_) => "website",
            VaultPayload::IdCard(_) => "idCard",
            VaultPayload::Passport(_) => "passport",
            VaultPayload::DriverLicense(_) => "driverLicense",
        }
    }

    fn fields_value(&self) -> Value {
        let value = serde_json::to_value(self).expect("payloads always serialize");
        value.get("fields").cloned().unwrap_or(Value::Object(Map::new()))
    }

    fn from_variant(variant: &str, fields: Value) -> Result<Self, PayloadParseError> {
        let mut tagged = Map::new();
        tagged.insert("variant".into(), Value::String(variant.into()));
        tagged.insert("fields".into(), fields);
        serde_json::from_value(Value::Object(tagged)).map_err(|_| PayloadParseError)
    }

    /// The bytes that get encrypted: iOS's `{"login":{"_0":{…}}}`.
    pub fn to_stored_json(&self) -> Vec<u8> {
        let mut inner = Map::new();
        inner.insert("_0".into(), self.fields_value());
        let mut outer = Map::new();
        outer.insert(self.variant().into(), Value::Object(inner));
        serde_json::to_vec(&Value::Object(outer)).expect("payloads always serialize")
    }

    /// Reads the iOS shape, or Android's `{"type": …}` shape.
    pub fn from_stored_json(bytes: &[u8]) -> Result<Self, PayloadParseError> {
        let value: Value = serde_json::from_slice(bytes).map_err(|_| PayloadParseError)?;
        let Value::Object(map) = value else { return Err(PayloadParseError) };

        // iOS: exactly one key naming the case, holding {"_0": fields}.
        if map.len() == 1 {
            let (variant, inner) = map.iter().next().expect("one entry");
            if VARIANTS.contains(&variant.as_str()) {
                let fields = inner.get("_0").cloned().ok_or(PayloadParseError)?;
                return Self::from_variant(variant, fields);
            }
        }

        // Android: a "type" discriminator beside the fields.
        let mut fields = map;
        let Some(Value::String(variant)) = fields.remove("type") else { return Err(PayloadParseError) };
        for android_key in ["honorific", "emailType", "phoneType"] {
            if let Some(v) = fields.remove(android_key) {
                fields.insert("type".into(), v);
            }
        }
        if !VARIANTS.contains(&variant.as_str()) {
            return Err(PayloadParseError);
        }
        Self::from_variant(&variant, Value::Object(fields))
    }

    pub fn kind(&self) -> ItemKind {
        match self {
            VaultPayload::Login(_) => ItemKind::Login,
            VaultPayload::SecureNote(_) => ItemKind::SecureNote,
            VaultPayload::Card(_) | VaultPayload::BankAccount(_) => ItemKind::Payment,
            VaultPayload::Name(_)
            | VaultPayload::PersonalEmail(_)
            | VaultPayload::Phone(_)
            | VaultPayload::Address(_)
            | VaultPayload::Company(_)
            | VaultPayload::Website(_) => ItemKind::PersonalInfo,
            VaultPayload::IdCard(_) | VaultPayload::Passport(_) | VaultPayload::DriverLicense(_) => ItemKind::IdDocument,
        }
    }

    pub fn display_name(&self) -> String {
        match self {
            VaultPayload::Login(f) => f.item_name.clone(),
            VaultPayload::SecureNote(f) => f.title.clone(),
            VaultPayload::Card(f) => f.item_name.clone(),
            VaultPayload::BankAccount(f) => f.item_name.clone(),
            VaultPayload::Name(f) => join_non_empty(&[&f.first_name, &f.last_name], " "),
            VaultPayload::PersonalEmail(f) => {
                if f.item_name.is_empty() { f.email.clone() } else { f.item_name.clone() }
            }
            VaultPayload::Phone(f) => {
                if f.item_name.is_empty() { f.number.clone() } else { f.item_name.clone() }
            }
            VaultPayload::Address(f) => f.item_name.clone(),
            VaultPayload::Company(f) => f.company_name.clone(),
            VaultPayload::Website(f) => f.item_name.clone(),
            VaultPayload::IdCard(f) | VaultPayload::Passport(f) | VaultPayload::DriverLicense(f) => f.name.clone(),
        }
    }

    /// The list subtitle. Literal English words ("Protected", "Card") are stored as iOS
    /// stores them; the UI localizes them when it shows them.
    pub fn subtitle(&self) -> String {
        match self {
            VaultPayload::Login(f) => if f.email.is_empty() { f.username.clone() } else { f.email.clone() },
            VaultPayload::SecureNote(f) => {
                if f.protect_item {
                    "Protected".into()
                } else {
                    f.content.graphemes(true).take(40).collect()
                }
            }
            VaultPayload::Card(f) => {
                let digits: Vec<char> = f.number.chars().filter(|c| c.is_numeric()).collect();
                let last4: String = digits[digits.len().saturating_sub(4)..].iter().collect();
                if last4.is_empty() { "Card".into() } else { format!("•••• {last4}") }
            }
            VaultPayload::BankAccount(f) => {
                if f.bank_name.is_empty() { f.account_number.clone() } else { f.bank_name.clone() }
            }
            VaultPayload::Name(f) => f.default_username.clone(),
            VaultPayload::PersonalEmail(f) => f.email.clone(),
            VaultPayload::Phone(f) => format!("{} {}", dial_code(&f.country_code), f.number),
            VaultPayload::Address(f) => join_non_empty(&[&f.city, &f.country_code], ", "),
            VaultPayload::Company(f) => f.job_title.clone(),
            VaultPayload::Website(f) => f.website_url.clone(),
            VaultPayload::IdCard(f) | VaultPayload::Passport(f) | VaultPayload::DriverLicense(f) => f.number.clone(),
        }
    }

    /// Collection, for the payloads that have one (logins and notes).
    pub fn collection_id(&self) -> Option<String> {
        match self {
            VaultPayload::Login(f) => f.collection_id.clone(),
            VaultPayload::SecureNote(f) => f.collection_id.clone(),
            _ => None,
        }
    }

    pub fn set_collection_id(&mut self, id: Option<String>) {
        match self {
            VaultPayload::Login(f) => f.collection_id = id,
            VaultPayload::SecureNote(f) => f.collection_id = id,
            _ => {}
        }
    }

    pub fn envelope(&self) -> Envelope {
        let name = self.display_name();
        let name = name.trim();
        Envelope {
            display_name: if name.is_empty() { "Untitled".into() } else { name.to_string() },
            subtitle: self.subtitle(),
            collection_id: self.collection_id(),
        }
    }

    /// The website a login or website item points at, trimmed.
    pub fn website_url(&self) -> Option<String> {
        let raw = match self {
            VaultPayload::Login(f) => &f.website,
            VaultPayload::Website(f) => &f.website_url,
            _ => return None,
        };
        let trimmed = raw.trim();
        (!trimmed.is_empty()).then(|| trimmed.to_string())
    }

    /// Text fed to the search index besides the envelope. iOS `searchableStrings`.
    pub fn searchable_strings(&self) -> Vec<String> {
        match self {
            VaultPayload::Login(f) => vec![
                f.item_name.clone(),
                f.email.clone(),
                f.username.clone(),
                f.website.clone(),
                f.note.clone(),
                f.totp.as_deref().and_then(crate::totp::issuer_of).unwrap_or_default(),
            ],
            VaultPayload::SecureNote(f) => vec![f.title.clone(), f.content.clone()],
            VaultPayload::Card(f) => vec![f.item_name.clone(), f.account_holder.clone(), f.note.clone()],
            VaultPayload::BankAccount(f) => vec![f.item_name.clone(), f.account_holder.clone(), f.bank_name.clone()],
            VaultPayload::Name(f) => vec![
                f.first_name.clone(),
                f.middle_name.clone(),
                f.last_name.clone(),
                f.default_username.clone(),
            ],
            VaultPayload::PersonalEmail(f) => vec![f.item_name.clone(), f.email.clone()],
            VaultPayload::Phone(f) => vec![f.item_name.clone(), f.number.clone()],
            VaultPayload::Address(f) => vec![f.item_name.clone(), f.address.clone(), f.city.clone(), f.recipient.clone()],
            VaultPayload::Company(f) => vec![f.company_name.clone(), f.job_title.clone()],
            VaultPayload::Website(f) => vec![f.item_name.clone(), f.website_url.clone()],
            VaultPayload::IdCard(f) | VaultPayload::Passport(f) | VaultPayload::DriverLicense(f) => {
                vec![f.name.clone(), f.number.clone()]
            }
        }
    }

    /// Hashed search tokens for this item: envelope name and subtitle plus
    /// [`Self::searchable_strings`], deduplicated. iOS `replaceSearchTokens`.
    pub fn search_token_hashes(&self, envelope: &Envelope, search_key: &[u8; 32]) -> Vec<String> {
        let mut texts = vec![envelope.display_name.clone(), envelope.subtitle.clone()];
        texts.extend(self.searchable_strings());
        let mut hashes = std::collections::BTreeSet::new();
        for text in texts.iter().filter(|t| !t.is_empty()) {
            hashes.extend(crate::token_hashes(text, search_key));
        }
        hashes.into_iter().collect()
    }
}

fn join_non_empty(parts: &[&String], separator: &str) -> String {
    parts.iter().filter(|p| !p.is_empty()).map(|p| p.as_str()).collect::<Vec<_>>().join(separator)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stored_shape_matches_ios() {
        let login = VaultPayload::Login(LoginFields { item_name: "GitHub".into(), password: "p".into(), ..Default::default() });
        let json: Value = serde_json::from_slice(&login.to_stored_json()).unwrap();
        assert_eq!(json["login"]["_0"]["itemName"], "GitHub");
        // nil optionals are omitted, like JSONEncoder's encodeIfPresent.
        assert!(json["login"]["_0"].get("collectionId").is_none());
        assert!(json["login"]["_0"].get("totp").is_none());
        assert_eq!(VaultPayload::from_stored_json(&login.to_stored_json()).unwrap(), login);
    }

    #[test]
    fn reads_an_ios_payload() {
        let ios = br#"{"phone":{"_0":{"itemName":"Me","type":"Work Cell","countryCode":"IQ","number":"750"}}}"#;
        let VaultPayload::Phone(f) = VaultPayload::from_stored_json(ios).unwrap() else { panic!() };
        assert_eq!(f.kind, PhoneKind::WorkCell);
        let site = br#"{"website":{"_0":{"itemName":"x","websiteURL":"https://a.b"}}}"#;
        let VaultPayload::Website(w) = VaultPayload::from_stored_json(site).unwrap() else { panic!() };
        assert_eq!(w.website_url, "https://a.b");
    }

    #[test]
    fn reads_an_android_payload() {
        let android = br#"{"type":"name","honorific":"Mr","firstName":"Ali"}"#;
        let VaultPayload::Name(f) = VaultPayload::from_stored_json(android).unwrap() else { panic!() };
        assert_eq!((f.honorific, f.first_name.as_str()), (Honorific::Mr, "Ali"));
        let old = br#"{"type":"phone","phoneType":"workCell","number":"1"}"#;
        let VaultPayload::Phone(p) = VaultPayload::from_stored_json(old).unwrap() else { panic!() };
        assert_eq!((p.kind, p.country_code.as_str()), (PhoneKind::WorkCell, "IQ"));
    }

    #[test]
    fn ui_shape_is_adjacently_tagged() {
        let p = VaultPayload::Name(NameFields { first_name: "Sara".into(), ..Default::default() });
        let json = serde_json::to_value(&p).unwrap();
        assert_eq!(json["variant"], "name");
        assert_eq!(json["fields"]["type"], "None of these");
    }

    #[test]
    fn envelope_and_subtitles() {
        let card = VaultPayload::Card(CardFields { number: "4111 1111 1111 4921".into(), ..Default::default() });
        assert_eq!(card.envelope(), Envelope { display_name: "Untitled".into(), subtitle: "•••• 4921".into(), collection_id: None });
        let phone = VaultPayload::Phone(PhoneFields { number: "7501234567".into(), ..Default::default() });
        assert_eq!(phone.subtitle(), "+964 7501234567");
        let note = VaultPayload::SecureNote(SecureNoteFields { content: "x".repeat(50), ..Default::default() });
        assert_eq!(note.subtitle().len(), 40);
        let protected = VaultPayload::SecureNote(SecureNoteFields { protect_item: true, ..Default::default() });
        assert_eq!(protected.subtitle(), "Protected");
    }
}
