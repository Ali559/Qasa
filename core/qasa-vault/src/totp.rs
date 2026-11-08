//! Time-based one-time codes (RFC 6238). Mirrors iOS `SharedAutofill/TOTP.swift`.
//!
//! A login stores one normalised `otpauth://totp/...` URI (`LoginFields::totp`). The URI
//! this module produces must be byte-identical to the one iOS stores for the same input,
//! so the parts iOS delegates to Foundation's `URLComponents` are re-implemented here:
//! RFC 3986 splitting, the strict parse, and the fallback that percent-encodes every
//! component (including existing `%`) when the string has any invalid character.

use hmac::{Hmac, Mac};
use sha1::Sha1;
use sha2::{Sha256, Sha512};

/// The HMAC hash. Mirrors `TOTP.Algorithm` in `TOTP.swift`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Algorithm {
    Sha1,
    Sha256,
    Sha512,
}

impl Algorithm {
    /// The URI spelling (`SHA1`, `SHA256`, `SHA512`). Mirrors `Algorithm.rawValue`.
    pub fn as_str(self) -> &'static str {
        match self {
            Algorithm::Sha1 => "SHA1",
            Algorithm::Sha256 => "SHA256",
            Algorithm::Sha512 => "SHA512",
        }
    }

    fn from_raw(raw: &str) -> Option<Self> {
        match raw {
            "SHA1" => Some(Algorithm::Sha1),
            "SHA256" => Some(Algorithm::Sha256),
            "SHA512" => Some(Algorithm::Sha512),
            _ => None,
        }
    }
}

/// Why a setup key or link was refused. Mirrors `TOTP.ParseError` in `TOTP.swift`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ParseError {
    /// Not an `otpauth://` link at all (a scanned QR for something else).
    NotOtpAuth,
    /// An otpauth link or key Qasa can't use: hotp, bad base32, too short, out-of-range parameters.
    Invalid,
}

impl std::fmt::Display for ParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            ParseError::NotOtpAuth => "not an otpauth link",
            ParseError::Invalid => "invalid 2FA setup key",
        })
    }
}

impl std::error::Error for ParseError {}

/// A parsed TOTP setup. Mirrors the `TOTP` struct in `TOTP.swift`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Totp {
    /// Normalised URI, the value saved on the login.
    pub uri: String,
    pub secret: Vec<u8>,
    pub algorithm: Algorithm,
    pub digits: u32,
    pub period: u32,
    pub issuer: Option<String>,
    /// Label without the issuer prefix, usually the account email.
    pub account_name: Option<String>,
}

/// Minimum decoded secret length. Mirrors `TOTP.minSecretBytes`.
pub const MIN_SECRET_BYTES: usize = 10;
/// Allowed `digits`. Mirrors `TOTP.digitRange`.
pub const DIGIT_RANGE: std::ops::RangeInclusive<u32> = 6..=8;
/// Allowed `period` in seconds. Mirrors `TOTP.periodRange`.
pub const PERIOD_RANGE: std::ops::RangeInclusive<u32> = 15..=120;

const BASE32_ALPHABET: &[u8; 32] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

impl Totp {
    /// Pasted text: an `otpauth://totp/...` link or a bare base32 setup key.
    /// Mirrors `TOTP.parse(_:)`.
    pub fn parse(input: &str) -> Result<Totp, ParseError> {
        let trimmed = input.trim();
        if trimmed.is_empty() {
            return Err(ParseError::Invalid);
        }
        if trimmed.contains("://") || trimmed.to_lowercase().starts_with("otpauth:") {
            return parse_uri(trimmed);
        }
        let cleaned = clean_base32(trimmed);
        let secret = decode_secret(&cleaned)?;
        Ok(Totp {
            uri: format!("otpauth://totp/Qasa?secret={cleaned}"),
            secret,
            algorithm: Algorithm::Sha1,
            digits: 6,
            period: 30,
            issuer: None,
            account_name: None,
        })
    }

    /// A scanned QR code. Only otpauth links count; anything else is `NotOtpAuth`.
    /// Mirrors `TOTP.parseScanned(_:)`.
    pub fn parse_scanned(payload: &str) -> Result<Totp, ParseError> {
        let trimmed = payload.trim();
        if !trimmed.to_lowercase().starts_with("otpauth://") {
            return Err(ParseError::NotOtpAuth);
        }
        parse_uri(trimmed)
    }

    /// Reads a stored `LoginFields::totp` value. `None`, empty or unreadable means no
    /// 2FA code. Mirrors `TOTP.init?(stored:)`.
    pub fn from_stored(stored: Option<&str>) -> Option<Totp> {
        let stored = stored?;
        if stored.trim().is_empty() {
            return None;
        }
        Totp::parse(stored).ok()
    }

    /// Code for the period that contains `unix_time` (seconds; negative clamps to 0).
    /// Mirrors `TOTP.code(at:)`.
    pub fn code_at(&self, unix_time: i64) -> String {
        code(&self.secret, unix_time.max(0) as u64, self.period, self.digits, self.algorithm)
    }

    /// Whole seconds left before the code changes. Mirrors `TOTP.secondsRemaining(at:)`.
    pub fn seconds_remaining(&self, unix_time: i64) -> u32 {
        let now = unix_time.max(0) as u64;
        self.period - (now % u64::from(self.period)) as u32
    }
}

/// The issuer of a stored `otpauth://` URI, for the search index. The secret never goes
/// into the index. Mirrors `TOTP(stored:)?.issuer` as used by iOS `searchableStrings`.
pub fn issuer_of(stored: &str) -> Option<String> {
    Totp::from_stored(Some(stored))?.issuer
}

/// RFC 6238: counter = floor(time / period), HMAC over the big-endian counter, RFC 4226
/// dynamic truncation, mod 10^digits, zero-padded. Mirrors static `TOTP.code(secret:…)`.
pub fn code(secret: &[u8], unix_time: u64, period: u32, digits: u32, algorithm: Algorithm) -> String {
    let message = (unix_time / u64::from(period.max(1))).to_be_bytes();
    let mac: Vec<u8> = match algorithm {
        Algorithm::Sha1 => hmac_bytes::<Hmac<Sha1>>(secret, &message),
        Algorithm::Sha256 => hmac_bytes::<Hmac<Sha256>>(secret, &message),
        Algorithm::Sha512 => hmac_bytes::<Hmac<Sha512>>(secret, &message),
    };
    let offset = usize::from(mac[mac.len() - 1] & 0x0F);
    let binary = (u32::from(mac[offset] & 0x7F) << 24)
        | (u32::from(mac[offset + 1]) << 16)
        | (u32::from(mac[offset + 2]) << 8)
        | u32::from(mac[offset + 3]);
    let modulus = 10u64.saturating_pow(digits);
    let value = (u64::from(binary) % modulus).to_string();
    let pad = (digits as usize).saturating_sub(value.len());
    "0".repeat(pad) + &value
}

fn hmac_bytes<M: Mac + hmac::digest::KeyInit>(key: &[u8], message: &[u8]) -> Vec<u8> {
    let mut mac = <M as hmac::digest::KeyInit>::new_from_slice(key).expect("HMAC takes any key length");
    mac.update(message);
    mac.finalize().into_bytes().to_vec()
}

/// "123 456" / "1234 5678": two groups for reading aloud or typing. Mirrors `TOTP.grouped(_:)`.
pub fn grouped(code: &str) -> String {
    let chars: Vec<char> = code.chars().collect();
    if chars.len() <= 4 {
        return code.to_string();
    }
    let split = chars.len() / 2;
    let head: String = chars[..split].iter().collect();
    let tail: String = chars[split..].iter().collect();
    format!("{head} {tail}")
}

/// Spaces and dashes out, uppercase, no `=` padding. Mirrors `TOTP.cleanBase32(_:)`.
pub fn clean_base32(input: &str) -> String {
    input
        .chars()
        .filter(|c| !c.is_whitespace() && *c != '-' && *c != '=')
        .collect::<String>()
        .to_uppercase()
}

/// RFC 4648 base32 without padding; `None` on any character outside A–Z and 2–7.
/// Trailing bits that don't fill a byte are dropped. Mirrors `TOTP.base32Decode(_:)`.
pub fn base32_decode(cleaned: &str) -> Option<Vec<u8>> {
    let mut bytes = Vec::with_capacity(cleaned.len() * 5 / 8);
    let mut buffer: u32 = 0;
    let mut bits = 0u32;
    for ch in cleaned.chars() {
        let value = BASE32_ALPHABET.iter().position(|&a| char::from(a) == ch)? as u32;
        buffer = ((buffer << 5) | value) & 0xFFFF;
        bits += 5;
        if bits >= 8 {
            bits -= 8;
            bytes.push(((buffer >> bits) & 0xFF) as u8);
        }
    }
    Some(bytes)
}

fn decode_secret(cleaned: &str) -> Result<Vec<u8>, ParseError> {
    match base32_decode(cleaned) {
        Some(data) if data.len() >= MIN_SECRET_BYTES => Ok(data),
        _ => Err(ParseError::Invalid),
    }
}

// MARK: - URI parsing (Foundation `URLComponents` semantics)

struct Components {
    scheme: Option<String>,
    host: Option<String>,
    /// Percent-encoded path as `URLComponents.percentEncodedPath` reports it.
    path: String,
    /// `percentEncodedQueryItems`: split on `&`, then on the first `=`; no `=` means no value.
    query_items: Vec<(String, Option<String>)>,
}

fn parse_uri(raw: &str) -> Result<Totp, ParseError> {
    // Some sites put bare spaces in the label or key. iOS encodes those itself first.
    let spaced = raw.replace(' ', "%20");
    let components = url_components(&spaced).ok_or(ParseError::NotOtpAuth)?;
    if components.scheme.as_deref().map(str::to_ascii_lowercase).as_deref() != Some("otpauth") {
        return Err(ParseError::NotOtpAuth);
    }
    if components.host.as_deref().map(str::to_lowercase).as_deref() != Some("totp") {
        return Err(ParseError::Invalid);
    }

    let items = &components.query_items;
    let param = |name: &str| -> Option<String> {
        let item = items.iter().find(|(n, _)| n.to_lowercase() == name)?;
        item.1.as_deref().and_then(percent_decode)
    };

    let raw_secret = param("secret").ok_or(ParseError::Invalid)?;
    let cleaned = clean_base32(&raw_secret);
    let secret = decode_secret(&cleaned)?;

    let mut algorithm = Algorithm::Sha1;
    if let Some(value) = param("algorithm") {
        algorithm = Algorithm::from_raw(&value.to_uppercase()).ok_or(ParseError::Invalid)?;
    }
    let mut digits = 6;
    if let Some(value) = param("digits") {
        digits = parse_in_range(&value, &DIGIT_RANGE)?;
    }
    let mut period = 30;
    if let Some(value) = param("period") {
        period = parse_in_range(&value, &PERIOD_RANGE)?;
    }

    let decoded_path = percent_decode(&components.path).unwrap_or_default();
    let label = decoded_path.trim_start_matches('/');
    let mut issuer = param("issuer").filter(|s| !s.is_empty());
    let mut account = (!label.is_empty()).then(|| label.to_string());
    if let Some(colon) = label.find(':') {
        let prefix = trim_whitespaces(&label[..colon]);
        let rest = trim_whitespaces(&label[colon + 1..]);
        if issuer.is_none() && !prefix.is_empty() {
            issuer = Some(prefix.to_string());
        }
        account = (!rest.is_empty()).then(|| rest.to_string());
    }

    // Keep the label and every parameter as given; only the secret is rewritten.
    let path = if components.path.is_empty() { "/" } else { components.path.as_str() };
    let query = items
        .iter()
        .map(|(name, value)| {
            if name.to_lowercase() == "secret" {
                format!("{name}={cleaned}")
            } else {
                match value {
                    Some(v) => format!("{name}={v}"),
                    None => name.clone(),
                }
            }
        })
        .collect::<Vec<_>>()
        .join("&");

    Ok(Totp {
        uri: format!("otpauth://totp{path}?{query}"),
        secret,
        algorithm,
        digits,
        period,
        issuer,
        account_name: account,
    })
}

/// Swift `Int(_:)` then a range check: an optional sign and ASCII digits, nothing else.
fn parse_in_range(value: &str, range: &std::ops::RangeInclusive<u32>) -> Result<u32, ParseError> {
    let parsed: i64 = value.parse().map_err(|_| ParseError::Invalid)?;
    u32::try_from(parsed).ok().filter(|v| range.contains(v)).ok_or(ParseError::Invalid)
}

/// `trimmingCharacters(in: .whitespaces)`: Unicode `Zs` plus tab, not newlines.
pub(crate) fn trim_whitespaces(s: &str) -> &str {
    s.trim_matches(|c: char| {
        c.is_whitespace() && !matches!(c, '\n' | '\u{0B}' | '\u{0C}' | '\r' | '\u{85}' | '\u{2028}' | '\u{2029}')
    })
}

/// Foundation `removingPercentEncoding`: `None` for a malformed escape or non-UTF-8 bytes.
fn percent_decode(s: &str) -> Option<String> {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            let hi = hex_value(*bytes.get(i + 1)?)?;
            let lo = hex_value(*bytes.get(i + 2)?)?;
            out.push(hi << 4 | lo);
            i += 3;
        } else {
            out.push(bytes[i]);
            i += 1;
        }
    }
    String::from_utf8(out).ok()
}

fn hex_value(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

fn is_unreserved(b: u8) -> bool {
    b.is_ascii_alphanumeric() || matches!(b, b'-' | b'.' | b'_' | b'~')
}

fn is_sub_delim(b: u8) -> bool {
    matches!(b, b'!' | b'$' | b'&' | b'\'' | b'(' | b')' | b'*' | b'+' | b',' | b';' | b'=')
}

fn path_allowed(b: u8) -> bool {
    is_unreserved(b) || is_sub_delim(b) || matches!(b, b':' | b'@' | b'/')
}

fn query_allowed(b: u8) -> bool {
    path_allowed(b) || b == b'?'
}

fn host_allowed(b: u8) -> bool {
    is_unreserved(b) || is_sub_delim(b)
}

fn userinfo_allowed(b: u8) -> bool {
    is_unreserved(b) || is_sub_delim(b) || b == b':'
}

/// Every byte allowed, or a well-formed `%XX` escape.
fn is_valid_component(s: &str, allowed: fn(u8) -> bool) -> bool {
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' {
            let ok = bytes.get(i + 1).copied().and_then(hex_value).is_some()
                && bytes.get(i + 2).copied().and_then(hex_value).is_some();
            if !ok {
                return false;
            }
            i += 3;
        } else if allowed(bytes[i]) {
            i += 1;
        } else {
            return false;
        }
    }
    true
}

/// `addingPercentEncoding`: every byte outside `allowed` (so `%` too) becomes `%XX`.
fn percent_encode(s: &str, allowed: fn(u8) -> bool) -> String {
    let mut out = String::with_capacity(s.len());
    for &b in s.as_bytes() {
        if allowed(b) {
            out.push(char::from(b));
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    out
}

fn is_valid_scheme(s: &str) -> bool {
    let mut chars = s.chars();
    matches!(chars.next(), Some(c) if c.is_ascii_alphabetic())
        && chars.all(|c| c.is_ascii_alphanumeric() || matches!(c, '+' | '-' | '.'))
}

/// `URLComponents(string:encodingInvalidCharacters: false) ?? URLComponents(string:)`.
fn url_components(s: &str) -> Option<Components> {
    // RFC 3986 appendix B: ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
    let mut rest = s;
    let mut scheme = None;
    if let Some(colon) = rest.find(':') {
        let candidate = &rest[..colon];
        if !candidate.is_empty() && !candidate.contains(['/', '?', '#']) {
            if !is_valid_scheme(candidate) {
                return None;
            }
            scheme = Some(candidate.to_string());
            rest = &rest[colon + 1..];
        }
    }
    let mut authority = None;
    if let Some(after) = rest.strip_prefix("//") {
        let end = after.find(['/', '?', '#']).unwrap_or(after.len());
        authority = Some(&after[..end]);
        rest = &after[end..];
    }
    let (before_fragment, fragment) = match rest.find('#') {
        Some(i) => (&rest[..i], Some(&rest[i + 1..])),
        None => (rest, None),
    };
    let (path, query) = match before_fragment.find('?') {
        Some(i) => (&before_fragment[..i], Some(&before_fragment[i + 1..])),
        None => (before_fragment, None),
    };

    let (userinfo, host) = match authority {
        Some(auth) => {
            let (userinfo, host_port) = match auth.rfind('@') {
                Some(i) => (Some(&auth[..i]), &auth[i + 1..]),
                None => (None, auth),
            };
            let host = match host_port.rfind(':') {
                Some(i) if !host_port.starts_with('[') || host_port[..i].ends_with(']') => {
                    let port = &host_port[i + 1..];
                    if !port.bytes().all(|b| b.is_ascii_digit()) {
                        return None;
                    }
                    &host_port[..i]
                }
                _ => host_port,
            };
            (userinfo, Some(host))
        }
        None => (None, None),
    };

    let strict = userinfo.is_none_or(|u| is_valid_component(u, userinfo_allowed))
        && host.is_none_or(|h| is_valid_component(h, host_allowed))
        && is_valid_component(path, path_allowed)
        && query.is_none_or(|q| is_valid_component(q, query_allowed))
        && fragment.is_none_or(|f| is_valid_component(f, query_allowed));

    let (host, path, query) = if strict {
        (host.map(|h| percent_decode(h).unwrap_or_else(|| h.to_string())), path.to_string(), query.map(str::to_string))
    } else {
        // The fallback re-encodes each component, `%` included.
        (
            host.map(str::to_string),
            percent_encode(path, path_allowed),
            query.map(|q| percent_encode(q, query_allowed)),
        )
    };

    let query_items = match query.as_deref() {
        None | Some("") => Vec::new(),
        Some(q) => q
            .split('&')
            .map(|pair| match pair.find('=') {
                Some(i) => (pair[..i].to_string(), Some(pair[i + 1..].to_string())),
                None => (pair.to_string(), None),
            })
            .collect(),
    };

    Some(Components { scheme, host, path, query_items })
}

#[cfg(test)]
mod tests {
    use super::*;

    const TIMES: [u64; 6] = [59, 1_111_111_109, 1_111_111_111, 1_234_567_890, 2_000_000_000, 20_000_000_000];
    const SHA1_SECRET: &[u8] = b"12345678901234567890";
    const SHA256_SECRET: &[u8] = b"12345678901234567890123456789012";
    const SHA512_SECRET: &[u8] = b"1234567890123456789012345678901234567890123456789012345678901234";

    fn base32(data: &[u8]) -> String {
        let mut out = String::new();
        let mut buffer: u32 = 0;
        let mut bits = 0;
        for &byte in data {
            buffer = (buffer << 8) | u32::from(byte);
            bits += 8;
            while bits >= 5 {
                bits -= 5;
                out.push(char::from(BASE32_ALPHABET[((buffer >> bits) & 0x1F) as usize]));
            }
        }
        if bits > 0 {
            out.push(char::from(BASE32_ALPHABET[((buffer << (5 - bits)) & 0x1F) as usize]));
        }
        out
    }

    #[test]
    fn rfc6238_vectors() {
        let cases: [(Algorithm, &[u8], [&str; 6]); 3] = [
            (Algorithm::Sha1, SHA1_SECRET, ["94287082", "07081804", "14050471", "89005924", "69279037", "65353130"]),
            (Algorithm::Sha256, SHA256_SECRET, ["46119246", "68084774", "67062674", "91819424", "90698825", "77737706"]),
            (Algorithm::Sha512, SHA512_SECRET, ["90693936", "25091201", "99943326", "93441116", "38618901", "47863826"]),
        ];
        for (algorithm, secret, expected) in cases {
            for (time, want) in TIMES.iter().zip(expected) {
                assert_eq!(code(secret, *time, 30, 8, algorithm), want, "{algorithm:?} at {time}");
            }
        }
    }

    #[test]
    fn parsed_uri_generates_rfc_vectors() {
        let uri = format!("otpauth://totp/Test?secret={}&algorithm=SHA256&digits=8", base32(SHA256_SECRET));
        let totp = Totp::parse(&uri).unwrap();
        assert_eq!(totp.code_at(59), "46119246");
        assert_eq!(totp.code_at(1_234_567_890), "91819424");
    }

    #[test]
    fn bare_secret_with_spaces_lowercase_and_padding() {
        let totp = Totp::parse("  jbsw y3dp-ehpk 3pxp====  ").unwrap();
        assert_eq!(totp.uri, "otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP");
        assert_eq!(totp.algorithm, Algorithm::Sha1);
        assert_eq!((totp.digits, totp.period, totp.secret.len()), (6, 30, 10));
    }

    #[test]
    fn android_bare_secret_is_cleaned_and_wrapped() {
        let totp = Totp::parse("gezd gnbv-gy3t qojq gezd gnbv gy3t qojq====").unwrap();
        assert_eq!(totp.uri, "otpauth://totp/Qasa?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        let again = Totp::parse(&totp.uri).unwrap();
        assert_eq!((again.algorithm, again.digits, again.period), (Algorithm::Sha1, 6, 30));
        assert_eq!(again.code_at(59), "287082");
    }

    #[test]
    fn full_uri_with_issuer_sha256_eight_digits_sixty_seconds() {
        let totp = Totp::parse(
            "OTPAUTH://TOTP/ACME%20Co:jane@example.com?secret=jbsw y3dp ehpk 3pxp&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60",
        )
        .unwrap();
        assert_eq!(totp.algorithm, Algorithm::Sha256);
        assert_eq!((totp.digits, totp.period), (8, 60));
        assert_eq!(totp.issuer.as_deref(), Some("ACME Co"));
        assert_eq!(totp.account_name.as_deref(), Some("jane@example.com"));
        assert_eq!(
            totp.uri,
            "otpauth://totp/ACME%20Co:jane@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ACME%20Co&algorithm=SHA256&digits=8&period=60"
        );
        assert_eq!(totp.code_at(1_700_000_000).len(), 8);
        assert_eq!(Totp::parse(&totp.uri).unwrap(), totp);
    }

    #[test]
    fn android_full_uri_keeps_label_and_parameters() {
        let raw = "OTPAUTH://TOTP/GitHub:ali%40example.com?secret=gezd-gnbv gy3t qojq gezd gnbv gy3t qojq&issuer=GitHub&algorithm=SHA256&digits=8&period=60";
        let totp = Totp::parse(raw).unwrap();
        assert_eq!(
            totp.uri,
            "otpauth://totp/GitHub:ali%40example.com?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=GitHub&algorithm=SHA256&digits=8&period=60"
        );
        assert_eq!((totp.algorithm, totp.digits, totp.period), (Algorithm::Sha256, 8, 60));
        assert_eq!(totp.issuer.as_deref(), Some("GitHub"));
        // iOS splits the label; Android keeps it whole ("GitHub:ali@example.com").
        assert_eq!(totp.account_name.as_deref(), Some("ali@example.com"));
    }

    #[test]
    fn issuer_falls_back_to_label_prefix() {
        let totp = Totp::parse("otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP").unwrap();
        assert_eq!(totp.issuer.as_deref(), Some("GitHub"));
        assert_eq!(totp.account_name.as_deref(), Some("dev"));
        assert_eq!(issuer_of("otpauth://totp/GitHub:dev?secret=JBSWY3DPEHPK3PXP").as_deref(), Some("GitHub"));
        assert_eq!(issuer_of("otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP"), None);
        assert_eq!(issuer_of(""), None);
    }

    #[test]
    fn hotp_rejected() {
        let hotp = "otpauth://hotp/ACME?secret=JBSWY3DPEHPK3PXP&counter=1";
        assert_eq!(Totp::parse(hotp), Err(ParseError::Invalid));
        assert_eq!(Totp::parse_scanned(hotp), Err(ParseError::Invalid));
    }

    #[test]
    fn bad_character_rejected() {
        assert_eq!(Totp::parse("JBSWY3DPEHPK3PX1"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("otpauth://totp/A?secret=JBSWY3DP!HPK3PXP"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("GEZDGNBVGY3TQOJ0GEZDGNBVGY3TQOJQ"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("otpauth://totp/X?secret=GEZDGNBVGY3TQOJ1"), Err(ParseError::Invalid));
    }

    #[test]
    fn too_short_secret_rejected() {
        assert_eq!(Totp::parse("JBSWY3DP"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("otpauth://totp/A?secret=JBSWY3DP"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("GEZDGNBVGY3TQOJ"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("otpauth://totp/X?secret=GEZDGNBVGY3TQOJ"), Err(ParseError::Invalid));
        assert!(Totp::parse("GEZDGNBVGY3TQOJQ").is_ok());
    }

    #[test]
    fn out_of_range_parameters_rejected() {
        let base = "otpauth://totp/A?secret=JBSWY3DPEHPK3PXP";
        assert!(Totp::parse(base).is_ok());
        for suffix in ["&digits=9", "&digits=5", "&period=10", "&period=121", "&algorithm=MD5"] {
            assert_eq!(Totp::parse(&format!("{base}{suffix}")), Err(ParseError::Invalid), "{suffix}");
        }
        assert_eq!(Totp::parse("otpauth://totp/A?issuer=A"), Err(ParseError::Invalid));
    }

    #[test]
    fn non_otpauth_qr_rejected() {
        assert_eq!(Totp::parse_scanned("https://example.com/login"), Err(ParseError::NotOtpAuth));
        assert_eq!(Totp::parse_scanned("JBSWY3DPEHPK3PXP"), Err(ParseError::NotOtpAuth));
        assert_eq!(Totp::parse("https://example.com/?secret=JBSWY3DPEHPK3PXP"), Err(ParseError::NotOtpAuth));
        assert_eq!(
            Totp::parse_scanned("https://example.com/setup?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"),
            Err(ParseError::NotOtpAuth)
        );
    }

    #[test]
    fn stored_empty_means_no_code() {
        assert!(Totp::from_stored(None).is_none());
        assert!(Totp::from_stored(Some("")).is_none());
        assert!(Totp::from_stored(Some("  ")).is_none());
        assert!(Totp::from_stored(Some("otpauth://totp/Qasa?secret=JBSWY3DPEHPK3PXP")).is_some());
    }

    #[test]
    fn seconds_remaining_and_grouping() {
        let totp = Totp::parse("JBSWY3DPEHPK3PXP").unwrap();
        assert_eq!(totp.seconds_remaining(59), 1);
        assert_eq!(totp.seconds_remaining(60), 30);
        assert_eq!(grouped("123456"), "123 456");
        assert_eq!(grouped("12345678"), "1234 5678");
        // iOS groups anything over 4 characters (Android: 6 or more).
        assert_eq!(grouped("12345"), "12 345");
        assert_eq!(grouped("1234"), "1234");
    }

    #[test]
    fn uri_normalisation_follows_url_components() {
        // No path: iOS writes "/".
        assert_eq!(Totp::parse("otpauth://totp?secret=jbswy3dpehpk3pxp").unwrap().uri, "otpauth://totp/?secret=JBSWY3DPEHPK3PXP");
        // Parameter names keep their case and order; valueless and empty items survive; fragment dropped.
        assert_eq!(
            Totp::parse("otpauth://totp/A?SECRET=jbswy3dpehpk3pxp&&flag&issuer=#frag").unwrap().uri,
            "otpauth://totp/A?SECRET=JBSWY3DPEHPK3PXP&&flag&issuer="
        );
        // Any invalid character makes Foundation re-encode every component, `%` included.
        let quoted = Totp::parse("otpauth://totp/Bob's \"Shop\"?secret=JBSWY3DPEHPK3PXP&issuer=A%20B").unwrap();
        assert_eq!(quoted.uri, "otpauth://totp/Bob's%2520%22Shop%22?secret=JBSWY3DPEHPK3PXP&issuer=A%2520B");
        assert_eq!(quoted.issuer.as_deref(), Some("A%20B"));
        // An undecodable value reads as absent, so the default applies.
        assert_eq!(Totp::parse("otpauth://totp/A?secret=JBSWY3DPEHPK3PXP&algorithm=%FF").unwrap().algorithm, Algorithm::Sha1);
        // Swift Int() takes a sign but not whitespace.
        assert_eq!(Totp::parse("otpauth://totp/A?secret=JBSWY3DPEHPK3PXP&digits=+8").unwrap().digits, 8);
        assert_eq!(Totp::parse("otpauth://totp/A?secret=JBSWY3DPEHPK3PXP&digits=%208"), Err(ParseError::Invalid));
        assert_eq!(Totp::parse("otpauth:totp?secret=JBSWY3DPEHPK3PXP"), Err(ParseError::Invalid));
    }
}
