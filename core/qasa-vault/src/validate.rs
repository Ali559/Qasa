//! Field rules for every vault item, shared by the three apps (ARCHITECTURE.md §5).
//!
//! [`validate`] returns typed per-field issues (never strings: each app turns a
//! [`Problem`] into its own localized message). [`normalize`] rewrites what the user typed
//! into the stored canonical form: dates `YYYY-MM-DD`, card expiry `MM/YY`, card numbers
//! grouped, ASCII digits, upper-case country codes. The apps' vault repositories run
//! `normalize` then `validate` on every user save and refuse the write while any issue
//! remains, so an add/edit form can never store an invalid item.
//!
//! The rules are deliberately lenient where the real world is messy (names, notes,
//! addresses, account holders: only a length cap) and strict where a standard exists
//! (card numbers: brand length + Luhn; IBAN: mod-97; email and web-address shape; phone
//! numbers: E.164 length with the item's country code; dates: real calendar dates in a
//! sensible order). `tests/validation_vectors.json` pins the behaviour for iOS and Android.

use serde::{Deserialize, Serialize};

use crate::country::dial_code;
use crate::payload::{
    AddressFields, BankAccountFields, CardFields, IdDocumentFields, LoginFields, NameFields, PhoneFields, VaultPayload,
};
use crate::totp::Totp;

// MARK: - Limits

/// Single-line fields: names, usernames, cities, bank names…
pub const SHORT_MAX: u32 = 500;
/// Street address lines.
pub const ADDRESS_MAX: u32 = 1_000;
/// Passwords (generated ones top out far below this).
pub const PASSWORD_MAX: u32 = 1_000;
/// The note on a login or card.
pub const NOTE_MAX: u32 = 10_000;
/// Secure note body (room for pasted keys and certificates).
pub const CONTENT_MAX: u32 = 100_000;
/// Postal codes: the longest real ones are 10 characters ("12345-6789").
pub const POSTAL_CODE_MAX: u32 = 12;
/// Passport, national ID and licence numbers.
pub const DOCUMENT_NUMBER_MAX: u32 = 30;
/// Bank account numbers (an IBAN has at most 34 characters, plus grouping spaces).
pub const ACCOUNT_NUMBER_MAX: u32 = 42;
/// A card expiry further out than this is a typo (cards run 3–5 years).
pub const CARD_EXPIRY_MAX_YEARS: u32 = 20;
/// An ID document expiry further out than this is a typo.
pub const DOCUMENT_EXPIRY_MAX_YEARS: u32 = 50;
/// Dates before this year are typos.
pub const EARLIEST_YEAR: i32 = 1900;
/// E.164: at most 15 digits including the country code.
pub const E164_MAX_DIGITS: u32 = 15;
/// The shortest national number we accept (some countries have 4-digit numbers).
pub const PHONE_MIN_DIGITS: u32 = 4;

// MARK: - Types

/// A calendar day with no time zone: the apps pass the device's local "today".
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct CivilDate {
    pub year: i32,
    pub month: u32,
    pub day: u32,
}

impl CivilDate {
    /// `None` unless this is a real day of the proleptic Gregorian calendar.
    pub fn new(year: i32, month: u32, day: u32) -> Option<Self> {
        ((1..=12).contains(&month) && day >= 1 && day <= days_in_month(year, month)).then_some(Self { year, month, day })
    }

    /// `YYYY-MM-DD`, the stored form.
    pub fn iso(&self) -> String {
        format!("{:04}-{:02}-{:02}", self.year, self.month, self.day)
    }

    /// The UTC day of a Unix time in milliseconds, shifted by `offset_minutes` (the
    /// device's UTC offset) to get the local day.
    pub fn from_unix_ms(ms: i64, offset_minutes: i32) -> Self {
        let shifted = ms + i64::from(offset_minutes) * 60_000;
        Self::from_unix_days(shifted.div_euclid(86_400_000))
    }

    /// Howard Hinnant's `civil_from_days`.
    pub fn from_unix_days(days: i64) -> Self {
        let z = days + 719_468;
        let era = z.div_euclid(146_097);
        let doe = z.rem_euclid(146_097);
        let yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365;
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        let mp = (5 * doy + 2) / 153;
        let day = (doy - (153 * mp + 2) / 5 + 1) as u32;
        let month = if mp < 10 { mp + 3 } else { mp - 9 } as u32;
        let year = (yoe + era * 400 + i64::from(month <= 2)) as i32;
        Self { year, month, day }
    }

    fn plus_years(self, years: u32) -> Self {
        let year = self.year + years as i32;
        Self { year, month: self.month, day: self.day.min(days_in_month(year, self.month)) }
    }
}

fn is_leap(year: i32) -> bool {
    (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

fn days_in_month(year: i32, month: u32) -> u32 {
    match month {
        1 | 3 | 5 | 7 | 8 | 10 | 12 => 31,
        4 | 6 | 9 | 11 => 30,
        2 if is_leap(year) => 29,
        2 => 28,
        _ => 0,
    }
}

/// The payload field an issue belongs to. Serialized as the field's JSON key.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub enum Field {
    ItemName,
    Email,
    Username,
    Password,
    Website,
    Note,
    Totp,
    Title,
    Content,
    AccountHolder,
    /// Card, phone and ID document number.
    Number,
    SecurityCode,
    ExpiryDate,
    AccountNumber,
    RoutingNumber,
    CountryCode,
    BankName,
    FirstName,
    MiddleName,
    LastName,
    DefaultUsername,
    BirthDate,
    BirthPlace,
    Address,
    City,
    ZipCode,
    Recipient,
    Building,
    Floor,
    Apartment,
    DoorCode,
    CompanyName,
    JobTitle,
    #[serde(rename = "websiteURL")]
    WebsiteUrl,
    Name,
    IssueDate,
    IssuePlace,
}

/// What is wrong with a field. Each app maps these to its own localized message.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum Problem {
    /// Empty (or only spaces) but the item needs it.
    Required,
    /// Longer than `max` characters.
    TooLong { max: u32 },
    /// Only digits (and spaces or dashes) belong here.
    DigitsOnly,
    /// Only letters, digits, spaces and dashes belong here.
    LettersDigitsOnly,
    /// Not a length any card brand uses (12–19 digits; per-brand when the brand is known).
    CardNumberLength,
    /// Fails the Luhn check: a typo.
    CardNumberChecksum,
    /// Security code length: 3 digits, 4 for American Express, 3–4 when the brand is unknown.
    SecurityCodeLength { min: u32, max: u32 },
    /// Not `MM/YY`.
    ExpiryFormat,
    /// `MM` is not 01–12.
    ExpiryMonth,
    /// More than `max_years` years from today.
    ExpiryTooFar { max_years: u32 },
    /// Not a real calendar date in a format Qasa reads.
    DateInvalid,
    /// After today (birth and issue dates).
    DateInFuture,
    /// Before [`EARLIEST_YEAR`].
    DateTooOld,
    /// An expiry date on or before the issue date.
    DateBeforeIssue,
    /// More than `max_years` years from today.
    DateTooFar { max_years: u32 },
    /// Not shaped like `name@example.com`.
    EmailInvalid,
    /// Not shaped like `example.com` or `https://example.com/login`.
    UrlInvalid,
    /// The national number has fewer than `min` or more than `max` digits (E.164 allows
    /// 15 including the country code).
    PhoneLength { min: u32, max: u32 },
    /// The number starts with `+` and a country code other than the item's.
    PhoneCountryMismatch,
    /// Shaped like an IBAN but the length is wrong for its country.
    IbanInvalid,
    /// IBAN check digits don't match: a typo.
    IbanChecksum,
    /// Not a routing number, sort code, BSB, IFSC or SWIFT/BIC code (US: ABA checksum).
    RoutingInvalid,
    /// Not a two-letter country code.
    CountryInvalid,
    /// Not a 2FA setup key or `otpauth://totp` link.
    TotpInvalid,
}

/// One problem with one field. [`validate`] returns at most one per field.
#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct FieldIssue {
    pub field: Field,
    pub problem: Problem,
}

impl Field {
    /// The JSON key, e.g. `expiryDate`.
    pub fn key(self) -> &'static str {
        match self {
            Field::ItemName => "itemName",
            Field::Email => "email",
            Field::Username => "username",
            Field::Password => "password",
            Field::Website => "website",
            Field::Note => "note",
            Field::Totp => "totp",
            Field::Title => "title",
            Field::Content => "content",
            Field::AccountHolder => "accountHolder",
            Field::Number => "number",
            Field::SecurityCode => "securityCode",
            Field::ExpiryDate => "expiryDate",
            Field::AccountNumber => "accountNumber",
            Field::RoutingNumber => "routingNumber",
            Field::CountryCode => "countryCode",
            Field::BankName => "bankName",
            Field::FirstName => "firstName",
            Field::MiddleName => "middleName",
            Field::LastName => "lastName",
            Field::DefaultUsername => "defaultUsername",
            Field::BirthDate => "birthDate",
            Field::BirthPlace => "birthPlace",
            Field::Address => "address",
            Field::City => "city",
            Field::ZipCode => "zipCode",
            Field::Recipient => "recipient",
            Field::Building => "building",
            Field::Floor => "floor",
            Field::Apartment => "apartment",
            Field::DoorCode => "doorCode",
            Field::CompanyName => "companyName",
            Field::JobTitle => "jobTitle",
            Field::WebsiteUrl => "websiteURL",
            Field::Name => "name",
            Field::IssueDate => "issueDate",
            Field::IssuePlace => "issuePlace",
        }
    }
}

impl Problem {
    /// A stable id such as `tooLong(500)`, for logs and tests. The UI switches on the enum.
    pub fn code(self) -> String {
        match self {
            Problem::Required => "required".into(),
            Problem::TooLong { max } => format!("tooLong({max})"),
            Problem::DigitsOnly => "digitsOnly".into(),
            Problem::LettersDigitsOnly => "lettersDigitsOnly".into(),
            Problem::CardNumberLength => "cardNumberLength".into(),
            Problem::CardNumberChecksum => "cardNumberChecksum".into(),
            Problem::SecurityCodeLength { min, max } => format!("securityCodeLength({min},{max})"),
            Problem::ExpiryFormat => "expiryFormat".into(),
            Problem::ExpiryMonth => "expiryMonth".into(),
            Problem::ExpiryTooFar { max_years } => format!("expiryTooFar({max_years})"),
            Problem::DateInvalid => "dateInvalid".into(),
            Problem::DateInFuture => "dateInFuture".into(),
            Problem::DateTooOld => "dateTooOld".into(),
            Problem::DateBeforeIssue => "dateBeforeIssue".into(),
            Problem::DateTooFar { max_years } => format!("dateTooFar({max_years})"),
            Problem::EmailInvalid => "emailInvalid".into(),
            Problem::UrlInvalid => "urlInvalid".into(),
            Problem::PhoneLength { min, max } => format!("phoneLength({min},{max})"),
            Problem::PhoneCountryMismatch => "phoneCountryMismatch".into(),
            Problem::IbanInvalid => "ibanInvalid".into(),
            Problem::IbanChecksum => "ibanChecksum".into(),
            Problem::RoutingInvalid => "routingInvalid".into(),
            Problem::CountryInvalid => "countryInvalid".into(),
            Problem::TotpInvalid => "totpInvalid".into(),
        }
    }
}

impl FieldIssue {
    /// `field:problem`, e.g. `number:cardNumberChecksum`.
    pub fn code(&self) -> String {
        format!("{}:{}", self.field.key(), self.problem.code())
    }
}

#[derive(Serialize, Deserialize, Clone, Copy, Debug, PartialEq, Eq, Hash)]
#[serde(rename_all = "camelCase")]
pub enum CardBrand {
    Visa,
    Mastercard,
    Amex,
    Discover,
    DinersClub,
    Jcb,
    UnionPay,
    Maestro,
    Mir,
    Unknown,
}

impl CardBrand {
    /// From the leading digits (IIN ranges). Separators and Arabic-Indic digits are fine.
    pub fn detect(number: &str) -> Self {
        let digits: String = ascii_digits(number).chars().filter(char::is_ascii_digit).collect();
        let prefix = |n: usize| digits.get(..n).and_then(|p| p.parse::<u32>().ok());
        let in_range = |n: usize, lo: u32, hi: u32| prefix(n).is_some_and(|p| (lo..=hi).contains(&p));
        if in_range(2, 34, 34) || in_range(2, 37, 37) {
            CardBrand::Amex
        } else if digits.starts_with('4') {
            CardBrand::Visa
        } else if in_range(2, 51, 55) || in_range(4, 2221, 2720) {
            CardBrand::Mastercard
        } else if in_range(4, 2200, 2204) {
            CardBrand::Mir
        } else if in_range(4, 6011, 6011) || in_range(3, 644, 649) || in_range(2, 65, 65) {
            CardBrand::Discover
        } else if in_range(4, 3528, 3589) {
            CardBrand::Jcb
        } else if in_range(3, 300, 305) || in_range(2, 36, 36) || in_range(2, 38, 39) {
            CardBrand::DinersClub
        } else if in_range(2, 62, 62) {
            CardBrand::UnionPay
        } else if in_range(2, 50, 50) || in_range(2, 56, 58) || digits.starts_with('6') {
            CardBrand::Maestro
        } else {
            CardBrand::Unknown
        }
    }

    /// The digit counts this brand issues.
    pub fn lengths(self) -> &'static [usize] {
        match self {
            CardBrand::Visa => &[13, 16, 19],
            CardBrand::Mastercard => &[16],
            CardBrand::Amex => &[15],
            CardBrand::Discover | CardBrand::Jcb | CardBrand::UnionPay | CardBrand::Mir => &[16, 17, 18, 19],
            CardBrand::DinersClub => &[14, 15, 16, 17, 18, 19],
            CardBrand::Maestro | CardBrand::Unknown => &[12, 13, 14, 15, 16, 17, 18, 19],
        }
    }

    /// `(min, max)` security code digits.
    pub fn security_code_digits(self) -> (u32, u32) {
        match self {
            CardBrand::Amex => (4, 4),
            CardBrand::Unknown => (3, 4),
            _ => (3, 3),
        }
    }

    /// Some UnionPay cards don't carry a Luhn check digit.
    fn uses_luhn(self) -> bool {
        self != CardBrand::UnionPay
    }
}

// MARK: - Validate

/// Every issue with `payload`, at most one per field, in form order. `today` is the
/// device's local date (for "not in the future" and "not absurdly far out" rules).
pub fn validate(payload: &VaultPayload, today: CivilDate) -> Vec<FieldIssue> {
    let mut c = Checker::default();
    match payload {
        VaultPayload::Login(f) => login(&mut c, f),
        VaultPayload::SecureNote(f) => {
            c.required(Field::Title, &f.title, SHORT_MAX);
            c.optional(Field::Content, &f.content, CONTENT_MAX);
        }
        VaultPayload::Card(f) => card(&mut c, f, today),
        VaultPayload::BankAccount(f) => bank(&mut c, f),
        VaultPayload::Name(f) => name(&mut c, f, today),
        VaultPayload::PersonalEmail(f) => {
            c.optional(Field::ItemName, &f.item_name, SHORT_MAX);
            if c.required(Field::Email, &f.email, SHORT_MAX) && !email_ok(&f.email) {
                c.push(Field::Email, Problem::EmailInvalid);
            }
        }
        VaultPayload::Phone(f) => phone(&mut c, f),
        VaultPayload::Address(f) => address(&mut c, f),
        VaultPayload::Company(f) => {
            c.required(Field::CompanyName, &f.company_name, SHORT_MAX);
            c.optional(Field::JobTitle, &f.job_title, SHORT_MAX);
        }
        VaultPayload::Website(f) => {
            c.required(Field::ItemName, &f.item_name, SHORT_MAX);
            if c.required(Field::WebsiteUrl, &f.website_url, SHORT_MAX) && !url_ok(&f.website_url) {
                c.push(Field::WebsiteUrl, Problem::UrlInvalid);
            }
        }
        VaultPayload::IdCard(f) | VaultPayload::DriverLicense(f) => document(&mut c, f, today, false),
        VaultPayload::Passport(f) => document(&mut c, f, today, true),
    }
    c.issues
}

/// Whether `payload` passes every rule.
pub fn is_valid(payload: &VaultPayload, today: CivilDate) -> bool {
    validate(payload, today).is_empty()
}

#[derive(Default)]
struct Checker {
    issues: Vec<FieldIssue>,
}

impl Checker {
    fn push(&mut self, field: Field, problem: Problem) {
        if !self.issues.iter().any(|i| i.field == field) {
            self.issues.push(FieldIssue { field, problem });
        }
    }

    /// Length cap only. True when there is something to check further.
    fn optional(&mut self, field: Field, value: &str, max: u32) -> bool {
        if blank(value) {
            return false;
        }
        if value.chars().count() > max as usize {
            self.push(field, Problem::TooLong { max });
            return false;
        }
        true
    }

    fn required(&mut self, field: Field, value: &str, max: u32) -> bool {
        if blank(value) {
            self.push(field, Problem::Required);
            return false;
        }
        self.optional(field, value, max)
    }

    fn country(&mut self, field: Field, code: &str) {
        let code = code.trim();
        if code.len() != 2 || !code.chars().all(|ch| ch.is_ascii_alphabetic()) {
            self.push(field, Problem::CountryInvalid);
        }
    }

    fn date(&mut self, field: Field, raw: &str, today: CivilDate, past_only: bool) -> Option<CivilDate> {
        if blank(raw) {
            return None;
        }
        let Some(date) = parse_date(raw) else {
            self.push(field, Problem::DateInvalid);
            return None;
        };
        if date.year < EARLIEST_YEAR {
            self.push(field, Problem::DateTooOld);
            return None;
        }
        if past_only && date > today {
            self.push(field, Problem::DateInFuture);
            return None;
        }
        Some(date)
    }
}

fn login(c: &mut Checker, f: &LoginFields) {
    c.required(Field::ItemName, &f.item_name, SHORT_MAX);
    if c.optional(Field::Email, &f.email, SHORT_MAX) && !email_ok(&f.email) {
        c.push(Field::Email, Problem::EmailInvalid);
    }
    c.optional(Field::Username, &f.username, SHORT_MAX);
    // Passwords are never trimmed; a password of spaces is still a password.
    if f.password.chars().count() > PASSWORD_MAX as usize {
        c.push(Field::Password, Problem::TooLong { max: PASSWORD_MAX });
    }
    if c.optional(Field::Website, &f.website, SHORT_MAX) && !url_ok(&f.website) {
        c.push(Field::Website, Problem::UrlInvalid);
    }
    c.optional(Field::Note, &f.note, NOTE_MAX);
    if let Some(totp) = f.totp.as_deref().filter(|t| !blank(t)) {
        if Totp::parse(totp).is_err() {
            c.push(Field::Totp, Problem::TotpInvalid);
        }
    }
}

fn card(c: &mut Checker, f: &CardFields, today: CivilDate) {
    c.required(Field::ItemName, &f.item_name, SHORT_MAX);
    c.optional(Field::AccountHolder, &f.account_holder, SHORT_MAX);
    if let Some(problem) = card_number_problem(&f.number) {
        c.push(Field::Number, problem);
    }
    if !blank(&f.security_code) {
        let code = ascii_digits(f.security_code.trim());
        let (min, max) = CardBrand::detect(&f.number).security_code_digits();
        if !code.chars().all(|ch| ch.is_ascii_digit()) {
            c.push(Field::SecurityCode, Problem::DigitsOnly);
        } else if !(min..=max).contains(&(code.len() as u32)) {
            c.push(Field::SecurityCode, Problem::SecurityCodeLength { min, max });
        }
    }
    if !blank(&f.expiry_date) {
        match parse_card_expiry(&f.expiry_date) {
            Err(problem) => c.push(Field::ExpiryDate, problem),
            Ok((month, year)) => {
                let limit = today.plus_years(CARD_EXPIRY_MAX_YEARS);
                if (year, month) > (limit.year, limit.month) {
                    c.push(Field::ExpiryDate, Problem::ExpiryTooFar { max_years: CARD_EXPIRY_MAX_YEARS });
                }
            }
        }
    }
    c.optional(Field::Note, &f.note, NOTE_MAX);
}

fn card_number_problem(raw: &str) -> Option<Problem> {
    if blank(raw) {
        return None;
    }
    let cleaned = ascii_digits(raw.trim());
    if !cleaned.chars().all(|ch| ch.is_ascii_digit() || ch == ' ' || ch == '-') {
        return Some(Problem::DigitsOnly);
    }
    let digits: String = cleaned.chars().filter(char::is_ascii_digit).collect();
    let brand = CardBrand::detect(&digits);
    if !brand.lengths().contains(&digits.len()) {
        return Some(Problem::CardNumberLength);
    }
    (brand.uses_luhn() && !luhn_ok(&digits)).then_some(Problem::CardNumberChecksum)
}

fn luhn_ok(digits: &str) -> bool {
    let sum: u32 = digits
        .bytes()
        .rev()
        .enumerate()
        .map(|(i, b)| {
            let d = u32::from(b - b'0');
            if i % 2 == 1 {
                let x = d * 2;
                if x > 9 { x - 9 } else { x }
            } else {
                d
            }
        })
        .sum();
    sum % 10 == 0
}

fn bank(c: &mut Checker, f: &BankAccountFields) {
    c.required(Field::ItemName, &f.item_name, SHORT_MAX);
    c.optional(Field::AccountHolder, &f.account_holder, SHORT_MAX);
    if c.optional(Field::AccountNumber, &f.account_number, ACCOUNT_NUMBER_MAX) {
        if let Some(problem) = account_number_problem(&f.account_number) {
            c.push(Field::AccountNumber, problem);
        }
    }
    if c.optional(Field::RoutingNumber, &f.routing_number, SHORT_MAX) && !routing_ok(&f.routing_number, &f.country_code) {
        c.push(Field::RoutingNumber, Problem::RoutingInvalid);
    }
    c.country(Field::CountryCode, &f.country_code);
    c.optional(Field::BankName, &f.bank_name, SHORT_MAX);
}

fn compact_upper(raw: &str) -> String {
    ascii_digits(raw).chars().filter(|ch| !ch.is_whitespace() && *ch != '-').collect::<String>().to_uppercase()
}

/// Two letters then two digits: treated (and checked) as an IBAN.
fn looks_like_iban(compact: &str) -> bool {
    let b = compact.as_bytes();
    b.len() >= 4 && b[0].is_ascii_uppercase() && b[1].is_ascii_uppercase() && b[2].is_ascii_digit() && b[3].is_ascii_digit()
}

fn account_number_problem(raw: &str) -> Option<Problem> {
    let compact = compact_upper(raw);
    if !compact.chars().all(|ch| ch.is_ascii_alphanumeric()) {
        return Some(Problem::LettersDigitsOnly);
    }
    if !looks_like_iban(&compact) {
        return None;
    }
    let expected = iban_length(&compact[..2]);
    let length_ok = match expected {
        Some(n) => compact.len() == n,
        None => (15..=34).contains(&compact.len()),
    };
    if !length_ok {
        return Some(Problem::IbanInvalid);
    }
    (iban_mod97(&compact) != 1).then_some(Problem::IbanChecksum)
}

fn iban_mod97(compact: &str) -> u32 {
    let rearranged = format!("{}{}", &compact[4..], &compact[..4]);
    let mut rem: u32 = 0;
    for ch in rearranged.chars() {
        let value = ch.to_digit(36).expect("alphanumeric");
        rem = if value >= 10 { (rem * 100 + value) % 97 } else { (rem * 10 + value) % 97 };
    }
    rem
}

/// IBAN lengths from the SWIFT registry (plus Iran, which uses IBANs outside it).
fn iban_length(country: &str) -> Option<usize> {
    const LENGTHS: &[(&str, usize)] = &[
        ("AD", 24), ("AE", 23), ("AL", 28), ("AT", 20), ("AZ", 28), ("BA", 20), ("BE", 16), ("BG", 22),
        ("BH", 22), ("BI", 27), ("BR", 29), ("BY", 28), ("CH", 21), ("CR", 22), ("CY", 28), ("CZ", 24),
        ("DE", 22), ("DJ", 27), ("DK", 18), ("DO", 28), ("EE", 20), ("EG", 29), ("ES", 24), ("FI", 18),
        ("FK", 18), ("FO", 18), ("FR", 27), ("GB", 22), ("GE", 22), ("GI", 23), ("GL", 18), ("GR", 27),
        ("GT", 28), ("HR", 21), ("HU", 28), ("IE", 22), ("IL", 23), ("IQ", 23), ("IR", 26), ("IS", 26),
        ("IT", 27), ("JO", 30), ("KW", 30), ("KZ", 20), ("LB", 28), ("LC", 32), ("LI", 21), ("LT", 20),
        ("LU", 20), ("LV", 21), ("LY", 25), ("MC", 27), ("MD", 24), ("ME", 22), ("MK", 19), ("MN", 20),
        ("MR", 27), ("MT", 31), ("MU", 30), ("NI", 28), ("NL", 18), ("NO", 15), ("OM", 23), ("PK", 24),
        ("PL", 28), ("PS", 29), ("PT", 25), ("QA", 29), ("RO", 24), ("RS", 22), ("RU", 33), ("SA", 24),
        ("SC", 31), ("SD", 18), ("SE", 24), ("SI", 19), ("SK", 24), ("SM", 27), ("SO", 23), ("ST", 25),
        ("SV", 28), ("TL", 23), ("TN", 24), ("TR", 26), ("UA", 29), ("VA", 22), ("VG", 24), ("XK", 20),
        ("YE", 30),
    ];
    LENGTHS.iter().find(|(c, _)| *c == country).map(|(_, n)| *n)
}

/// Routing number (US ABA, with its checksum), sort code, BSB, transit number, IFSC or
/// SWIFT/BIC. Lenient for other countries' digit-only codes.
fn routing_ok(raw: &str, country: &str) -> bool {
    let compact = compact_upper(raw);
    if compact.chars().all(|ch| ch.is_ascii_digit()) {
        if country.trim().eq_ignore_ascii_case("US") || compact.len() == 9 && country.trim().is_empty() {
            return compact.len() == 9 && aba_ok(&compact);
        }
        return (3..=12).contains(&compact.len());
    }
    let b = compact.as_bytes();
    let letters = |r: std::ops::Range<usize>| b[r].iter().all(u8::is_ascii_uppercase);
    let alnum = |r: std::ops::Range<usize>| b[r].iter().all(u8::is_ascii_alphanumeric);
    // SWIFT/BIC: bank(4 letters) country(2 letters) location(2) [branch(3)].
    let bic = (b.len() == 8 || b.len() == 11) && letters(0..6) && alnum(6..b.len());
    // India's IFSC: 4 letters, a zero, 6 alphanumerics.
    let ifsc = b.len() == 11 && letters(0..4) && b[4] == b'0' && alnum(5..11);
    bic || ifsc
}

fn aba_ok(digits: &str) -> bool {
    let d: Vec<u32> = digits.bytes().map(|b| u32::from(b - b'0')).collect();
    (3 * (d[0] + d[3] + d[6]) + 7 * (d[1] + d[4] + d[7]) + (d[2] + d[5] + d[8])) % 10 == 0
}

fn name(c: &mut Checker, f: &NameFields, today: CivilDate) {
    if blank(&f.first_name) && blank(&f.last_name) {
        c.push(Field::FirstName, Problem::Required);
    }
    c.optional(Field::FirstName, &f.first_name, SHORT_MAX);
    c.optional(Field::MiddleName, &f.middle_name, SHORT_MAX);
    c.optional(Field::LastName, &f.last_name, SHORT_MAX);
    c.optional(Field::DefaultUsername, &f.default_username, SHORT_MAX);
    c.date(Field::BirthDate, &f.birth_date, today, true);
    c.optional(Field::BirthPlace, &f.birth_place, SHORT_MAX);
}

fn phone(c: &mut Checker, f: &PhoneFields) {
    c.optional(Field::ItemName, &f.item_name, SHORT_MAX);
    c.country(Field::CountryCode, &f.country_code);
    if !c.required(Field::Number, &f.number, SHORT_MAX) {
        return;
    }
    let dial = dial_code(&normalize_country(&f.country_code)).trim_start_matches('+').to_string();
    match national_digits(&f.number, &dial) {
        Err(problem) => c.push(Field::Number, problem),
        Ok(national) => {
            let max = E164_MAX_DIGITS.saturating_sub(dial.len() as u32);
            let n = national.trim_start_matches('0').len() as u32;
            if !(PHONE_MIN_DIGITS..=max).contains(&n) {
                c.push(Field::Number, Problem::PhoneLength { min: PHONE_MIN_DIGITS, max });
            }
        }
    }
}

const PHONE_SEPARATORS: [char; 5] = [' ', '-', '.', '(', ')'];

/// The national digits of a typed number: separators dropped, and a leading `+964` /
/// `00964` dropped when it is the item's own country code.
fn national_digits(raw: &str, dial: &str) -> Result<String, Problem> {
    let compact: String = ascii_digits(raw.trim()).chars().filter(|ch| !PHONE_SEPARATORS.contains(ch)).collect();
    let international = compact.strip_prefix('+').or_else(|| compact.strip_prefix("00"));
    let national = match international {
        Some(rest) => {
            if !rest.chars().all(|ch| ch.is_ascii_digit()) {
                return Err(Problem::DigitsOnly);
            }
            rest.strip_prefix(dial).ok_or(Problem::PhoneCountryMismatch)?.to_string()
        }
        None => compact,
    };
    if !national.chars().all(|ch| ch.is_ascii_digit()) {
        return Err(Problem::DigitsOnly);
    }
    Ok(national)
}

fn address(c: &mut Checker, f: &AddressFields) {
    c.required(Field::ItemName, &f.item_name, SHORT_MAX);
    c.optional(Field::Address, &f.address, ADDRESS_MAX);
    c.optional(Field::City, &f.city, SHORT_MAX);
    if c.optional(Field::ZipCode, &f.zip_code, POSTAL_CODE_MAX) && !letters_digits(&f.zip_code) {
        c.push(Field::ZipCode, Problem::LettersDigitsOnly);
    }
    c.country(Field::CountryCode, &f.country_code);
    c.optional(Field::Recipient, &f.recipient, SHORT_MAX);
    c.optional(Field::Building, &f.building, SHORT_MAX);
    c.optional(Field::Floor, &f.floor, SHORT_MAX);
    c.optional(Field::Apartment, &f.apartment, SHORT_MAX);
    c.optional(Field::DoorCode, &f.door_code, SHORT_MAX);
}

fn document(c: &mut Checker, f: &IdDocumentFields, today: CivilDate, passport: bool) {
    c.required(Field::Name, &f.name, SHORT_MAX);
    c.country(Field::CountryCode, &f.country_code);
    if c.optional(Field::Number, &f.number, DOCUMENT_NUMBER_MAX) && !letters_digits(&f.number) {
        c.push(Field::Number, Problem::LettersDigitsOnly);
    }
    let issued = c.date(Field::IssueDate, &f.issue_date, today, true);
    if passport {
        c.optional(Field::IssuePlace, &f.issue_place, SHORT_MAX);
    }
    if let Some(expiry) = c.date(Field::ExpiryDate, &f.expiry_date, today, false) {
        if issued.is_some_and(|issued| expiry <= issued) {
            c.push(Field::ExpiryDate, Problem::DateBeforeIssue);
        } else if expiry > today.plus_years(DOCUMENT_EXPIRY_MAX_YEARS) {
            c.push(Field::ExpiryDate, Problem::DateTooFar { max_years: DOCUMENT_EXPIRY_MAX_YEARS });
        }
    }
}

// MARK: - Shapes

fn blank(s: &str) -> bool {
    s.trim().is_empty()
}

/// Letters (any script), digits, spaces and dashes.
fn letters_digits(s: &str) -> bool {
    s.trim().chars().all(|ch| ch.is_alphanumeric() || ch == ' ' || ch == '-')
}

/// Arabic-Indic (U+0660–0669) and Extended Arabic-Indic (U+06F0–06F9) digits → ASCII.
pub fn ascii_digits(s: &str) -> String {
    s.chars()
        .map(|ch| match ch {
            '\u{0660}'..='\u{0669}' => char::from(b'0' + (ch as u32 - 0x0660) as u8),
            '\u{06F0}'..='\u{06F9}' => char::from(b'0' + (ch as u32 - 0x06F0) as u8),
            _ => ch,
        })
        .collect()
}

/// `local@domain.tld`: one `@`, no spaces, a dotted domain with a 2+ letter TLD.
/// Unicode letters are allowed (internationalized addresses).
pub fn email_ok(raw: &str) -> bool {
    let s = raw.trim();
    if s.chars().count() > 254 || s.chars().any(char::is_whitespace) {
        return false;
    }
    let Some((local, domain)) = s.split_once('@') else { return false };
    !local.is_empty()
        && local.chars().count() <= 64
        && !local.starts_with('.')
        && !local.ends_with('.')
        && !local.contains("..")
        && !domain.contains('@')
        && domain_ok(domain)
}

fn domain_ok(domain: &str) -> bool {
    let domain = domain.strip_suffix('.').unwrap_or(domain);
    let labels: Vec<&str> = domain.split('.').collect();
    if labels.len() < 2 || domain.chars().count() > 253 {
        return false;
    }
    let label_ok = |l: &&str| {
        !l.is_empty()
            && l.chars().count() <= 63
            && !l.starts_with('-')
            && !l.ends_with('-')
            && l.chars().all(|ch| ch.is_alphanumeric() || ch == '-')
    };
    let tld = labels[labels.len() - 1];
    labels.iter().all(label_ok)
        && (tld.chars().count() >= 2 && tld.chars().all(char::is_alphabetic) || tld.starts_with("xn--"))
}

/// A web address: `example.com`, `https://example.com/login`, `localhost:8080`, an IP, or
/// an app link with another scheme (`androidapp://com.example`). No spaces.
pub fn url_ok(raw: &str) -> bool {
    let s = raw.trim();
    if s.is_empty() || s.chars().any(char::is_whitespace) {
        return false;
    }
    let rest = match s.split_once("://") {
        Some((scheme, rest)) => {
            let scheme_ok = scheme.chars().next().is_some_and(|ch| ch.is_ascii_alphabetic())
                && scheme.chars().all(|ch| ch.is_ascii_alphanumeric() || "+-.".contains(ch));
            if !scheme_ok || rest.is_empty() {
                return false;
            }
            if !matches!(scheme.to_ascii_lowercase().as_str(), "http" | "https") {
                return true;
            }
            rest
        }
        None => s,
    };
    let authority = rest.split(['/', '?', '#']).next().unwrap_or("");
    let host_port = authority.rsplit_once('@').map_or(authority, |(_, h)| h);
    let (host, port) = if let Some(v6) = host_port.strip_prefix('[') {
        match v6.split_once(']') {
            Some((addr, tail)) if addr.contains(':') && addr.chars().all(|ch| ch.is_ascii_hexdigit() || ch == ':' || ch == '.') => {
                return tail.is_empty() || tail.strip_prefix(':').is_some_and(port_ok);
            }
            _ => return false,
        }
    } else {
        match host_port.rsplit_once(':') {
            Some((h, p)) => (h, Some(p)),
            None => (host_port, None),
        }
    };
    if port.is_some_and(|p| !port_ok(p)) {
        return false;
    }
    host.eq_ignore_ascii_case("localhost") || ipv4_ok(host) || domain_ok(host)
}

fn port_ok(p: &str) -> bool {
    !p.is_empty() && p.len() <= 5 && p.chars().all(|ch| ch.is_ascii_digit()) && p.parse::<u32>().is_ok_and(|n| n <= 65_535)
}

fn ipv4_ok(host: &str) -> bool {
    let parts: Vec<&str> = host.split('.').collect();
    parts.len() == 4 && parts.iter().all(|p| !p.is_empty() && p.len() <= 3 && p.chars().all(|ch| ch.is_ascii_digit()) && p.parse::<u32>().is_ok_and(|n| n <= 255))
}

// MARK: - Dates

const MONTHS: [&str; 12] = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const MONTH_NAMES: [&str; 12] =
    ["january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"];

fn month_from_word(word: &str) -> Option<u32> {
    let w = word.trim_end_matches('.').to_lowercase();
    if w == "sept" {
        return Some(9);
    }
    let i = MONTHS.iter().position(|m| *m == w).or_else(|| MONTH_NAMES.iter().position(|m| *m == w))?;
    Some(i as u32 + 1)
}

/// Reads a stored or typed date. Canonical `YYYY-MM-DD`, plus the forms older versions
/// stored: `Nov 6, 2000` / `Nov 6 2000` / `6 Nov 2000` (English month names), and
/// `06/11/2000` (day first; month first when day first is impossible). Arabic-Indic digits
/// are read as digits. `None` for anything else, including impossible days.
pub fn parse_date(raw: &str) -> Option<CivilDate> {
    let s = ascii_digits(raw.trim()).replace('،', ",");
    if s.is_empty() {
        return None;
    }
    let num = |p: &str, max_len: usize| -> Option<u32> {
        (!p.is_empty() && p.len() <= max_len && p.chars().all(|ch| ch.is_ascii_digit())).then(|| p.parse().ok()).flatten()
    };
    // YYYY-MM-DD (also YYYY/MM/DD).
    let parts: Vec<&str> = s.split(['-', '/', '.']).collect();
    if parts.len() == 3 && parts[0].len() == 4 {
        return CivilDate::new(num(parts[0], 4)? as i32, num(parts[1], 2)?, num(parts[2], 2)?);
    }
    // DD/MM/YYYY, then MM/DD/YYYY.
    if parts.len() == 3 && parts[2].len() == 4 {
        let (a, b, y) = (num(parts[0], 2)?, num(parts[1], 2)?, num(parts[2], 4)? as i32);
        return CivilDate::new(y, b, a).or_else(|| CivilDate::new(y, a, b));
    }
    // English month names, as iOS's en "MMMdyyyy" template and the desktop picker wrote them.
    let words: Vec<&str> = s.split([' ', ',']).filter(|w| !w.is_empty()).collect();
    if words.len() == 3 {
        let year = num(words[2], 4).filter(|_| words[2].len() == 4)? as i32;
        if let Some(month) = month_from_word(words[0]) {
            return CivilDate::new(year, month, num(words[1], 2)?);
        }
        if let Some(month) = month_from_word(words[1]) {
            return CivilDate::new(year, month, num(words[0], 2)?);
        }
    }
    None
}

/// `(month, four-digit year)` of a card expiry: `MM/YY`, plus `MM/YYYY`, `MM-YY`, `MMYY`
/// and the full dates older versions stored (which keep their month and year).
pub fn parse_card_expiry(raw: &str) -> Result<(u32, i32), Problem> {
    let s: String = ascii_digits(raw.trim()).chars().filter(|ch| !ch.is_whitespace()).collect();
    let digits_only = |p: &str| !p.is_empty() && p.chars().all(|ch| ch.is_ascii_digit());
    let pair = match s.split_once(['/', '-', '.']) {
        Some((m, y)) if digits_only(m) && digits_only(y) && m.len() <= 2 && (y.len() == 2 || y.len() == 4) => Some((m, y)),
        None if digits_only(&s) && s.len() == 4 => Some((&s[..2], &s[2..])),
        None if digits_only(&s) && s.len() == 6 => Some((&s[..2], &s[2..])),
        _ => None,
    };
    let Some((m, y)) = pair else {
        return parse_date(raw).map(|d| (d.month, d.year)).ok_or(Problem::ExpiryFormat);
    };
    let month: u32 = m.parse().map_err(|_| Problem::ExpiryFormat)?;
    let year: i32 = y.parse().map_err(|_| Problem::ExpiryFormat)?;
    if !(1..=12).contains(&month) {
        return Err(Problem::ExpiryMonth);
    }
    Ok((month, if y.len() == 2 { 2000 + year } else { year }))
}

// MARK: - Input formatting

/// Card number as the user types: digits only (at most 19), grouped 4-4-4-4, or 4-6-5
/// for American Express and 4-6-4 for 14-digit Diners Club.
pub fn format_card_number(raw: &str) -> String {
    let digits: String = ascii_digits(raw).chars().filter(char::is_ascii_digit).take(19).collect();
    let brand = CardBrand::detect(&digits);
    let groups: &[usize] = match brand {
        CardBrand::Amex => &[4, 6, 5],
        CardBrand::DinersClub if digits.len() == 14 => &[4, 6, 4],
        _ => &[4, 4, 4, 4, 3],
    };
    let mut out = String::new();
    let mut rest = digits.as_str();
    for &n in groups.iter().chain(std::iter::repeat(&4)) {
        if rest.is_empty() {
            break;
        }
        let (head, tail) = rest.split_at(n.min(rest.len()));
        if !out.is_empty() {
            out.push(' ');
        }
        out.push_str(head);
        rest = tail;
    }
    out
}

/// Card expiry as the user types: digits only, `MM/YY` with the slash added after the
/// month. A first digit of 2–9 can only be a single-digit month, so it becomes `0d`. A
/// second digit that would make the month 00 or more than 12 is not taken, so the field
/// never holds a month outside 01–12.
pub fn format_card_expiry(raw: &str) -> String {
    let mut digits: Vec<char> = ascii_digits(raw).chars().filter(char::is_ascii_digit).collect();
    if digits.first().is_some_and(|ch| ('2'..='9').contains(ch)) {
        digits.insert(0, '0');
    }
    while digits.len() >= 2 && !matches!((digits[0], digits[1]), ('0', '1'..='9') | ('1', '0'..='2')) {
        digits.remove(1);
    }
    let mut digits: String = digits.into_iter().collect();
    digits.truncate(4);
    if digits.len() <= 2 { digits } else { format!("{}/{}", &digits[..2], &digits[2..]) }
}

// MARK: - Normalize

/// The stored canonical form of what the user typed. Only rewrites values it can read
/// without doubt and leaves everything else alone (so [`validate`] still reports it):
/// trimmed emails and web addresses; ASCII digits; card numbers grouped; card expiry
/// `MM/YY`; dates `YYYY-MM-DD`; upper-case country codes; IBANs in groups of four; the
/// item's own `+code` dropped from a phone number. Never touches passwords, notes or names.
/// Idempotent.
pub fn normalize(payload: &VaultPayload) -> VaultPayload {
    let mut p = payload.clone();
    match &mut p {
        VaultPayload::Login(f) => {
            f.email = f.email.trim().to_string();
            f.website = f.website.trim().to_string();
            if f.totp.as_deref().is_some_and(blank) {
                f.totp = None;
            }
        }
        VaultPayload::SecureNote(_) | VaultPayload::Company(_) => {}
        VaultPayload::Card(f) => {
            if card_number_problem(&f.number).is_none() && !blank(&f.number) {
                f.number = format_card_number(&f.number);
            }
            let code = ascii_digits(f.security_code.trim());
            if code.chars().all(|ch| ch.is_ascii_digit()) {
                f.security_code = code;
            }
            if let Ok((month, year)) = parse_card_expiry(&f.expiry_date) {
                f.expiry_date = format!("{:02}/{:02}", month, year.rem_euclid(100));
            }
        }
        VaultPayload::BankAccount(f) => {
            f.country_code = normalize_country(&f.country_code);
            let compact = compact_upper(&f.account_number);
            if looks_like_iban(&compact) && account_number_problem(&f.account_number).is_none() {
                f.account_number = compact.as_bytes().chunks(4).map(|c| std::str::from_utf8(c).expect("ascii")).collect::<Vec<_>>().join(" ");
            } else {
                f.account_number = ascii_digits(f.account_number.trim());
            }
            f.routing_number = ascii_digits(f.routing_number.trim());
        }
        VaultPayload::Name(f) => f.birth_date = normalize_date(&f.birth_date),
        VaultPayload::PersonalEmail(f) => f.email = f.email.trim().to_string(),
        VaultPayload::Phone(f) => {
            f.country_code = normalize_country(&f.country_code);
            let dial = dial_code(&f.country_code).trim_start_matches('+').to_string();
            let typed = ascii_digits(f.number.trim());
            f.number = match national_digits(&typed, &dial) {
                // Keep the user's grouping; drop only the redundant own-country prefix.
                Ok(_) if typed.starts_with('+') || typed.starts_with("00") => strip_dial_prefix(&typed, &dial),
                _ => typed,
            };
        }
        VaultPayload::Address(f) => {
            f.country_code = normalize_country(&f.country_code);
            f.zip_code = ascii_digits(f.zip_code.trim());
        }
        VaultPayload::Website(f) => f.website_url = f.website_url.trim().to_string(),
        VaultPayload::IdCard(f) | VaultPayload::Passport(f) | VaultPayload::DriverLicense(f) => {
            f.country_code = normalize_country(&f.country_code);
            f.number = ascii_digits(f.number.trim());
            f.issue_date = normalize_date(&f.issue_date);
            f.expiry_date = normalize_date(&f.expiry_date);
        }
    }
    p
}

fn normalize_country(code: &str) -> String {
    code.trim().to_ascii_uppercase()
}

fn normalize_date(raw: &str) -> String {
    parse_date(raw).map_or_else(|| raw.to_string(), |d| d.iso())
}

/// Drops `+<dial>` or `00<dial>` (with any separators between) from the front.
fn strip_dial_prefix(typed: &str, dial: &str) -> String {
    let body = typed.strip_prefix('+').or_else(|| typed.strip_prefix("00")).unwrap_or(typed);
    let mut needed = dial.chars().peekable();
    let mut rest = body;
    while needed.peek().is_some() {
        let Some(ch) = rest.chars().next() else { break };
        if PHONE_SEPARATORS.contains(&ch) {
            rest = &rest[ch.len_utf8()..];
        } else if Some(&ch) == needed.peek() {
            needed.next();
            rest = &rest[ch.len_utf8()..];
        } else {
            return typed.to_string();
        }
    }
    rest.trim_start_matches(PHONE_SEPARATORS).to_string()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::payload::*;

    const TODAY: CivilDate = CivilDate { year: 2026, month: 11, day: 10 };

    fn issues(p: &VaultPayload) -> Vec<String> {
        validate(p, TODAY).iter().map(FieldIssue::code).collect()
    }

    fn card(number: &str, cvv: &str, expiry: &str) -> VaultPayload {
        VaultPayload::Card(CardFields {
            item_name: "Card".into(),
            number: number.into(),
            security_code: cvv.into(),
            expiry_date: expiry.into(),
            ..Default::default()
        })
    }

    #[test]
    fn civil_dates() {
        assert_eq!(CivilDate::from_unix_days(0), CivilDate { year: 1970, month: 1, day: 1 });
        assert_eq!(CivilDate::from_unix_ms(1_794_268_800_000, 0), TODAY);
        assert_eq!(CivilDate::from_unix_ms(1_794_268_800_000 - 1, 0).day, 9);
        assert_eq!(CivilDate::from_unix_ms(1_794_268_800_000 - 1, 180).day, 10);
        assert!(CivilDate::new(2024, 2, 29).is_some());
        assert!(CivilDate::new(2023, 2, 29).is_none());
        assert_eq!(CivilDate::new(2024, 2, 29).unwrap().plus_years(1), CivilDate { year: 2025, month: 2, day: 28 });
    }

    #[test]
    fn cards() {
        assert!(issues(&card("4111 1111 1111 1111", "123", "12/28")).is_empty());
        assert!(issues(&card("3782 822463 10005", "1234", "01/30")).is_empty());
        assert_eq!(issues(&card("4111 1111 1111 1112", "", "")), ["number:cardNumberChecksum"]);
        assert_eq!(issues(&card("4111 1111 1111", "", "")), ["number:cardNumberLength"]);
        assert_eq!(issues(&card("4111-abcd", "", "")), ["number:digitsOnly"]);
        assert_eq!(issues(&card("378282246310005", "123", "")), ["securityCode:securityCodeLength(4,4)"]);
        assert_eq!(issues(&card("", "12", "")), ["securityCode:securityCodeLength(3,4)"]);
        assert_eq!(issues(&card("", "", "13/28")), ["expiryDate:expiryMonth"]);
        assert_eq!(issues(&card("", "", "12/2099")), ["expiryDate:expiryTooFar(20)"]);
        assert_eq!(issues(&card("", "", "soon")), ["expiryDate:expiryFormat"]);
        // Expired cards are kept: people store old cards.
        assert!(issues(&card("", "", "01/20")).is_empty());
        // UnionPay without a Luhn digit.
        assert!(issues(&card("6212 3456 7890 1232", "", "")).is_empty());
        assert_eq!(issues(&card("", "", "")), Vec::<String>::new());
        assert_eq!(issues(&VaultPayload::Card(CardFields::default())), ["itemName:required"]);
    }

    #[test]
    fn brands_and_formatting() {
        assert_eq!(CardBrand::detect("5555 5555 5555 4444"), CardBrand::Mastercard);
        assert_eq!(CardBrand::detect("2223000048400011"), CardBrand::Mastercard);
        assert_eq!(CardBrand::detect("6011111111111117"), CardBrand::Discover);
        assert_eq!(CardBrand::detect("3530111333300000"), CardBrand::Jcb);
        assert_eq!(CardBrand::detect("30569309025904"), CardBrand::DinersClub);
        assert_eq!(CardBrand::detect("6762"), CardBrand::Maestro);
        assert_eq!(format_card_number("٤١١١١١١١١١١١١١١١"), "4111 1111 1111 1111");
        assert_eq!(format_card_number("378282246310005"), "3782 822463 10005");
        assert_eq!(format_card_number("4111111111111111111"), "4111 1111 1111 1111 111");
        assert_eq!(format_card_expiry("4"), "04");
        assert_eq!(format_card_expiry("12"), "12");
        assert_eq!(format_card_expiry("123"), "12/3");
        assert_eq!(format_card_expiry("12/28"), "12/28");
        assert_eq!(format_card_expiry("122899"), "12/28");
        // Months stay within 01–12 while typing.
        assert_eq!(format_card_expiry("13"), "1");
        assert_eq!(format_card_expiry("1328"), "12/8");
        assert_eq!(format_card_expiry("00"), "0");
        assert_eq!(format_card_expiry("0012"), "01/2");
        assert_eq!(format_card_expiry("10"), "10");
        assert_eq!(format_card_expiry("09/30"), "09/30");
    }

    #[test]
    fn dates() {
        assert_eq!(parse_date("2000-11-06"), CivilDate::new(2000, 11, 6));
        assert_eq!(parse_date("Nov 6, 2000"), CivilDate::new(2000, 11, 6));
        assert_eq!(parse_date("6 November 2000"), CivilDate::new(2000, 11, 6));
        assert_eq!(parse_date("06/11/2000"), CivilDate::new(2000, 11, 6));
        assert_eq!(parse_date("11/26/2000"), CivilDate::new(2000, 11, 26));
        assert_eq!(parse_date("٢٠٠٠-١١-٠٦"), CivilDate::new(2000, 11, 6));
        assert_eq!(parse_date("2001-02-29"), None);
        assert_eq!(parse_date("yesterday"), None);
        assert_eq!(parse_card_expiry("Nov 6, 2027"), Ok((11, 2027)));
    }

    #[test]
    fn documents() {
        let doc = |issue: &str, expiry: &str| {
            VaultPayload::Passport(IdDocumentFields { name: "P".into(), issue_date: issue.into(), expiry_date: expiry.into(), ..Default::default() })
        };
        assert!(issues(&doc("2020-01-01", "2030-01-01")).is_empty());
        assert_eq!(issues(&doc("2030-01-01", "")), ["issueDate:dateInFuture"]);
        assert_eq!(issues(&doc("2020-01-01", "2019-01-01")), ["expiryDate:dateBeforeIssue"]);
        assert_eq!(issues(&doc("", "2090-01-01")), ["expiryDate:dateTooFar(50)"]);
        assert_eq!(issues(&doc("1850-01-01", "")), ["issueDate:dateTooOld"]);
        assert_eq!(issues(&doc("Feb 30 2020", "")), ["issueDate:dateInvalid"]);
    }

    #[test]
    fn phones() {
        let phone = |country: &str, number: &str| {
            VaultPayload::Phone(PhoneFields { country_code: country.into(), number: number.into(), ..Default::default() })
        };
        assert!(issues(&phone("IQ", "0750 123 4567")).is_empty());
        assert!(issues(&phone("IQ", "+964 750 123 4567")).is_empty());
        assert_eq!(issues(&phone("IQ", "+44 20 7946 0958")), ["number:phoneCountryMismatch"]);
        assert_eq!(issues(&phone("IQ", "12")), ["number:phoneLength(4,12)"]);
        assert_eq!(issues(&phone("IQ", "750-CALL")), ["number:digitsOnly"]);
        assert_eq!(issues(&phone("IQ", "")), ["number:required"]);
        assert_eq!(issues(&phone("", "7501234567")), ["countryCode:countryInvalid"]);
        let VaultPayload::Phone(p) = normalize(&phone("IQ", "+964 750 123 4567")) else { panic!() };
        assert_eq!(p.number, "750 123 4567");
        let VaultPayload::Phone(p) = normalize(&phone("iq", "00964-750")) else { panic!() };
        assert_eq!((p.country_code.as_str(), p.number.as_str()), ("IQ", "750"));
    }

    #[test]
    fn banks() {
        let bank = |account: &str, routing: &str, country: &str| {
            VaultPayload::BankAccount(BankAccountFields {
                item_name: "B".into(),
                account_number: account.into(),
                routing_number: routing.into(),
                country_code: country.into(),
                ..Default::default()
            })
        };
        assert!(issues(&bank("GB82 WEST 1234 5698 7654 32", "NWBKGB2L", "GB")).is_empty());
        assert_eq!(issues(&bank("GB82 WEST 1234 5698 7654 33", "", "GB")), ["accountNumber:ibanChecksum"]);
        assert_eq!(issues(&bank("GB82 WEST 1234 5698 7654", "", "GB")), ["accountNumber:ibanInvalid"]);
        assert!(issues(&bank("000123456789", "011000015", "US")).is_empty());
        assert_eq!(issues(&bank("", "011000016", "US")), ["routingNumber:routingInvalid"]);
        assert!(issues(&bank("", "20-00-00", "GB")).is_empty());
        assert!(issues(&bank("", "SBIN0001234", "IN")).is_empty());
        assert_eq!(issues(&bank("12#34", "", "IQ")), ["accountNumber:lettersDigitsOnly"]);
        let VaultPayload::BankAccount(b) = normalize(&bank("gb82west12345698765432", "", "gb")) else { panic!() };
        assert_eq!((b.account_number.as_str(), b.country_code.as_str()), ("GB82 WEST 1234 5698 7654 32", "GB"));
    }

    #[test]
    fn shapes() {
        for good in ["a@b.co", "ali.barznji+qasa@mail.example.iq", "ئالی@نموونە.کورد"] {
            assert!(email_ok(good), "{good}");
        }
        for bad in ["a@b", "a b@c.com", "@b.com", "a@b..com", "a@-b.com", "a@b.c", "a@@b.com"] {
            assert!(!email_ok(bad), "{bad}");
        }
        for good in ["example.com", "https://accounts.google.com/login?x=1", "http://localhost:8080", "192.168.1.1", "androidapp://com.x", "http://[::1]:80/"] {
            assert!(url_ok(good), "{good}");
        }
        for bad in ["not a url", "https://", "example", "http://exa mple.com", "https://example.com:99999", "://x.com"] {
            assert!(!url_ok(bad), "{bad}");
        }
    }

    #[test]
    fn required_and_lenient_fields() {
        assert_eq!(issues(&VaultPayload::Login(LoginFields::default())), ["itemName:required"]);
        assert_eq!(issues(&VaultPayload::SecureNote(SecureNoteFields::default())), ["title:required"]);
        assert_eq!(issues(&VaultPayload::Name(NameFields::default())), ["firstName:required"]);
        assert!(issues(&VaultPayload::Name(NameFields { last_name: "Barznji".into(), ..Default::default() })).is_empty());
        assert_eq!(issues(&VaultPayload::PersonalEmail(PersonalEmailFields::default())), ["email:required"]);
        assert_eq!(issues(&VaultPayload::Company(CompanyFields::default())), ["companyName:required"]);
        assert_eq!(
            issues(&VaultPayload::Website(WebsiteFields::default())),
            ["itemName:required", "websiteURL:required"]
        );
        // Any script, any punctuation in names and addresses.
        let addr = VaultPayload::Address(AddressFields {
            item_name: "Home".into(),
            address: "شەقامی ٦٠ مەتری، نزیک #3".into(),
            zip_code: "44001".into(),
            ..Default::default()
        });
        assert!(issues(&addr).is_empty());
        let login = VaultPayload::Login(LoginFields {
            item_name: "x".into(),
            password: "   ".into(),
            totp: Some("not base32!".into()),
            ..Default::default()
        });
        assert_eq!(issues(&login), ["totp:totpInvalid"]);
        let long = VaultPayload::SecureNote(SecureNoteFields { title: "x".repeat(501), ..Default::default() });
        assert_eq!(issues(&long), ["title:tooLong(500)"]);
    }

    #[test]
    fn normalize_is_idempotent_and_canonical() {
        let p = VaultPayload::Card(CardFields {
            item_name: "Visa".into(),
            number: "4111111111111111".into(),
            security_code: "١٢٣".into(),
            expiry_date: "Nov 6, 2027".into(),
            ..Default::default()
        });
        let once = normalize(&p);
        let VaultPayload::Card(f) = &once else { panic!() };
        assert_eq!((f.number.as_str(), f.security_code.as_str(), f.expiry_date.as_str()), ("4111 1111 1111 1111", "123", "11/27"));
        assert_eq!(normalize(&once), once);
        let name = normalize(&VaultPayload::Name(NameFields { first_name: "A".into(), birth_date: "Nov 6, 2000".into(), ..Default::default() }));
        let VaultPayload::Name(n) = name else { panic!() };
        assert_eq!(n.birth_date, "2000-11-06");
        // Unreadable values are left for validate to report.
        let odd = normalize(&card("4111 1111", "", "whenever"));
        let VaultPayload::Card(f) = odd else { panic!() };
        assert_eq!((f.number.as_str(), f.expiry_date.as_str()), ("4111 1111", "whenever"));
    }
}
