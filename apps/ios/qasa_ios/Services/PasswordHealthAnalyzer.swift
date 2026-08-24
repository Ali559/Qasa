import Foundation

struct PasswordHealthReport {
    let totalPasswords: Int
    let reusedCount: Int
    let weakCount: Int
    let score: Int
    let reusedItems: [VaultItemRecord]
    let weakItems: [VaultItemRecord]

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

    static func analyze(items: [VaultItemRecord]) -> PasswordHealthReport {
        let logins: [(item: VaultItemRecord, password: String)] = items.compactMap { item in
            guard case .login(let fields) = item.payload else { return nil }
            let trimmed = fields.password.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return nil }
            return (item, trimmed)
        }

        guard !logins.isEmpty else { return .empty }

        var frequency: [String: Int] = [:]
        for entry in logins {
            frequency[entry.password, default: 0] += 1
        }

        let reusedItems = logins
            .filter { frequency[$0.password, default: 0] > 1 }
            .map(\.item)
            .sorted { $0.sortKey.localizedCaseInsensitiveCompare($1.sortKey) == .orderedAscending }

        let weakItems = logins
            .filter { isWeak($0.password) }
            .map(\.item)
            .sorted { $0.sortKey.localizedCaseInsensitiveCompare($1.sortKey) == .orderedAscending }

        let reusedCount = reusedItems.count
        let weakCount = weakItems.count
        let passwords = logins.map(\.password)

        var score = 100
        score -= weakCount * 12
        score -= reusedCount * 10
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
            weakItems: weakItems
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
