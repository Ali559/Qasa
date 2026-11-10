//! Inputs for the field-validation golden vectors (`tests/validation_vectors.json`), and
//! the code that computes their expected outputs. iOS (`qasaTests/ValidationVectorTests.swift`)
//! and Android (`vault/ValidationVectorTest.kt`) run the same cases through their own
//! payload types and the FFI.

use qasa_vault::payload::*;
use qasa_vault::validate::{self, CivilDate};
use serde::{Deserialize, Serialize};
use serde_json::Value;

#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct ValidationVectors {
    pub comment: String,
    pub today: CivilDate,
    pub cases: Vec<Case>,
    pub card_brands: Vec<Io>,
    pub format_card_number: Vec<Io>,
    pub format_card_expiry: Vec<Io>,
    /// `output` is the ISO date, or empty when the input is not a date.
    pub parse_date: Vec<Io>,
}

#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct Case {
    pub name: String,
    /// The stored (iOS) payload shape.
    pub payload: Value,
    /// `field:problem` codes ([`validate::FieldIssue::code`]), in order.
    pub issues: Vec<String>,
    /// [`validate::normalize`] of `payload`, stored shape.
    pub normalized: Value,
}

#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct Io {
    pub input: String,
    pub output: String,
}

pub const TODAY: CivilDate = CivilDate { year: 2026, month: 11, day: 10 };

fn stored(p: &VaultPayload) -> Value {
    serde_json::from_slice(&p.to_stored_json()).unwrap()
}

fn cases() -> Vec<(&'static str, VaultPayload)> {
    let login = |f: LoginFields| VaultPayload::Login(LoginFields { item_name: "GitHub".into(), ..f });
    let card = |number: &str, cvv: &str, expiry: &str| {
        VaultPayload::Card(CardFields { item_name: "Card".into(), number: number.into(), security_code: cvv.into(), expiry_date: expiry.into(), ..Default::default() })
    };
    let bank = |account: &str, routing: &str, country: &str| {
        VaultPayload::BankAccount(BankAccountFields {
            item_name: "Bank".into(),
            account_number: account.into(),
            routing_number: routing.into(),
            country_code: country.into(),
            ..Default::default()
        })
    };
    let phone = |country: &str, number: &str| {
        VaultPayload::Phone(PhoneFields { item_name: "Me".into(), country_code: country.into(), number: number.into(), ..Default::default() })
    };
    let doc = |issue: &str, expiry: &str| IdDocumentFields { name: "Ali".into(), number: "A1234567".into(), issue_date: issue.into(), expiry_date: expiry.into(), ..Default::default() };
    vec![
        ("login ok", login(LoginFields { email: "ali@example.com".into(), password: "pw".into(), website: "https://github.com/login".into(), ..Default::default() })),
        ("login empty", VaultPayload::Login(LoginFields::default())),
        ("login bad email and url", login(LoginFields { email: "ali@example".into(), website: "not a url".into(), ..Default::default() })),
        ("login bare domain", login(LoginFields { website: "  github.com  ".into(), ..Default::default() })),
        ("login bad totp", login(LoginFields { totp: Some("!!!".into()), ..Default::default() })),
        ("login password of spaces", login(LoginFields { password: "   ".into(), ..Default::default() })),
        ("note empty title", VaultPayload::SecureNote(SecureNoteFields { content: "x".into(), ..Default::default() })),
        ("note title too long", VaultPayload::SecureNote(SecureNoteFields { title: "x".repeat(501), ..Default::default() })),
        ("card visa ok", card("4111111111111111", "123", "12/28")),
        ("card amex ok", card("3782 822463 10005", "1234", "1/30")),
        ("card arabic digits", card("٤١١١ ١١١١ ١١١١ ١١١١", "١٢٣", "١٢/٢٨")),
        ("card luhn", card("4111 1111 1111 1112", "", "")),
        ("card length", card("4111 1111 1111", "", "")),
        ("card letters", card("4111-abcd", "", "")),
        ("card amex cvv", card("378282246310005", "123", "")),
        ("card unknown cvv", card("", "12", "")),
        ("card expiry month", card("", "", "13/28")),
        ("card expiry too far", card("", "", "12/2099")),
        ("card expiry text", card("", "", "soon")),
        ("card expired is kept", card("", "", "01/20")),
        ("card legacy expiry date", card("", "", "Nov 6, 2027")),
        ("card unionpay without luhn", card("6212345678901232", "", "")),
        ("card no name", VaultPayload::Card(CardFields::default())),
        ("bank iban ok", bank("gb82west12345698765432", "NWBKGB2L", "gb")),
        ("bank iraq iban ok", bank("IQ98 NBIQ 8501 2345 6789 012", "", "IQ")),
        ("bank iban checksum", bank("GB82 WEST 1234 5698 7654 33", "", "GB")),
        ("bank iban length", bank("GB82 WEST 1234 5698 7654", "", "GB")),
        ("bank us aba ok", bank("000123456789", "011000015", "US")),
        ("bank us aba checksum", bank("", "011000016", "US")),
        ("bank sort code", bank("12345678", "20-00-00", "GB")),
        ("bank ifsc", bank("", "SBIN0001234", "IN")),
        ("bank bad bic", bank("", "NWBK!2L", "GB")),
        ("bank account symbols", bank("12#34", "", "IQ")),
        ("bank bad country", bank("", "", "Iraq")),
        ("name last only", VaultPayload::Name(NameFields { last_name: "Barznji".into(), ..Default::default() })),
        ("name empty", VaultPayload::Name(NameFields::default())),
        ("name legacy birth date", VaultPayload::Name(NameFields { first_name: "Ali".into(), birth_date: "Nov 6, 2000".into(), ..Default::default() })),
        ("name future birth date", VaultPayload::Name(NameFields { first_name: "Ali".into(), birth_date: "2030-01-01".into(), ..Default::default() })),
        ("name ancient birth date", VaultPayload::Name(NameFields { first_name: "Ali".into(), birth_date: "1850-01-01".into(), ..Default::default() })),
        ("email ok", VaultPayload::PersonalEmail(PersonalEmailFields { email: " ali@qasa.app ".into(), ..Default::default() })),
        ("email unicode", VaultPayload::PersonalEmail(PersonalEmailFields { email: "ئالی@نموونە.کورد".into(), ..Default::default() })),
        ("email missing", VaultPayload::PersonalEmail(PersonalEmailFields { item_name: "Work".into(), ..Default::default() })),
        ("email no tld", VaultPayload::PersonalEmail(PersonalEmailFields { email: "a@b".into(), ..Default::default() })),
        ("phone national", phone("IQ", "0750 123 4567")),
        ("phone own prefix", phone("IQ", "+964 750 123 4567")),
        ("phone 00 prefix lower country", phone("iq", "00964-750-123-4567")),
        ("phone other country", phone("IQ", "+44 20 7946 0958")),
        ("phone too short", phone("IQ", "12")),
        ("phone too long", phone("US", "2025550123456789")),
        ("phone letters", phone("IQ", "750-CALL")),
        ("phone missing", phone("IQ", "")),
        ("phone no country", phone("", "7501234567")),
        ("address lenient", VaultPayload::Address(AddressFields { item_name: "Home".into(), address: "شەقامی ٦٠ مەتری، نزیک #3".into(), zip_code: "44001".into(), ..Default::default() })),
        ("address postal symbols", VaultPayload::Address(AddressFields { item_name: "Home".into(), zip_code: "44#01".into(), ..Default::default() })),
        ("address no name", VaultPayload::Address(AddressFields::default())),
        ("company missing", VaultPayload::Company(CompanyFields { job_title: "CTO".into(), ..Default::default() })),
        ("website ok", VaultPayload::Website(WebsiteFields { item_name: "Blog".into(), website_url: "qasa.app".into() })),
        ("website empty", VaultPayload::Website(WebsiteFields::default())),
        ("passport ok", VaultPayload::Passport(IdDocumentFields { issue_place: "Erbil".into(), ..doc("2020-01-01", "2030-01-01") })),
        ("passport legacy dates", VaultPayload::Passport(doc("Jan 1, 2020", "01/01/2030"))),
        ("id issued in future", VaultPayload::IdCard(doc("2030-01-01", ""))),
        ("id expiry before issue", VaultPayload::IdCard(doc("2020-01-01", "2019-01-01"))),
        ("id expiry same day", VaultPayload::IdCard(doc("2020-01-01", "2020-01-01"))),
        ("licence expiry too far", VaultPayload::DriverLicense(doc("", "2090-01-01"))),
        ("licence impossible date", VaultPayload::DriverLicense(doc("Feb 30 2020", ""))),
        ("licence number symbols", VaultPayload::DriverLicense(IdDocumentFields { number: "AB/12".into(), ..doc("", "") })),
        ("id no name", VaultPayload::IdCard(IdDocumentFields::default())),
    ]
}

fn io(inputs: &[&str], f: impl Fn(&str) -> String) -> Vec<Io> {
    inputs.iter().map(|i| Io { input: (*i).into(), output: f(i) }).collect()
}

pub fn build() -> ValidationVectors {
    let cases = cases()
        .into_iter()
        .map(|(name, p)| Case {
            name: name.into(),
            payload: stored(&p),
            issues: validate::validate(&p, TODAY).iter().map(|i| i.code()).collect(),
            normalized: stored(&validate::normalize(&p)),
        })
        .collect();
    ValidationVectors {
        comment: "Golden vectors for field validation. Generated by core/qasa-vault/examples/gen_validation_vectors.rs; checked by core (cargo test), iOS (qasaTests/ValidationVectorTests.swift) and Android (vault/ValidationVectorTest.kt). Do not edit by hand.".into(),
        today: TODAY,
        cases,
        card_brands: io(
            &["4111111111111111", "5555 5555 5555 4444", "2223000048400011", "378282246310005", "6011111111111117", "3530111333300000", "30569309025904", "6212345678901232", "6762", "2200123456789010", "9999", ""],
            |n| serde_json::to_value(validate::CardBrand::detect(n)).unwrap().as_str().unwrap().to_string(),
        ),
        format_card_number: io(&["4111111111111111", "4111-1111", "٤١١١١١١١", "378282246310005", "30569309025904", "41111111111111111119999"], validate::format_card_number),
        format_card_expiry: io(&["", "1", "4", "12", "123", "1228", "12/28", "122899", "٠٤٢٨"], validate::format_card_expiry),
        parse_date: io(
            &["2000-11-06", "Nov 6, 2000", "Nov 6 2000", "6 November 2000", "06/11/2000", "11/26/2000", "٢٠٠٠-١١-٠٦", "2001-02-29", "2024-02-29", "yesterday", ""],
            |s| validate::parse_date(s).map(|d| d.iso()).unwrap_or_default(),
        ),
    }
}
