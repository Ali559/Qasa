import Foundation

enum VaultItemType: String, CaseIterable {
    case login = "Logins"
    case card = "Cards"
    case note = "Secure Notes"
}

struct VaultItem: Identifiable {
    let id = UUID()
    let title: String
    let subtitle: String
    let type: VaultItemType
    let iconName: String
    var flagged: Bool = false
}

struct LinkedDevice: Identifiable {
    let id = UUID()
    let name: String
    let location: String
    let isThisDevice: Bool
    let iconName: String
}

struct PaymentMethod: Identifiable {
    let id = UUID()
    let name: String
    let subtitle: String
    let badgeText: String
    let badgeColorHex: String
}

enum BreachStatus: String {
    case unresolved = "UNRESOLVED"
    case resolved = "RESOLVED"
}

struct Breach: Identifiable {
    let id = UUID()
    let title: String
    let breachDate: String
    let discoveredDate: String
    let compromisedFields: [String]
    let status: BreachStatus
}
