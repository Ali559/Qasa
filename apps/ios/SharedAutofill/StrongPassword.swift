import Foundation

/// The shared generator's defaults (`core/qasa-vault/src/generator.rs`, the app's
/// `PasswordGeneratorModel`, Android `PasswordGenerator`): 16 characters from upper, lower,
/// digits and `!@#$%^&*()_+-=`, one of each class guaranteed, shuffled. Used where the
/// AutoFill extension suggests a password for a new-password field (design rule 6c).
///
/// A site's Apple `passwordrules` (`minlength: 20; required: lower, upper; allowed: digit,
/// [-_]`) narrow it: the length moves into `minlength…maxlength`, and only the classes the
/// rules name are used, each `required` one guaranteed.
enum StrongPassword {
    static let defaultLength = 16
    static let maxLength = 128

    static let upper = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
    static let lower = Array("abcdefghijklmnopqrstuvwxyz")
    static let digits = Array("0123456789")
    static let symbols = Array("!@#$%^&*()_+-=")

    /// What `passwordrules` asks for, reduced to what the generator can do.
    struct Rules: Equatable {
        var minLength: Int?
        var maxLength: Int?
        /// Each set must appear at least once.
        var required: [[Character]] = []
        /// Further characters that may appear.
        var allowed: [[Character]] = []

        var isEmpty: Bool { required.isEmpty && allowed.isEmpty }
    }

    static func generate(rules text: String? = nil) -> String {
        generate(rules: parse(text))
    }

    static func generate(rules: Rules) -> String {
        var classes = rules.isEmpty ? [upper, lower, digits, symbols] : rules.required + rules.allowed
        classes = classes.map { Array(Set($0)).sorted() }.filter { !$0.isEmpty }
        if classes.isEmpty { classes = [upper, lower, digits, symbols] }
        let guaranteed = rules.isEmpty ? classes : rules.required.filter { !$0.isEmpty }
        let pool = Array(Set(classes.joined())).sorted()

        var length = max(defaultLength, rules.minLength ?? 0)
        if let max = rules.maxLength { length = min(length, max) }
        length = min(max(length, guaranteed.count, 1), maxLength)

        var chars = guaranteed.compactMap { $0.randomElement() }
        while chars.count < length, let c = pool.randomElement() { chars.append(c) }
        return String(chars.prefix(length).shuffled())
    }

    /// Parses Apple's `passwordrules` syntax. Unknown properties and classes are ignored.
    static func parse(_ text: String?) -> Rules {
        var rules = Rules()
        guard let text, !text.isEmpty else { return rules }
        for part in text.split(separator: ";") {
            let pieces = part.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            guard pieces.count == 2 else { continue }
            let key = pieces[0].lowercased()
            let value = pieces[1]
            switch key {
            case "minlength": rules.minLength = Int(value)
            case "maxlength": rules.maxLength = Int(value)
            case "required": rules.required.append(contentsOf: classes(in: value))
            case "allowed": rules.allowed.append(contentsOf: classes(in: value))
            default: continue
            }
        }
        // `required: upper, lower` means "one from either"; guaranteeing each is stricter and
        // still satisfies the site.
        return rules
    }

    /// `upper, lower, digit, special, ascii-printable, [abc]` → character sets.
    private static func classes(in value: String) -> [[Character]] {
        var out: [[Character]] = []
        var rest = Substring(value)
        while !rest.isEmpty {
            rest = rest.drop { $0 == "," || $0 == " " }
            if rest.first == "[" {
                let body = rest.dropFirst().prefix { $0 != "]" }
                out.append(Array(body).filter { $0.isASCII && !$0.isWhitespace })
                rest = rest.dropFirst(body.count + 2)
                continue
            }
            let word = rest.prefix { $0 != "," }
            rest = rest.dropFirst(word.count)
            switch word.trimmingCharacters(in: .whitespaces).lowercased() {
            case "upper": out.append(upper)
            case "lower": out.append(lower)
            case "digit": out.append(digits)
            case "special": out.append(symbols)
            case "ascii-printable", "unicode": out.append(contentsOf: [upper, lower, digits, symbols])
            default: continue
            }
        }
        return out
    }
}
