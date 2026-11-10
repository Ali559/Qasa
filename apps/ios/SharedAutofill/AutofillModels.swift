import Foundation

enum AutofillItemKind: String, Codable, Sendable, CaseIterable {
    case login
    case card
    case bankAccount
    // Text insertion (iOS 18 "AutoFill → Qasa"), docs/autofill-plan.md §3.4.
    case name
    case email
    case phone
    case address
    case company

    /// What the QuickType password list and the AutoFill sheet may offer. Profile items
    /// are only ever inserted as text.
    var isCredentialListKind: Bool {
        switch self {
        case .login, .card, .bankAccount: return true
        case .name, .email, .phone, .address, .company: return false
        }
    }
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
    /// A login with a 2FA code: registered for iOS's one-time-code AutoFill. Not a secret;
    /// nil in indexes written before it existed.
    var hasTOTP: Bool? = nil
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
    case name(Name)
    case email(Email)
    case phone(Phone)
    case address(Address)
    case company(Company)

    struct Name: Codable, Sendable {
        var firstName: String
        var middleName: String
        var lastName: String
    }

    struct Email: Codable, Sendable {
        var email: String
    }

    struct Phone: Codable, Sendable {
        var number: String
        /// `+964`, resolved by the app (the extensions don't carry the country table).
        var dialCode: String
    }

    struct Address: Codable, Sendable {
        var recipient: String
        var address: String
        var building: String
        var floor: String
        var apartment: String
        var city: String
        var zipCode: String
        var countryCode: String
        var countryName: String
    }

    struct Company: Codable, Sendable {
        var companyName: String
        var jobTitle: String
    }

    struct Login: Codable, Sendable {
        var email: String
        var username: String
        var password: String
        var website: String
        /// Normalised otpauth URI; nil for logins without 2FA and for indexes written before it.
        var totp: String? = nil
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
