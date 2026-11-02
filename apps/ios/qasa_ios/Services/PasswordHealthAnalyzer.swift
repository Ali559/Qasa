import Foundation

struct PasswordHealthReport {
    let totalPasswords: Int
    let reusedCount: Int
    let weakCount: Int
    let score: Int
    let reusedItems: [VaultListRow]
    let weakItems: [VaultListRow]
    /// Logins whose password is in Pwned Passwords, with how often it was seen.
    var breachedItems: [VaultListRow] = []
    var breachCounts: [UUID: Int] = [:]
    /// The breach check ran and failed (usually offline). Breached stays 0 then.
    var breachCheckFailed = false

    var breachedCount: Int { breachedItems.count }

    static let empty = PasswordHealthReport(
        totalPasswords: 0,
        reusedCount: 0,
        weakCount: 0,
        score: 100,
        reusedItems: [],
        weakItems: []
    )
}

enum PasswordHealthAnalyzer {
    private static let common: Set<String> = [
        "password", "password1", "123456", "12345678", "qwerty", "abc123",
        "letmein", "admin", "welcome", "iloveyou", "monkey", "dragon"
    ]

    /// Trimmed, non-empty login passwords from the unlocked vault.
    static func vaultLogins() -> [(row: VaultListRow, password: String)] {
        (try? VaultRepository.shared.allLoginPayloads())?
            .compactMap { id, fields in
                guard let row = VaultRepository.shared.cache.row(for: id) else { return nil }
                let trimmed = fields.password.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return nil }
                return (row, trimmed)
            } ?? []
    }

    /// Local analysis plus the Pwned Passwords check. Never throws: a failed check comes back
    /// as `breachCheckFailed` on an otherwise complete report.
    static func analyzeWithBreaches(logins: [(row: VaultListRow, password: String)]) async -> PasswordHealthReport {
        guard !logins.isEmpty else { return .empty }
        do {
            let counts = try await PwnedPasswordsService.shared.breachCounts(for: logins.map(\.password))
            return analyze(logins: logins, breachCounts: counts)
        } catch {
            var report = analyze(logins: logins)
            report.breachCheckFailed = true
            return report
        }
    }

    /// `breachCounts` maps a password to its Pwned Passwords count; nil means not checked yet.
    static func analyze(
        logins: [(row: VaultListRow, password: String)],
        breachCounts: [String: Int]? = nil
    ) -> PasswordHealthReport {
        guard !logins.isEmpty else { return .empty }

        var frequency: [String: Int] = [:]
        for entry in logins {
            frequency[entry.password, default: 0] += 1
        }

        let reusedItems = logins
            .filter { frequency[$0.password, default: 0] > 1 }
            .map(\.row)
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }

        let weakItems = logins
            .filter { isWeak($0.password) }
            .map(\.row)
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }

        var rowCounts: [UUID: Int] = [:]
        for entry in logins {
            if let count = breachCounts?[entry.password] { rowCounts[entry.row.id] = count }
        }
        let breachedItems = logins
            .map(\.row)
            .filter { rowCounts[$0.id] != nil }
            .sorted { (rowCounts[$0.id] ?? 0) > (rowCounts[$1.id] ?? 0) }

        let reusedCount = Set(reusedItems.map(\.id)).count
        let weakCount = Set(weakItems.map(\.id)).count
        let passwords = logins.map(\.password)

        var score = 100
        score -= weakCount * 12
        score -= reusedCount * 10
        score -= breachedItems.count * 15
        if passwords.count >= 5 {
            let uniqueRatio = Double(Set(passwords).count) / Double(passwords.count)
            score -= Int((1 - uniqueRatio) * 20)
        }
        score = min(100, max(0, score))

        return PasswordHealthReport(
            totalPasswords: passwords.count,
            reusedCount: reusedCount,
            weakCount: weakCount,
            score: score,
            reusedItems: reusedItems,
            weakItems: weakItems,
            breachedItems: breachedItems,
            breachCounts: rowCounts
        )
    }

    static func isWeak(_ password: String) -> Bool {
        if password.count < 10 { return true }
        if common.contains(password.lowercased()) { return true }
        let hasLetter = password.rangeOfCharacter(from: .letters) != nil
        let hasNumber = password.rangeOfCharacter(from: .decimalDigits) != nil
        let hasSymbol = password.unicodeScalars.contains { CharacterSet.alphanumerics.inverted.contains($0) }
        let classes = [hasLetter, hasNumber, hasSymbol].filter { $0 }.count
        if classes < 2 { return true }
        if password.count < 12 && classes < 3 { return true }
        return false
    }
}
