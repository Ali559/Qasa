import Foundation

enum AutofillItemKind: String, Codable, Sendable {
    case login
    case card
    case bankAccount
}

/// Non-secret metadata + encrypted secrets blob for Autofill (App Group).
struct AutofillIndexEntry: Codable, Identifiable, Sendable, Equatable {
    let id: UUID
    let kind: AutofillItemKind
    let title: String
    let subtitle: String
    /// Normalized host for login matching (e.g. `accounts.google.com`).
    let websiteHost: String?
    let last4: String?
    /// AES-GCM sealed `AutofillSecrets` JSON using vault enc key.
    let encryptedSecrets: Data
}

struct AutofillIndexFile: Codable, Sendable {
    var version: Int = 1
    var updatedAt: Date
    var entries: [AutofillIndexEntry]
}

enum AutofillSecrets: Codable, Sendable {
    case login(Login)
    case card(Card)
    case bank(Bank)

    struct Login: Codable, Sendable {
        var email: String
        var username: String
        var password: String
        var website: String
    }

    struct Card: Codable, Sendable {
        var itemName: String
        var accountHolder: String
        var number: String
        var securityCode: String
        var expiryDate: String
    }

    struct Bank: Codable, Sendable {
        var itemName: String
        var accountHolder: String
        var accountNumber: String
        var routingNumber: String
        var bankName: String
    }
}

struct AutofillCandidate: Identifiable, Sendable, Equatable {
    let id: UUID
    let kind: AutofillItemKind
    let title: String
    let subtitle: String
    let last4: String?
    let score: Int
    let encryptedSecrets: Data
}
