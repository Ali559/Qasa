import Foundation

enum SuggestedContext: Equatable {
    case none
    case checkout
    case googleSignIn

    var title: String {
        switch self {
        case .none: return ""
        case .checkout: return "Suggested for Checkout"
        case .googleSignIn: return "Suggested for Google Sign-In"
        }
    }

    var isEmpty: Bool { self == .none }
}

protocol SuggestedContextProviding {
    var context: SuggestedContext { get }
}

/// Demo provider — swap for AutoFill / extension later.
final class MockSuggestedContextProvider: SuggestedContextProviding {
    var context: SuggestedContext = .checkout
}

enum VaultSuggestionEngine {
    static func suggest(items: [VaultItemRecord], context: SuggestedContext) -> [VaultItemRecord] {
        switch context {
        case .none:
            return []
        case .checkout:
            return items.filter { $0.kind == .payment }
        case .googleSignIn:
            return items.filter { item in
                guard case .login(let f) = item.payload else { return false }
                let hay = (f.website + f.itemName + f.email).lowercased()
                return hay.contains("google") || hay.contains("gmail") || hay.contains("accounts.google")
            }
        }
    }
}
