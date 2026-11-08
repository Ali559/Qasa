//! Password health: weak and reused logins, Pwned Passwords hits, and the 0–100 score.
//! Mirrors iOS `Services/PasswordHealthAnalyzer.swift`. The breach lookup itself (HIBP)
//! is the caller's job; pass its per-password counts in.

use std::collections::{HashMap, HashSet};

use unicode_general_category::{get_general_category, GeneralCategory as G};
use unicode_segmentation::UnicodeSegmentation;

use crate::payload::LoginFields;

/// One login going into the analysis: its id, list display name (used for sorting) and
/// trimmed password. Mirrors the `(row: VaultListRow, password: String)` tuples.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct HealthLogin {
    pub id: String,
    pub display_name: String,
    pub password: String,
}

/// The analysis result; items are login ids. Mirrors `PasswordHealthReport`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct PasswordHealthReport {
    pub total_passwords: usize,
    pub reused_count: usize,
    pub weak_count: usize,
    pub score: i64,
    /// Ids sharing a password with another login, sorted by display name.
    pub reused_ids: Vec<String>,
    /// Ids with a weak password ([`is_weak`]), sorted by display name.
    pub weak_ids: Vec<String>,
    /// Ids whose password is in Pwned Passwords, most-seen first.
    pub breached_ids: Vec<String>,
    /// Pwned Passwords count per breached id.
    pub breach_counts: HashMap<String, u64>,
    /// The breach check ran and failed (usually offline). Set by the caller; breached
    /// stays empty then.
    pub breach_check_failed: bool,
    /// Free plan: the check never ran and the score has no breach deduction.
    pub breach_locked: bool,
}

impl PasswordHealthReport {
    /// Number of breached logins. Mirrors `breachedCount`.
    pub fn breached_count(&self) -> usize {
        self.breached_ids.len()
    }

    /// No logins: score 100. Mirrors `PasswordHealthReport.empty`.
    pub fn empty() -> Self {
        Self {
            total_passwords: 0,
            reused_count: 0,
            weak_count: 0,
            score: 100,
            reused_ids: Vec::new(),
            weak_ids: Vec::new(),
            breached_ids: Vec::new(),
            breach_counts: HashMap::new(),
            breach_check_failed: false,
            breach_locked: false,
        }
    }
}

const COMMON: [&str; 12] = [
    "password", "password1", "123456", "12345678", "qwerty", "abc123", "letmein", "admin", "welcome", "iloveyou",
    "monkey", "dragon",
];

/// Logins with a non-blank password, trimmed (Unicode whitespace and newlines). The
/// display name is the list name iOS stores (`"Untitled"` when the name is blank).
/// Mirrors `PasswordHealthAnalyzer.vaultLogins()`.
pub fn health_logins<'a, I>(logins: I) -> Vec<HealthLogin>
where
    I: IntoIterator<Item = (&'a str, &'a LoginFields)>,
{
    logins
        .into_iter()
        .filter_map(|(id, fields)| {
            let password = fields.password.trim();
            if password.is_empty() {
                return None;
            }
            let name = fields.item_name.trim();
            Some(HealthLogin {
                id: id.to_string(),
                display_name: if name.is_empty() { "Untitled".into() } else { name.to_string() },
                password: password.to_string(),
            })
        })
        .collect()
}

/// Scores `logins`. `breach_counts` maps a password to its Pwned Passwords count
/// (`None`: not checked yet). `breach_locked` marks a Free-plan report: counts are
/// ignored and nothing is deducted for breaches. Mirrors `PasswordHealthAnalyzer.analyze`.
///
/// Score: 100, −12 per weak login, −10 per reused login, −15 per breached login, and
/// with 5 or more logins −trunc((1 − unique/total) × 20); clamped to 0…100.
pub fn analyze(
    logins: &[HealthLogin],
    breach_counts: Option<&HashMap<String, u64>>,
    breach_locked: bool,
) -> PasswordHealthReport {
    if logins.is_empty() {
        return PasswordHealthReport { breach_locked, ..PasswordHealthReport::empty() };
    }
    let breach_counts = if breach_locked { None } else { breach_counts };

    let mut frequency: HashMap<&str, usize> = HashMap::new();
    for entry in logins {
        *frequency.entry(entry.password.as_str()).or_default() += 1;
    }

    let by_name = |items: Vec<&HealthLogin>| -> Vec<String> {
        let mut items = items;
        // Approximates `localizedCaseInsensitiveCompare` (stable).
        items.sort_by_cached_key(|l| l.display_name.to_lowercase());
        items.into_iter().map(|l| l.id.clone()).collect()
    };
    let reused_ids = by_name(logins.iter().filter(|l| frequency[l.password.as_str()] > 1).collect());
    let weak_ids = by_name(logins.iter().filter(|l| is_weak(&l.password)).collect());

    let mut row_counts: HashMap<String, u64> = HashMap::new();
    if let Some(counts) = breach_counts {
        for entry in logins {
            if let Some(&count) = counts.get(&entry.password) {
                row_counts.insert(entry.id.clone(), count);
            }
        }
    }
    let mut breached: Vec<&HealthLogin> = logins.iter().filter(|l| row_counts.contains_key(&l.id)).collect();
    breached.sort_by(|a, b| row_counts[&b.id].cmp(&row_counts[&a.id]));
    let breached_ids: Vec<String> = breached.into_iter().map(|l| l.id.clone()).collect();

    let reused_count = reused_ids.iter().collect::<HashSet<_>>().len();
    let weak_count = weak_ids.iter().collect::<HashSet<_>>().len();
    let total = logins.len();

    let mut score: i64 = 100;
    score -= weak_count as i64 * 12;
    score -= reused_count as i64 * 10;
    score -= breached_ids.len() as i64 * 15;
    if total >= 5 {
        let unique = logins.iter().map(|l| l.password.as_str()).collect::<HashSet<_>>().len();
        let unique_ratio = unique as f64 / total as f64;
        score -= ((1.0 - unique_ratio) * 20.0) as i64;
    }
    let score = score.clamp(0, 100);

    PasswordHealthReport {
        total_passwords: total,
        reused_count,
        weak_count,
        score,
        reused_ids,
        weak_ids,
        breached_ids,
        breach_counts: row_counts,
        breach_check_failed: false,
        breach_locked,
    }
}

/// Weak: under 10 characters (grapheme clusters), a common password, fewer than two of
/// letters / digits / symbols, or under 12 characters without all three. Mirrors
/// `PasswordHealthAnalyzer.isWeak(_:)`, including Foundation's character sets
/// (letters = L* and M*, digits = Nd, symbol = anything outside L*, M*, N*).
pub fn is_weak(password: &str) -> bool {
    let count = password.graphemes(true).count();
    if count < 10 {
        return true;
    }
    if COMMON.contains(&password.to_lowercase().as_str()) {
        return true;
    }
    let has_letter = password.chars().any(is_letter);
    let has_number = password.chars().any(|c| get_general_category(c) == G::DecimalNumber);
    let has_symbol = password.chars().any(|c| !is_alphanumeric(c));
    let classes = [has_letter, has_number, has_symbol].iter().filter(|b| **b).count();
    if classes < 2 {
        return true;
    }
    count < 12 && classes < 3
}

fn is_letter(c: char) -> bool {
    matches!(
        get_general_category(c),
        G::UppercaseLetter
            | G::LowercaseLetter
            | G::TitlecaseLetter
            | G::ModifierLetter
            | G::OtherLetter
            | G::NonspacingMark
            | G::SpacingMark
            | G::EnclosingMark
    )
}

fn is_alphanumeric(c: char) -> bool {
    is_letter(c) || matches!(get_general_category(c), G::DecimalNumber | G::LetterNumber | G::OtherNumber)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn login(id: &str, password: &str) -> HealthLogin {
        HealthLogin { id: id.into(), display_name: id.into(), password: password.into() }
    }

    #[test]
    fn weak_rules_match_ios() {
        assert!(is_weak("Ab1!xyz9"));
        assert!(is_weak("password1"));
        assert!(is_weak("abcdefghijklmnop"));
        assert!(is_weak("12345678901234"));
        assert!(is_weak("abcdefgh12"));
        assert!(!is_weak("abcdefg1!x"));
        assert!(!is_weak("abcdefghij12"));
        assert!(is_weak("sunshine99"));
        // Common-list match is case-insensitive; length counts grapheme clusters.
        assert!(is_weak("PASSWORD1"));
        assert!(is_weak("e\u{301}e\u{301}e\u{301}e\u{301}1!"));
        assert!(!is_weak("e\u{301}e\u{301}e\u{301}e\u{301}e\u{301}1234!"));
    }

    #[test]
    fn score_matches_ios_for_shared_vault() {
        let logins = vec![
            login("a", "abcdefghij12"),
            login("b", "abcdefghij12"),
            login("c", "short"),
            login("d", "Zq9!vT4#mL2p"),
            login("e", "Pw7$kD3@nX8r"),
        ];
        let report = analyze(&logins, None, false);
        assert_eq!((report.total_passwords, report.weak_count, report.reused_count), (5, 1, 2));
        // 100 - 12 - 20 - Int((1 - 4/5) * 20) where (1 - 0.8) * 20 truncates to 3.
        assert_eq!(report.score, 65);
        assert_eq!(report.reused_ids, vec!["a", "b"]);
        assert_eq!(report.weak_ids, vec!["c"]);
    }

    #[test]
    fn breached_logins_lower_score_by_15_and_sort_by_count() {
        let logins = vec![login("a", "Zq9!vT4#mL2p"), login("b", "Pw7$kD3@nX8r")];
        let counts = HashMap::from([("Zq9!vT4#mL2p".to_string(), 3), ("Pw7$kD3@nX8r".to_string(), 40)]);
        let report = analyze(&logins, Some(&counts), false);
        assert_eq!(report.breached_count(), 2);
        assert_eq!(report.breached_ids, vec!["b", "a"]);
        assert_eq!(report.breach_counts["b"], 40);
        assert_eq!(report.score, 70);
        assert_eq!(analyze(&logins, None, false).breached_count(), 0);
        assert_eq!(analyze(&logins, None, false).score, 100);
        // Free plan: counts ignored, no deduction.
        let locked = analyze(&logins, Some(&counts), true);
        assert_eq!((locked.breached_count(), locked.score, locked.breach_locked), (0, 100, true));
    }

    #[test]
    fn empty_and_blank_logins() {
        assert_eq!(analyze(&[], None, false), PasswordHealthReport::empty());
        assert!(analyze(&[], None, true).breach_locked);
        let fields = [
            LoginFields { item_name: " Mail ".into(), password: "  pw \n".into(), ..Default::default() },
            LoginFields { password: " \t ".into(), ..Default::default() },
            LoginFields { password: "x".into(), ..Default::default() },
        ];
        let logins = health_logins([("1", &fields[0]), ("2", &fields[1]), ("3", &fields[2])]);
        assert_eq!(logins, vec![
            HealthLogin { id: "1".into(), display_name: "Mail".into(), password: "pw".into() },
            HealthLogin { id: "3".into(), display_name: "Untitled".into(), password: "x".into() },
        ]);
    }

    #[test]
    fn lists_sort_case_insensitively_by_name() {
        let mut logins = vec![login("1", "same"), login("2", "same"), login("3", "same")];
        logins[0].display_name = "beta".into();
        logins[1].display_name = "Alpha".into();
        logins[2].display_name = "gamma".into();
        assert_eq!(analyze(&logins, None, false).reused_ids, vec!["2", "1", "3"]);
    }
}
