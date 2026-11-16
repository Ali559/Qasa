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
    /// Several options can share one gateway (every brand pays through Wayl), so the id is the brand.
    let id: String
    let name: String
    let subtitle: String
    /// Short brand name for the "Pay … via %@" button.
    let badgeText: String
    /// Logo images in the asset catalog; two for the card row (Visa, Mastercard).
    let logos: [String]
    /// Sent to the server; the brand choice is UI only.
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
