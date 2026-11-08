//! Password generator. Character sets, defaults and the strength label mirror iOS
//! `Views/Vault/PasswordGeneratorModal.swift` (`PasswordGeneratorModel`); the algorithm
//! and validation mirror Android `vault/crypto/PasswordGenerator.kt` (one character from
//! every enabled class, fill from the pool, Fisher–Yates shuffle), which iOS also uses now.

use rand::rngs::OsRng;
use rand::seq::SliceRandom;
use rand::Rng;

/// Mirrors `uppercaseChars`.
pub const UPPERCASE: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
/// Mirrors `lowercaseChars`.
pub const LOWERCASE: &str = "abcdefghijklmnopqrstuvwxyz";
/// Mirrors `numberChars`.
pub const DIGITS: &str = "0123456789";
/// Mirrors `symbolChars`.
pub const SYMBOLS: &str = "!@#$%^&*()_+-=";

/// The iOS length slider range (`Slider(value:in: 8...32)`), for the UI.
pub const SLIDER_MIN_LENGTH: usize = 8;
/// See [`SLIDER_MIN_LENGTH`].
pub const SLIDER_MAX_LENGTH: usize = 32;
/// iOS default length (`length: Double = 16`).
pub const DEFAULT_LENGTH: usize = 16;
/// Longest password [`generate`] accepts. Mirrors Android `PasswordGenerator.MAX_LENGTH`.
pub const MAX_LENGTH: usize = 256;

/// Which classes to draw from and how long. Defaults mirror `PasswordGeneratorModel`:
/// 16 characters, every class on.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct GeneratorOptions {
    pub length: usize,
    pub uppercase: bool,
    pub lowercase: bool,
    pub digits: bool,
    pub symbols: bool,
}

impl Default for GeneratorOptions {
    fn default() -> Self {
        Self { length: DEFAULT_LENGTH, uppercase: true, lowercase: true, digits: true, symbols: true }
    }
}

impl GeneratorOptions {
    fn classes(&self) -> Vec<&'static str> {
        [(self.uppercase, UPPERCASE), (self.lowercase, LOWERCASE), (self.digits, DIGITS), (self.symbols, SYMBOLS)]
            .into_iter()
            .filter_map(|(on, chars)| on.then_some(chars))
            .collect()
    }
}

/// Why no password could be made. Mirrors Android's `IllegalArgumentException` cases.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GeneratorError {
    /// Length 0 or over [`MAX_LENGTH`].
    LengthOutOfRange,
    /// Every class is off.
    NoClasses,
    /// Shorter than the number of enabled classes, so one of each can't fit.
    TooShort,
}

impl std::fmt::Display for GeneratorError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(match self {
            GeneratorError::LengthOutOfRange => "password length out of range",
            GeneratorError::NoClasses => "no character classes selected",
            GeneratorError::TooShort => "length too short to include every selected class",
        })
    }
}

impl std::error::Error for GeneratorError {}

/// A random password from the OS CSPRNG with at least one character from every enabled
/// class. Mirrors Android `PasswordGenerator.generate` / iOS `regenerate()`.
pub fn generate(options: &GeneratorOptions) -> Result<String, GeneratorError> {
    if options.length == 0 || options.length > MAX_LENGTH {
        return Err(GeneratorError::LengthOutOfRange);
    }
    let classes = options.classes();
    if classes.is_empty() {
        return Err(GeneratorError::NoClasses);
    }
    if options.length < classes.len() {
        return Err(GeneratorError::TooShort);
    }
    let pick = |set: &[u8]| char::from(set[OsRng.gen_range(0..set.len())]);
    let pool: String = classes.concat();
    let mut chars: Vec<char> = classes.iter().map(|c| pick(c.as_bytes())).collect();
    while chars.len() < options.length {
        chars.push(pick(pool.as_bytes()));
    }
    chars.shuffle(&mut OsRng);
    Ok(chars.into_iter().collect())
}

/// The strength bar's label. Mirrors the cases of `PasswordGeneratorModel.strengthLabel`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Strength {
    /// `gen.weak`, 1 of 5 segments, danger colour.
    Weak,
    /// `gen.fair`, 2 of 5 segments, gold.
    Fair,
    /// `gen.strong`, 4 of 5 segments, accent.
    Strong,
    /// `gen.veryStrong`, 5 of 5 segments, accent.
    VeryStrong,
}

impl Strength {
    /// Filled segments out of 5.
    pub fn filled_segments(self) -> u8 {
        match self {
            Strength::Weak => 1,
            Strength::Fair => 2,
            Strength::Strong => 4,
            Strength::VeryStrong => 5,
        }
    }

    /// The iOS localization key.
    pub fn l10n_key(self) -> &'static str {
        match self {
            Strength::Weak => "gen.weak",
            Strength::Fair => "gen.fair",
            Strength::Strong => "gen.strong",
            Strength::VeryStrong => "gen.veryStrong",
        }
    }
}

/// Estimate from the settings, not the password: `length / 4` (floored) plus the number
/// of enabled classes; 0–3 weak, 4–5 fair, 6–7 strong, 8+ very strong. Mirrors
/// `PasswordGeneratorModel.strengthLabel`.
pub fn strength(options: &GeneratorOptions) -> Strength {
    let score = options.length / 4 + options.classes().len();
    match score {
        0..=3 => Strength::Weak,
        4..=5 => Strength::Fair,
        6..=7 => Strength::Strong,
        _ => Strength::VeryStrong,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashSet;

    const ALL: &str = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=";

    fn opts(length: usize, uppercase: bool, lowercase: bool, digits: bool, symbols: bool) -> GeneratorOptions {
        GeneratorOptions { length, uppercase, lowercase, digits, symbols }
    }

    fn len(length: usize) -> GeneratorOptions {
        GeneratorOptions { length, ..Default::default() }
    }

    #[test]
    fn honours_requested_length() {
        for n in [3, 4, 8, 16, 64, 128, 256] {
            let pw = generate(&opts(n, true, true, true, false)).unwrap();
            assert_eq!(pw.chars().count(), n, "length {n}");
        }
        assert_eq!(generate(&opts(1, false, false, true, false)).unwrap().len(), 1);
    }

    #[test]
    fn guarantees_one_character_from_every_enabled_class() {
        for _ in 0..300 {
            let pw = generate(&len(16)).unwrap();
            assert!(pw.chars().any(|c| c.is_ascii_uppercase()));
            assert!(pw.chars().any(|c| c.is_ascii_lowercase()));
            assert!(pw.chars().any(|c| c.is_ascii_digit()));
            assert!(pw.chars().any(|c| SYMBOLS.contains(c)));
        }
    }

    #[test]
    fn excludes_disabled_classes() {
        assert!(generate(&opts(32, false, false, true, false)).unwrap().chars().all(|c| c.is_ascii_digit()));
        assert!(generate(&opts(32, false, false, false, true)).unwrap().chars().all(|c| SYMBOLS.contains(c)));
    }

    #[test]
    fn covers_every_character() {
        let mut seen = HashSet::new();
        for _ in 0..400 {
            seen.extend(generate(&len(8)).unwrap().chars());
        }
        let missing: String = ALL.chars().filter(|c| !seen.contains(c)).collect();
        assert!(missing.is_empty(), "never generated: {missing}");
    }

    #[test]
    fn shuffles_forced_characters_out_of_the_prefix() {
        let mut prefix = HashSet::new();
        for _ in 0..400 {
            prefix.extend(generate(&len(32)).unwrap().chars().take(4));
        }
        let never: String = ALL.chars().filter(|c| !prefix.contains(c)).collect();
        assert!(never.is_empty(), "never in the first 4 slots: {never}");
    }

    #[test]
    fn does_not_repeat() {
        let mut seen = HashSet::new();
        for _ in 0..200 {
            assert!(seen.insert(generate(&len(24)).unwrap()), "collision");
        }
    }

    #[test]
    fn rejects_invalid_configurations() {
        assert_eq!(generate(&len(0)), Err(GeneratorError::LengthOutOfRange));
        assert_eq!(generate(&len(MAX_LENGTH + 1)), Err(GeneratorError::LengthOutOfRange));
        assert_eq!(generate(&opts(8, false, false, false, false)), Err(GeneratorError::NoClasses));
        assert_eq!(generate(&len(3)), Err(GeneratorError::TooShort));
        assert_eq!(generate(&len(4)).unwrap().len(), 4);
        assert_eq!(generate(&GeneratorOptions::default()).unwrap().len(), 16);
    }

    #[test]
    fn strength_label_matches_ios() {
        assert_eq!(strength(&GeneratorOptions::default()), Strength::VeryStrong); // 4 + 4
        assert_eq!(strength(&opts(8, true, false, false, false)), Strength::Weak); // 2 + 1
        assert_eq!(strength(&opts(8, true, true, false, false)), Strength::Fair); // 2 + 2
        assert_eq!(strength(&opts(11, true, true, true, false)), Strength::Fair); // 2 + 3
        assert_eq!(strength(&opts(12, true, true, true, false)), Strength::Strong); // 3 + 3
        assert_eq!(strength(&opts(15, true, true, true, true)), Strength::Strong); // 3 + 4
        assert_eq!(strength(&opts(32, false, false, false, false)), Strength::VeryStrong); // 8 + 0
        assert_eq!(Strength::Strong.filled_segments(), 4);
    }
}
