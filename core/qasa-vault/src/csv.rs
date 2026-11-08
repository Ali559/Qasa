//! Plaintext CSV backup of the vault. Mirrors iOS `Services/Vault/VaultCSV.swift`.
//!
//! A wide set of human-readable columns for the common fields, plus a `fields` column
//! holding compact JSON of the concrete fields struct, written exactly as iOS
//! `JSONEncoder` with `.sortedKeys` writes it. Import prefers `fields` (decoded as
//! strictly as Swift's synthesized `Decodable`: every non-optional key must be present
//! with the right type), and falls back to the wide columns for logins, notes, cards and
//! bank accounts only.

use serde_json::{Map, Value};
use unicode_segmentation::UnicodeSegmentation;

use crate::payload::{BankAccountFields, CardFields, LoginFields, SecureNoteFields, VaultPayload};
use crate::totp::{trim_whitespaces, Totp};

/// The 16 export columns, in order. Mirrors `VaultCSV.columns`.
pub const COLUMNS: [&str; 16] = [
    "type",
    "title",
    "username",
    "password",
    "url",
    "email",
    "account_holder",
    "number",
    "security_code",
    "expiry",
    "account_number",
    "routing_number",
    "bank_name",
    "content",
    "notes",
    "fields",
];

/// Why a file could not be imported at all. Mirrors `VaultCSVError`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum CsvError {
    /// No non-blank record (`settings.importNothing`).
    Empty,
    /// The header lacks `type` or `title` (`settings.importBadHeader`); `missing` is
    /// always `'type', 'title'`, as on iOS.
    InvalidHeader { missing: String },
}

impl std::fmt::Display for CsvError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CsvError::Empty => f.write_str("nothing to import"),
            CsvError::InvalidHeader { missing } => write!(f, "CSV header is missing {missing}"),
        }
    }
}

impl std::error::Error for CsvError {}

// MARK: - Export

/// The CSV text for `payloads`: header, one row each, `\n`-separated, no trailing
/// newline. Mirrors `VaultCSV.exportCSV(payloads:)`.
pub fn export_csv(payloads: &[VaultPayload]) -> String {
    let mut lines = vec![COLUMNS.join(",")];
    lines.extend(payloads.iter().map(row));
    lines.join("\n")
}

fn row(payload: &VaultPayload) -> String {
    let mut cells: std::collections::HashMap<&str, String> = std::collections::HashMap::new();
    cells.insert("type", type_name(payload).to_string());
    cells.insert("title", payload.display_name());

    let join = |parts: &[&String]| {
        parts.iter().filter(|p| !p.is_empty()).map(|p| p.as_str()).collect::<Vec<_>>().join(" ")
    };
    match payload {
        VaultPayload::Login(f) => {
            cells.insert("username", f.username.clone());
            cells.insert("password", f.password.clone());
            cells.insert("url", f.website.clone());
            cells.insert("email", f.email.clone());
            cells.insert("notes", f.note.clone());
        }
        VaultPayload::SecureNote(f) => {
            cells.insert("content", f.content.clone());
            cells.insert("notes", f.protect_item.to_string());
        }
        VaultPayload::Card(f) => {
            cells.insert("account_holder", f.account_holder.clone());
            cells.insert("number", f.number.clone());
            cells.insert("security_code", f.security_code.clone());
            cells.insert("expiry", f.expiry_date.clone());
            cells.insert("notes", f.note.clone());
        }
        VaultPayload::BankAccount(f) => {
            cells.insert("account_holder", f.account_holder.clone());
            cells.insert("account_number", f.account_number.clone());
            cells.insert("routing_number", f.routing_number.clone());
            cells.insert("bank_name", f.bank_name.clone());
        }
        VaultPayload::Name(f) => {
            cells.insert("email", f.default_username.clone());
            cells.insert("expiry", f.birth_date.clone());
            cells.insert("content", f.birth_place.clone());
        }
        VaultPayload::PersonalEmail(f) => {
            cells.insert("email", f.email.clone());
        }
        VaultPayload::Phone(f) => {
            cells.insert("number", f.number.clone());
        }
        VaultPayload::Address(f) => {
            cells.insert("content", join(&[&f.address, &f.city, &f.zip_code]));
            cells.insert("notes", join(&[&f.building, &f.floor, &f.apartment, &f.door_code]));
            cells.insert("account_holder", f.recipient.clone());
        }
        VaultPayload::Company(f) => {
            cells.insert("account_holder", f.job_title.clone());
        }
        VaultPayload::Website(f) => {
            cells.insert("url", f.website_url.clone());
        }
        VaultPayload::IdCard(f) | VaultPayload::Passport(f) | VaultPayload::DriverLicense(f) => {
            cells.insert("account_holder", f.name.clone());
            cells.insert("number", f.number.clone());
            cells.insert("expiry", f.expiry_date.clone());
            cells.insert("content", f.issue_date.clone());
            cells.insert("notes", f.issue_place.clone());
        }
    }
    cells.insert("fields", fields_json(payload));
    COLUMNS.iter().map(|c| csv_escape(cells.get(c).map_or("", String::as_str))).collect::<Vec<_>>().join(",")
}

/// The CSV `type` cell (`login`, `secureNote`, …). Mirrors `VaultCSV.typeName(for:)`.
pub fn type_name(payload: &VaultPayload) -> &'static str {
    payload.variant()
}

/// Compact JSON of the fields struct, byte-identical to iOS `JSONEncoder` with
/// `.sortedKeys`: sorted keys, `/` escaped as `\/`, non-ASCII raw, nil optionals
/// omitted, UUIDs uppercase, a login's empty `totp` left out. Mirrors
/// `VaultCSV.fieldsJSON(for:)`.
pub fn fields_json(payload: &VaultPayload) -> String {
    let value = serde_json::to_value(payload).expect("payloads always serialize");
    let mut fields = match value.get("fields") {
        Some(Value::Object(map)) => map.clone(),
        _ => Map::new(),
    };
    if matches!(payload, VaultPayload::Login(_))
        && fields.get("totp").and_then(Value::as_str).is_some_and(str::is_empty)
    {
        fields.remove("totp");
    }
    // Swift holds these as `UUID?`: written as the uppercase `uuidString`, and a value
    // that is not a UUID could never exist there, so it is left out.
    for key in ["collectionId", "phoneItemId"] {
        if let Some(raw) = fields.get(key).and_then(Value::as_str) {
            match normalize_uuid(raw) {
                Some(uuid) => {
                    fields.insert(key.into(), Value::String(uuid));
                }
                None => {
                    fields.remove(key);
                }
            }
        }
    }
    write_apple_json(&fields)
}

/// Stable identity used to skip rows already in the vault on a repeated import.
/// Mirrors `VaultCSV.fingerprint(_:)`.
pub fn fingerprint(payload: &VaultPayload) -> String {
    format!("{}|{}", type_name(payload), fields_json(payload))
}

fn write_apple_json(fields: &Map<String, Value>) -> String {
    let mut keys: Vec<&String> = fields.keys().collect();
    keys.sort();
    let mut out = String::from("{");
    let mut first = true;
    for key in keys {
        let value = &fields[key];
        if value.is_null() {
            continue;
        }
        if !first {
            out.push(',');
        }
        first = false;
        push_json_string(&mut out, key);
        out.push(':');
        match value {
            Value::String(s) => push_json_string(&mut out, s),
            Value::Bool(b) => out.push_str(if *b { "true" } else { "false" }),
            other => out.push_str(&other.to_string()),
        }
    }
    out.push('}');
    out
}

fn push_json_string(out: &mut String, s: &str) {
    out.push('"');
    for ch in s.chars() {
        match ch {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '/' => out.push_str("\\/"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            '\u{08}' => out.push_str("\\b"),
            '\u{0C}' => out.push_str("\\f"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
}

/// Swift `UUID(uuidString:)` then `.uuidString`: 8-4-4-4-12 hex, uppercased.
fn normalize_uuid(raw: &str) -> Option<String> {
    let bytes = raw.as_bytes();
    if bytes.len() != 36 {
        return None;
    }
    for (i, b) in bytes.iter().enumerate() {
        let ok = if matches!(i, 8 | 13 | 18 | 23) { *b == b'-' } else { b.is_ascii_hexdigit() };
        if !ok {
            return None;
        }
    }
    Some(raw.to_ascii_uppercase())
}

// MARK: - Import

/// What a CSV import adds once rows already in the vault are skipped. Mirrors the
/// counting in iOS `SettingsView.performImport` (`added` / `skipped`).
#[derive(Clone, Debug, PartialEq, Eq, Default)]
pub struct ImportOutcome {
    /// New payloads to save, in file order.
    pub imported: Vec<VaultPayload>,
    /// Rows whose [`fingerprint`] matches an item already in the vault.
    pub skipped: usize,
}

/// Parses `csv` and drops rows whose fingerprint matches one of `existing`. Rows are
/// not deduplicated against each other, as on iOS. An empty `imported` with
/// `skipped == 0` is iOS's "nothing to import". Mirrors `VaultCSV.parseImport` plus the
/// dedupe in `SettingsView.performImport`.
pub fn import_csv(csv: &str, existing: &[VaultPayload]) -> Result<ImportOutcome, CsvError> {
    let candidates = parse_import(csv)?;
    let existing_prints: std::collections::HashSet<String> = existing.iter().map(fingerprint).collect();
    let mut outcome = ImportOutcome::default();
    for payload in candidates {
        if existing_prints.contains(&fingerprint(&payload)) {
            outcome.skipped += 1;
        } else {
            outcome.imported.push(payload);
        }
    }
    Ok(outcome)
}

/// Every importable row of `csv`, in order. Rows without a title, with an unknown type
/// and no readable `fields`, or of a kind the wide columns can't rebuild are skipped.
/// Mirrors `VaultCSV.parseImport(_:)`.
pub fn parse_import(csv: &str) -> Result<Vec<VaultPayload>, CsvError> {
    let records: Vec<String> = parse_records(csv)
        .into_iter()
        .map(|r| r.trim_matches('\r').to_string())
        .filter(|r| !trim_whitespaces(r).is_empty())
        .collect();
    let header = records.first().ok_or(CsvError::Empty)?;
    let header_cells: Vec<String> = parse_line(header).iter().map(|c| trim_whitespaces(c).to_lowercase()).collect();
    if !header_cells.iter().any(|c| c == "type") || !header_cells.iter().any(|c| c == "title") {
        return Err(CsvError::InvalidHeader { missing: "'type', 'title'".into() });
    }
    let index = |name: &str| header_cells.iter().position(|c| c == name);
    let title_idx = index("title");
    let fields_idx = index("fields");

    let mut out = Vec::new();
    for record in &records[1..] {
        let cells = parse_line(record);
        let cell = |i: Option<usize>| -> String { i.and_then(|i| cells.get(i)).cloned().unwrap_or_default() };
        let value = |name: &str| cell(index(name));

        let title = cell(title_idx);
        if title.is_empty() {
            continue;
        }
        let fields_raw = cell(fields_idx);
        let kind = value("type");

        if !fields_raw.is_empty() {
            if let Some(payload) = decode_payload(&kind, &fields_raw) {
                out.push(payload);
                continue;
            }
        }

        match kind.as_str() {
            "login" => {
                let totp_raw = if value("totp").is_empty() { value("login_totp") } else { value("totp") };
                out.push(VaultPayload::Login(LoginFields {
                    item_name: title,
                    email: value("email"),
                    username: value("username"),
                    password: value("password"),
                    website: value("url"),
                    note: value("notes"),
                    collection_id: None,
                    totp: Totp::parse(&totp_raw).ok().map(|t| t.uri),
                }));
            }
            "secureNote" => out.push(VaultPayload::SecureNote(SecureNoteFields {
                title,
                content: value("content"),
                ..Default::default()
            })),
            "card" => out.push(VaultPayload::Card(CardFields {
                item_name: title,
                account_holder: value("account_holder"),
                number: value("number"),
                security_code: value("security_code"),
                expiry_date: value("expiry"),
                note: value("notes"),
                ..Default::default()
            })),
            "bankAccount" => out.push(VaultPayload::BankAccount(BankAccountFields {
                item_name: title,
                account_holder: value("account_holder"),
                account_number: value("account_number"),
                routing_number: value("routing_number"),
                bank_name: value("bank_name"),
                ..Default::default()
            })),
            _ => {}
        }
    }
    Ok(out)
}

#[derive(Clone, Copy)]
enum Kind {
    Str,
    Bool,
    OptStr,
    OptUuid,
    Raw(&'static [&'static str]),
}

const NOTE_COLORS: &[&str] = &["mint", "gold", "blue", "purple", "red", "gray"];
const HONORIFICS: &[&str] = &["Mr", "Mrs", "Ms", "Mx", "Miss", "None of these"];
const EMAIL_KINDS: &[&str] = &["Personal", "Work"];
const PHONE_KINDS: &[&str] = &["Cell", "Work", "Work Cell", "Work Fax", "Fax", "Home Phone", "Other"];

fn schema(kind: &str) -> Option<&'static [(&'static str, Kind)]> {
    use Kind::*;
    const ID_DOC: &[(&str, Kind)] = &[
        ("name", Str),
        ("countryCode", Str),
        ("number", Str),
        ("issueDate", Str),
        ("expiryDate", Str),
        ("issuePlace", Str),
    ];
    Some(match kind {
        "login" => &[
            ("itemName", Str),
            ("email", Str),
            ("username", Str),
            ("password", Str),
            ("website", Str),
            ("note", Str),
            ("collectionId", OptUuid),
            ("totp", OptStr),
        ],
        "secureNote" => &[
            ("title", Str),
            ("content", Str),
            ("color", Raw(NOTE_COLORS)),
            ("collectionId", OptUuid),
            ("protectItem", Bool),
        ],
        "card" => &[
            ("itemName", Str),
            ("accountHolder", Str),
            ("number", Str),
            ("securityCode", Str),
            ("expiryDate", Str),
            ("note", Str),
            ("color", Raw(NOTE_COLORS)),
        ],
        "bankAccount" => &[
            ("itemName", Str),
            ("accountHolder", Str),
            ("accountNumber", Str),
            ("routingNumber", Str),
            ("countryCode", Str),
            ("bankName", Str),
        ],
        "name" => &[
            ("type", Raw(HONORIFICS)),
            ("firstName", Str),
            ("lastName", Str),
            ("middleName", Str),
            ("defaultUsername", Str),
            ("birthDate", Str),
            ("birthPlace", Str),
        ],
        "personalEmail" => &[("itemName", Str), ("type", Raw(EMAIL_KINDS)), ("email", Str)],
        "phone" => &[("itemName", Str), ("type", Raw(PHONE_KINDS)), ("countryCode", Str), ("number", Str)],
        "address" => &[
            ("itemName", Str),
            ("address", Str),
            ("city", Str),
            ("zipCode", Str),
            ("countryCode", Str),
            ("recipient", Str),
            ("building", Str),
            ("floor", Str),
            ("apartment", Str),
            ("doorCode", Str),
            ("phoneItemId", OptUuid),
        ],
        "company" => &[("companyName", Str), ("jobTitle", Str)],
        "website" => &[("itemName", Str), ("websiteURL", Str)],
        "idCard" | "passport" | "driverLicense" => ID_DOC,
        _ => return None,
    })
}

/// Swift's synthesized `Decodable` for the fields struct named by `kind`: every
/// non-optional key present with the right JSON type, enums by exact raw value, UUIDs
/// well-formed; unknown keys ignored. Mirrors `VaultCSV.decodePayload(type:json:)`.
fn decode_payload(kind: &str, json: &str) -> Option<VaultPayload> {
    let schema = schema(kind)?;
    let Ok(Value::Object(object)) = serde_json::from_str::<Value>(json) else { return None };
    let mut fields = Map::new();
    for (key, ty) in schema {
        let value = object.get(*key);
        let decoded = match (*ty, value) {
            (Kind::Str, Some(Value::String(s))) => Value::String(s.clone()),
            (Kind::Bool, Some(Value::Bool(b))) => Value::Bool(*b),
            (Kind::Raw(allowed), Some(Value::String(s))) if allowed.contains(&s.as_str()) => Value::String(s.clone()),
            (Kind::OptStr | Kind::OptUuid, None | Some(Value::Null)) => continue,
            (Kind::OptStr, Some(Value::String(s))) => Value::String(s.clone()),
            (Kind::OptUuid, Some(Value::String(s))) => Value::String(normalize_uuid(s)?),
            _ => return None,
        };
        fields.insert((*key).to_string(), decoded);
    }
    let mut tagged = Map::new();
    tagged.insert("variant".into(), Value::String(kind.into()));
    tagged.insert("fields".into(), Value::Object(fields));
    let mut payload: VaultPayload = serde_json::from_value(Value::Object(tagged)).ok()?;
    if let VaultPayload::Login(f) = &mut payload {
        if f.totp.as_deref().is_some_and(str::is_empty) {
            f.totp = None;
        }
    }
    Some(payload)
}

// MARK: - RFC 4180 primitives

/// Quotes a cell only when it contains a comma, quote, LF or CR, doubling quotes.
/// Mirrors `VaultCSV.csvEscape(_:)`.
pub fn csv_escape(value: &str) -> String {
    if value.contains([',', '"', '\n', '\r']) {
        format!("\"{}\"", value.replace('"', "\"\""))
    } else {
        value.to_string()
    }
}

/// Swift `Character.isNewline`, judged on the cluster's first scalar.
fn is_newline(grapheme: &str) -> bool {
    matches!(grapheme.chars().next(), Some('\n' | '\u{0B}' | '\u{0C}' | '\r' | '\u{85}' | '\u{2028}' | '\u{2029}'))
}

/// Splits CSV text into records on newlines that are not inside a quoted field, keeping
/// the quotes for [`parse_line`]. Iterates grapheme clusters like Swift, so `\r\n` is one
/// newline. Mirrors `VaultCSV.parseRecords(_:)`.
pub fn parse_records(csv: &str) -> Vec<String> {
    let mut records = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut iter = csv.graphemes(true);
    let mut pending: Option<&str> = None;
    while let Some(ch) = pending.take().or_else(|| iter.next()) {
        if ch == "\"" {
            current.push('"');
            if in_quotes {
                match iter.next() {
                    Some("\"") => current.push('"'),
                    Some(next) => {
                        in_quotes = false;
                        pending = Some(next);
                    }
                    None => in_quotes = false,
                }
            } else {
                in_quotes = true;
            }
        } else if is_newline(ch) && !in_quotes {
            records.push(std::mem::take(&mut current));
        } else {
            current.push_str(ch);
        }
    }
    records.push(current);
    records
}

/// Splits one record into cells: quoted commas and newlines kept, `""` read as `"`.
/// Mirrors `VaultCSV.parseLine(_:)`.
pub fn parse_line(line: &str) -> Vec<String> {
    let mut result = Vec::new();
    let mut current = String::new();
    let mut in_quotes = false;
    let mut iter = line.graphemes(true);
    let mut pending: Option<&str> = None;
    while let Some(ch) = pending.take().or_else(|| iter.next()) {
        if in_quotes {
            if ch == "\"" {
                match iter.next() {
                    Some("\"") => current.push('"'),
                    Some(next) => {
                        in_quotes = false;
                        pending = Some(next);
                    }
                    None => in_quotes = false,
                }
            } else {
                current.push_str(ch);
            }
        } else {
            match ch {
                "\"" => in_quotes = true,
                "," => result.push(std::mem::take(&mut current)),
                _ => current.push_str(ch),
            }
        }
    }
    result.push(current);
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::payload::*;

    const COLLECTION: &str = "3F2B8C1E-9A4D-4E7B-8C21-5D6F7A8B9C0D";
    const TOTP_URI: &str = "otpauth://totp/GitHub:ali?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=GitHub";

    fn s(v: &str) -> String {
        v.to_string()
    }

    fn header() -> String {
        COLUMNS.join(",")
    }

    fn id_doc(name: &str, cc: &str, number: &str, issue: &str, expiry: &str, place: &str) -> IdDocumentFields {
        IdDocumentFields {
            name: s(name),
            country_code: s(cc),
            number: s(number),
            issue_date: s(issue),
            expiry_date: s(expiry),
            issue_place: s(place),
        }
    }

    /// Every field set to a non-default value so a dropped field would show up.
    fn all13() -> Vec<VaultPayload> {
        vec![
            VaultPayload::Login(LoginFields {
                item_name: s("GitHub"),
                email: s("a@b.c"),
                username: s("ali"),
                password: s("p,\"w\nx"),
                website: s("https://github.com"),
                note: s("n"),
                collection_id: Some(s(COLLECTION)),
                totp: None,
            }),
            VaultPayload::SecureNote(SecureNoteFields {
                title: s("Diary"),
                content: s("line 1\n\nline \"3\", end"),
                color: NoteColor::Purple,
                collection_id: Some(s(COLLECTION)),
                protect_item: true,
            }),
            VaultPayload::Card(CardFields {
                item_name: s("Visa"),
                account_holder: s("Ali B"),
                number: s("4111111111111111"),
                security_code: s("123"),
                expiry_date: s("12/29"),
                note: s("note"),
                color: NoteColor::Gold,
            }),
            VaultPayload::BankAccount(BankAccountFields {
                item_name: s("Main"),
                account_holder: s("Ali B"),
                account_number: s("123"),
                routing_number: s("456"),
                country_code: s("AE"),
                bank_name: s("Rafidain"),
            }),
            VaultPayload::Name(NameFields {
                honorific: Honorific::Mrs,
                first_name: s("Ali"),
                last_name: s("Barznji"),
                middle_name: s("M"),
                default_username: s("ali"),
                birth_date: s("01/02/1990"),
                birth_place: s("Erbil"),
            }),
            VaultPayload::PersonalEmail(PersonalEmailFields { item_name: s("Work mail"), kind: EmailKind::Work, email: s("w@x.y") }),
            VaultPayload::Phone(PhoneFields {
                item_name: s("Office"),
                kind: PhoneKind::WorkCell,
                country_code: s("TR"),
                number: s("0770123"),
            }),
            VaultPayload::Address(AddressFields {
                item_name: s("Home"),
                address: s("100m St"),
                city: s("Erbil"),
                zip_code: s("44001"),
                country_code: s("IQ"),
                recipient: s("Ali"),
                building: s("B2"),
                floor: s("3"),
                apartment: s("12"),
                door_code: s("4321"),
                phone_item_id: Some(s("A1B2C3D4-0000-4000-8000-000000000001")),
            }),
            VaultPayload::Company(CompanyFields { company_name: s("Qasa"), job_title: s("CTO") }),
            VaultPayload::Website(WebsiteFields { item_name: s("Blog"), website_url: s("https://ali.dev/a?b=c") }),
            VaultPayload::IdCard(id_doc("Ali", "IQ", "ID1", "01/01/2020", "01/01/2030", "Erbil")),
            VaultPayload::Passport(id_doc("Ali", "DE", "P1", "02/02/2021", "02/02/2031", "Berlin")),
            VaultPayload::DriverLicense(id_doc("Ali", "IQ", "D1", "03/03/2022", "03/03/2032", "")),
        ]
    }

    // Rows in exactly the shape iOS `VaultCSV.row(for:)` emits.
    const IOS_LOGIN: &str = r#"login,GitHub,ali,"p,""w",https://github.com,a@b.c,,,,,,,,,,"{""email"":""a@b.c"",""itemName"":""GitHub"",""note"":"""",""password"":""p,\""w"",""username"":""ali"",""website"":""https:\/\/github.com""}""#;
    const IOS_CARD: &str = r#"card,Visa,,,,,Ali B,4111111111111111,123,12/29,,,,,,"{""accountHolder"":""Ali B"",""color"":""blue"",""expiryDate"":""12\/29"",""itemName"":""Visa"",""note"":"""",""number"":""4111111111111111"",""securityCode"":""123""}""#;
    const IOS_PHONE: &str = r#"phone,Office,,,,,,0770123,,,,,,,,"{""countryCode"":""IQ"",""itemName"":""Office"",""number"":""0770123"",""type"":""Work Cell""}""#;
    const IOS_NAME: &str = r#"name,Ali Barznji,,,,ali,,,,01/02/1990,,,,Erbil,,"{""birthDate"":""01\/02\/1990"",""birthPlace"":""Erbil"",""defaultUsername"":""ali"",""firstName"":""Ali"",""lastName"":""Barznji"",""middleName"":"""",""type"":""Mr""}""#;

    fn ios_items() -> Vec<VaultPayload> {
        vec![
            VaultPayload::Login(LoginFields {
                item_name: s("GitHub"),
                email: s("a@b.c"),
                username: s("ali"),
                password: s("p,\"w"),
                website: s("https://github.com"),
                ..Default::default()
            }),
            VaultPayload::Card(CardFields {
                item_name: s("Visa"),
                account_holder: s("Ali B"),
                number: s("4111111111111111"),
                security_code: s("123"),
                expiry_date: s("12/29"),
                ..Default::default()
            }),
            VaultPayload::Phone(PhoneFields {
                item_name: s("Office"),
                kind: PhoneKind::WorkCell,
                country_code: s("IQ"),
                number: s("0770123"),
            }),
            VaultPayload::Name(NameFields {
                honorific: Honorific::Mr,
                first_name: s("Ali"),
                last_name: s("Barznji"),
                default_username: s("ali"),
                birth_date: s("01/02/1990"),
                birth_place: s("Erbil"),
                ..Default::default()
            }),
        ]
    }

    fn login(f: &VaultPayload) -> &LoginFields {
        match f {
            VaultPayload::Login(f) => f,
            other => panic!("not a login: {other:?}"),
        }
    }

    #[test]
    fn round_trips_all_13_types() {
        let csv = export_csv(&all13());
        assert_eq!(parse_import(&csv).unwrap(), all13());
        assert!(csv.starts_with(&format!("{}\n", header())));
        assert!(!csv.ends_with('\n'));
    }

    #[test]
    fn round_trip_survives_crlf_line_endings() {
        let csv = export_csv(&all13()).replace('\n', "\r\n");
        let back = parse_import(&csv).unwrap();
        assert_eq!(all13().iter().map(type_name).collect::<Vec<_>>(), back.iter().map(type_name).collect::<Vec<_>>());
        assert_eq!(
            all13().iter().map(VaultPayload::display_name).collect::<Vec<_>>(),
            back.iter().map(VaultPayload::display_name).collect::<Vec<_>>()
        );
    }

    #[test]
    fn fingerprint_is_stable_across_export_import() {
        let back = parse_import(&export_csv(&all13())).unwrap();
        assert_eq!(all13().iter().map(fingerprint).collect::<Vec<_>>(), back.iter().map(fingerprint).collect::<Vec<_>>());
        assert_ne!(fingerprint(&all13()[10]), fingerprint(&all13()[11]));
    }

    #[test]
    fn parses_ios_written_rows() {
        let csv = [header().as_str(), IOS_LOGIN, IOS_CARD, IOS_PHONE, IOS_NAME].join("\n");
        assert_eq!(parse_import(&csv).unwrap(), ios_items());
    }

    #[test]
    fn export_is_byte_identical_to_ios() {
        let expected = [header().as_str(), IOS_LOGIN, IOS_CARD, IOS_PHONE, IOS_NAME].join("\n");
        assert_eq!(export_csv(&ios_items()), expected);
    }

    #[test]
    fn collection_id_is_uppercase_and_strictly_decoded() {
        let mut lower = all13()[0].clone();
        lower.set_collection_id(Some(COLLECTION.to_lowercase()));
        let json = fields_json(&lower);
        assert!(json.contains(&format!("\"collectionId\":\"{COLLECTION}\"")), "{json}");
        assert!(!fields_json(&VaultPayload::Login(LoginFields { item_name: s("x"), ..Default::default() })).contains("collectionId"));
        // A non-UUID id could never exist on iOS; it is left out.
        lower.set_collection_id(Some(s("not-a-uuid")));
        assert!(!fields_json(&lower).contains("collectionId"));

        // iOS's synthesized decoder needs every key: this partial JSON fails and the row
        // falls back to the wide columns (Android deliberately fills defaults instead).
        let partial = format!("{}\nlogin,X,,,,,,,,,,,,,,\"{{\"\"collectionId\"\":\"\"{COLLECTION}\"\",\"\"itemName\"\":\"\"X\"\"}}\"", header());
        let back = parse_import(&partial).unwrap();
        assert_eq!(back, vec![VaultPayload::Login(LoginFields { item_name: s("X"), ..Default::default() })]);
    }

    #[test]
    fn strict_decode_rejects_android_picker_raws() {
        // Swift's raw-value enums only take the iOS spelling.
        let json = r#"{"countryCode":"IQ","itemName":"Office","number":"0770123","type":"workCell"}"#;
        assert!(decode_payload("phone", json).is_none());
        assert!(decode_payload("phone", &fields_json(&ios_items()[2])).is_some());
        assert!(decode_payload("nope", "{}").is_none());
        assert!(decode_payload("company", r#"{"companyName":"Q","jobTitle":1}"#).is_none());
        assert!(decode_payload("company", r#"[]"#).is_none());
    }

    #[test]
    fn totp_survives_export_import_through_fields() {
        let l = VaultPayload::Login(LoginFields {
            item_name: s("GitHub"),
            username: s("ali"),
            password: s("pw"),
            totp: Some(s(TOTP_URI)),
            ..Default::default()
        });
        let json = fields_json(&l);
        assert!(json.contains(r#""totp":"otpauth:\/\/totp\/GitHub:ali?secret="#), "{json}");
        assert_eq!(parse_import(&export_csv(std::slice::from_ref(&l))).unwrap(), vec![l]);
    }

    #[test]
    fn totp_key_is_omitted_when_empty_and_optional_on_import() {
        let none = VaultPayload::Login(LoginFields { item_name: s("GitHub"), password: s("p"), ..Default::default() });
        let empty = VaultPayload::Login(LoginFields { item_name: s("GitHub"), password: s("p"), totp: Some(s("")), ..Default::default() });
        assert!(!export_csv(std::slice::from_ref(&empty)).contains("totp"));
        assert_eq!(export_csv(&[empty]), export_csv(&[none]));
        let back = parse_import(&format!("{}\n{IOS_LOGIN}", header())).unwrap();
        assert_eq!(login(&back[0]).totp, None);
        assert_eq!(back[0], ios_items()[0]);
    }

    #[test]
    fn imports_android_fields_json() {
        let uri = "otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP&issuer=GitHub";
        let json = format!(r#"{{"itemName":"GitHub","email":"","username":"dev","password":"p","website":"","note":"","totp":"{uri}"}}"#);
        let csv = format!("type,title,fields\nlogin,GitHub,{}\n", csv_escape(&json));
        assert_eq!(login(&parse_import(&csv).unwrap()[0]).totp.as_deref(), Some(uri));

        let empty_json = r#"{"itemName":"GitHub","password":"p","totp":""}"#;
        let csv = format!("type,title,fields\nlogin,GitHub,{}\n", csv_escape(empty_json));
        assert_eq!(login(&parse_import(&csv).unwrap()[0]).totp, None);
    }

    #[test]
    fn flat_totp_columns_from_other_managers() {
        let csv = format!(
            "type,title,password,totp,login_totp\nlogin,A,pw,gezd gnbv gy3t qojq gezd gnbv gy3t qojq,\nlogin,B,pw,,{TOTP_URI}\nlogin,C,pw,not a key,"
        );
        let totps: Vec<Option<String>> = parse_import(&csv).unwrap().iter().map(|p| login(p).totp.clone()).collect();
        assert_eq!(
            totps,
            vec![Some(s("otpauth://totp/Qasa?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")), Some(s(TOTP_URI)), None]
        );
        let ios = "type,title,password,login_totp\nlogin,GitHub,p,jbsw y3dp ehpk 3pxp\n";
        assert_eq!(login(&parse_import(ios).unwrap()[0]).totp.as_deref(), Some("otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP"));
    }

    #[test]
    fn wide_column_fallback_without_fields_column() {
        let csv = "Type, Title ,username,password,url,notes,content,number\n\
                   login,Mail,ali,pw,https://m.com,hi,,\n\
                   secureNote,Memo,,,,,body,\n\
                   card,Visa,,,,,,4111\n\
                   phone,Office,,,,,,0770";
        assert_eq!(
            parse_import(csv).unwrap(),
            vec![
                VaultPayload::Login(LoginFields {
                    item_name: s("Mail"),
                    username: s("ali"),
                    password: s("pw"),
                    website: s("https://m.com"),
                    note: s("hi"),
                    ..Default::default()
                }),
                VaultPayload::SecureNote(SecureNoteFields { title: s("Memo"), content: s("body"), ..Default::default() }),
                VaultPayload::Card(CardFields { item_name: s("Visa"), number: s("4111"), ..Default::default() }),
            ]
        );
    }

    #[test]
    fn malformed_fields_json_falls_back_to_wide_columns() {
        let csv = format!("{}\nlogin,Mail,ali,pw,,,,,,,,,,,,{{not json", header());
        assert_eq!(
            parse_import(&csv).unwrap(),
            vec![VaultPayload::Login(LoginFields { item_name: s("Mail"), username: s("ali"), password: s("pw"), ..Default::default() })]
        );
    }

    #[test]
    fn rows_without_title_are_skipped() {
        assert_eq!(parse_import(&format!("{}\nlogin,,ali,pw\n\n   \n", header())).unwrap(), vec![]);
    }

    #[test]
    fn empty_file_and_bad_header() {
        assert_eq!(parse_import(" \n\r\n"), Err(CsvError::Empty));
        assert_eq!(parse_import("name,username\nx,y"), Err(CsvError::InvalidHeader { missing: s("'type', 'title'") }));
    }

    #[test]
    fn import_skips_rows_already_in_the_vault() {
        let csv = export_csv(&all13());
        let existing = &all13()[..3];
        let outcome = import_csv(&csv, existing).unwrap();
        assert_eq!(outcome.skipped, 3);
        assert_eq!(outcome.imported, all13()[3..].to_vec());
        // Duplicate rows within one file are both kept, as on iOS.
        let twice = export_csv(&[all13()[8].clone(), all13()[8].clone()]);
        assert_eq!(import_csv(&twice, &[]).unwrap().imported.len(), 2);
    }

    #[test]
    fn escape_quotes_only_when_needed() {
        assert_eq!(csv_escape("plain"), "plain");
        assert_eq!(csv_escape("a,b"), "\"a,b\"");
        assert_eq!(csv_escape("say \"hi\""), "\"say \"\"hi\"\"\"");
        assert_eq!(csv_escape("l1\nl2"), "\"l1\nl2\"");
        assert_eq!(csv_escape("cr\r"), "\"cr\r\"");
        assert_eq!(csv_escape(""), "");
    }

    #[test]
    fn parse_line_handles_quoted_commas_quotes_and_newlines() {
        assert_eq!(parse_line("a,\"b,c\",\"d\"\"e\",,\"f\ng\""), vec!["a", "b,c", "d\"e", "", "f\ng"]);
        assert_eq!(parse_line(","), vec!["", ""]);
        assert_eq!(parse_line("\"\"\"\""), vec!["\""]);
    }

    #[test]
    fn parse_records_keeps_quoted_newlines_together() {
        let records = parse_records("h1,h2\n\"a\n\nb\",\"x\"\"\ny\"\nlast");
        assert_eq!(records, vec!["h1,h2", "\"a\n\nb\",\"x\"\"\ny\"", "last"]);
        assert_eq!(parse_line(&records[1]), vec!["a\n\nb", "x\"\ny"]);
        // Swift sees "\r\n" as one character, so one record break.
        assert_eq!(parse_records("a\r\nb"), vec!["a", "b"]);
    }

    #[test]
    fn json_writer_matches_apple_escaping() {
        let mut map = Map::new();
        map.insert(s("b"), Value::Bool(true));
        map.insert(s("a"), Value::String(s("x/y\"z\\\n\t\u{1}é")));
        map.insert(s("c"), Value::Null);
        assert_eq!(write_apple_json(&map), "{\"a\":\"x\\/y\\\"z\\\\\\n\\t\\u0001é\",\"b\":true}");
    }
}
