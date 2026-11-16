import Foundation

/// The shared generator's defaults (`core/qasa-vault/src/generator.rs`, the app's
/// `PasswordGeneratorModel`, Android `PasswordGenerator`): 16 characters from upper, lower,
/// digits and `!@#$%^&*()_+-=`, one of each class guaranteed, shuffled. Used where the
/// AutoFill extension suggests a password for a new-password field (design rule 6c).
///
/// A site's Apple `passwordrules` (`minlength: 20; required: lower, upper; allowed: digit,
/// [-_]`) narrow it: the length moves into `minlength…maxlength`, and only the classes the
/// rules name are used, each `required` one guaranteed.
nonisolated enum StrongPassword {
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
        /// No character repeats more than this many times in a row.
        var maxConsecutive: Int?
        /// Each set must appear at least once.
        var required: [[Character]] = []
        /// Further characters that may appear.
        var allowed: [[Character]] = []

        /// No character restriction (lengths may still be set).
        var isEmpty: Bool { required.isEmpty && allowed.isEmpty }

        /// Every character a password under these rules may use.
        var characters: Set<Character> {
            isEmpty ? Set(upper + lower + digits + symbols) : Set((required + allowed).joined())
        }

        /// Whether a symbol (anything but a letter or digit) may appear.
        var allowsSymbols: Bool { characters.contains { !$0.isLetter && !$0.isNumber } }

        /// A password must satisfy both: the tighter lengths, the shared characters, and every
        /// required class (narrowed to the shared characters).
        func combined(with other: Rules) -> Rules {
            var out = Rules()
            out.minLength = [minLength, other.minLength].compactMap { $0 }.max()
            out.maxLength = [maxLength, other.maxLength].compactMap { $0 }.min()
            out.maxConsecutive = [maxConsecutive, other.maxConsecutive].compactMap { $0 }.min()
            if isEmpty || other.isEmpty {
                let chars = isEmpty ? other : self
                out.required = chars.required
                out.allowed = chars.allowed
                return out
            }
            let shared = characters.intersection(other.characters)
            guard !shared.isEmpty else {
                // Contradictory rules: keep ours so the password is at least valid for the field.
                out.required = required
                out.allowed = allowed
                return out
            }
            out.required = (required + other.required).map { $0.filter(shared.contains) }.filter { !$0.isEmpty }
            out.allowed = [shared.sorted()]
            return out
        }
    }

    static func generate(rules text: String? = nil) -> String {
        generate(rules: parse(text))
    }

    /// The rules of a generate request: the new field's, the confirm field's and Apple's
    /// quirks for the site, all honoured. Empty or blank rules are no rules (R2).
    static func rules(password: String?, confirm: String?, quirks: String?) -> Rules {
        [password, confirm, quirks]
            .compactMap { text -> String? in
                guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
                return text
            }
            .map(parse)
            .reduce(Rules()) { $0.combined(with: $1) }
    }

    /// What the iOS 26.2 generate request returns, in priority order: a strong password, and
    /// an alphanumeric one as the alternative. A site that allows no symbols gets only the
    /// alphanumeric one (`ASGeneratedPassword.Kind.alphanumeric`).
    enum Kind: Equatable { case strong, alphanumeric }

    static func suggestions(rules: Rules) -> [(kind: Kind, value: String)] {
        guard rules.allowsSymbols else { return [(.alphanumeric, generate(rules: rules))] }
        var out: [(kind: Kind, value: String)] = [(.strong, generate(rules: rules))]
        if let plain = alphanumeric(rules) { out.append((.alphanumeric, generate(rules: plain))) }
        return out
    }

    /// The rules with symbols taken out, or nil when the site requires a symbol.
    private static func alphanumeric(_ rules: Rules) -> Rules? {
        var out = rules
        let base = rules.isEmpty ? [upper, lower, digits] : rules.required
        let isPlain: (Character) -> Bool = { $0.isASCII && ($0.isLetter || $0.isNumber) }
        out.required = base.map { $0.filter(isPlain) }
        guard !out.required.contains(where: \.isEmpty) else { return nil }
        out.allowed = rules.isEmpty ? [] : rules.allowed.map { $0.filter(isPlain) }.filter { !$0.isEmpty }
        return out.isEmpty ? nil : out
    }

    static func generate(rules: Rules) -> String {
        var classes = rules.isEmpty ? [upper, lower, digits, symbols] : rules.required + rules.allowed
        classes = classes.map { Array(Set($0)).sorted() }.filter { !$0.isEmpty }
        if classes.isEmpty { classes = [upper, lower, digits, symbols] }
        var guaranteed = rules.isEmpty ? classes : rules.required.map { Array(Set($0)).sorted() }.filter { !$0.isEmpty }
        let pool = Array(Set(classes.joined())).sorted()

        var length = max(defaultLength, rules.minLength ?? 0)
        if let max = rules.maxLength, max >= 1 { length = min(length, max) }
        length = min(max(length, 1), maxLength)
        // `maxlength` wins over the class count: a field that holds 3 characters gets 3,
        // with the first 3 required classes.
        if guaranteed.count > length { guaranteed = Array(guaranteed.prefix(length)) }

        var chars = guaranteed.compactMap { $0.randomElement() }
        while chars.count < length, let c = pool.randomElement() { chars.append(c) }
        chars.shuffle()
        if let limit = rules.maxConsecutive, limit >= 1 {
            limitRuns(&chars, to: limit, classes: classes, pool: pool)
        }
        return String(chars)
    }

    /// Breaks every run longer than `limit` by replacing its next character with another one
    /// of the same class (so a required class stays present), or any other from the pool.
    static func limitRuns(_ chars: inout [Character], to limit: Int, classes: [[Character]], pool: [Character]) {
        guard limit >= 1, chars.count > limit else { return }
        var run = 1
        for i in 1..<chars.count {
            if chars[i] == chars[i - 1] {
                run += 1
            } else {
                run = 1
                continue
            }
            guard run > limit else { continue }
            let previous = chars[i - 1]
            let own = classes.first { $0.contains(chars[i]) } ?? pool
            if let other = own.filter({ $0 != previous }).randomElement() ?? pool.filter({ $0 != previous }).randomElement() {
                chars[i] = other
                run = 1
            }
        }
    }

    /// Parses Apple's `passwordrules` syntax. Unknown properties and classes are ignored.
    static func parse(_ text: String?) -> Rules {
        var rules = Rules()
        guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return rules }
        for part in text.split(separator: ";") {
            let pieces = part.split(separator: ":", maxSplits: 1).map { $0.trimmingCharacters(in: .whitespaces) }
            guard pieces.count == 2 else { continue }
            let key = pieces[0].lowercased()
            let value = pieces[1]
            switch key {
            case "minlength": rules.minLength = Int(value)
            case "maxlength": rules.maxLength = Int(value)
            case "max-consecutive":
                if let n = Int(value) { rules.maxConsecutive = min(rules.maxConsecutive ?? n, n) }
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
