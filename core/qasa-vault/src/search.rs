//! The blind search index. Mirrors iOS `VaultSearchIndex`:
//!
//! - lowercase the whole text;
//! - split on every character outside Unicode Letters, Marks and Numbers (Foundation's
//!   `CharacterSet.alphanumerics`);
//! - for each word, keep the word plus every 2- and 3-character n-gram, counting
//!   characters as extended grapheme clusters (Swift `Character`);
//! - HMAC-SHA256 each token with the search key, as lowercase hex.

use std::collections::BTreeSet;

use hmac::{Hmac, Mac};
use sha2::Sha256;
use unicode_general_category::{get_general_category, GeneralCategory as G};
use unicode_segmentation::UnicodeSegmentation;

const MIN_NGRAM: usize = 2;
const MAX_NGRAM: usize = 3;

fn is_alphanumeric(c: char) -> bool {
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
            | G::DecimalNumber
            | G::LetterNumber
            | G::OtherNumber
    )
}

/// The plaintext tokens for `text`, unique and sorted.
pub fn tokenize(text: &str) -> Vec<String> {
    let lowered = text.to_lowercase();
    let mut tokens = BTreeSet::new();
    for word in lowered.split(|c: char| !is_alphanumeric(c)).filter(|w| !w.is_empty()) {
        tokens.insert(word.to_string());
        let graphemes: Vec<&str> = word.graphemes(true).collect();
        for len in MIN_NGRAM..=MAX_NGRAM {
            for window in graphemes.windows(len) {
                tokens.insert(window.concat());
            }
        }
    }
    tokens.into_iter().collect()
}

/// HMAC-SHA256 of one token, lowercase hex.
pub fn hash_token(token: &str, search_key: &[u8; 32]) -> String {
    let mut mac = Hmac::<Sha256>::new_from_slice(search_key).expect("HMAC takes any key length");
    mac.update(token.as_bytes());
    let digest = mac.finalize().into_bytes();
    digest.iter().map(|b| format!("{b:02x}")).collect()
}

/// Hashed tokens for `text`, in the order of [`tokenize`].
pub fn token_hashes(text: &str, search_key: &[u8; 32]) -> Vec<String> {
    tokenize(text).iter().map(|t| hash_token(t, search_key)).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn words_and_ngrams() {
        assert_eq!(tokenize("Ab"), vec!["ab"]);
        assert_eq!(tokenize("abc"), vec!["ab", "abc", "bc"]);
        assert_eq!(tokenize("A b"), vec!["a", "b"]);
        assert!(tokenize("  -- ").is_empty());
    }

    #[test]
    fn arabic_and_kurdish_are_indexed() {
        assert!(tokenize("فیسبووک").contains(&"فیسبووک".to_string()));
        // A combining mark stays inside its grapheme: "مُ" is one character.
        assert!(tokenize("مُحَمَّد").iter().any(|t| t == "مُحَ"));
    }
}
