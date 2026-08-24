import Foundation

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
