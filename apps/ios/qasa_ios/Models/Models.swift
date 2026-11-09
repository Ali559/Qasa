import Foundation

struct LinkedDevice: Identifiable {
    let id = UUID()
    let name: String
    let location: String
    let isThisDevice: Bool
    let iconName: String
}

struct PaymentMethod: Identifiable, Equatable {
    /// Stable id so selection survives view recomputation (must not be a fresh UUID).
    var id: String { gateway }
    let name: String
    let subtitle: String
    let badgeText: String
    let gateway: String
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
