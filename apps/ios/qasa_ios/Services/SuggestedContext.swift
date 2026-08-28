import Foundation

enum SuggestedContext: Equatable {
    case none
    case checkout
    case googleSignIn

    var title: String {
        switch self {
        case .none: return ""
        case .checkout: return L10n.tr("vault.suggestedCheckout")
        case .googleSignIn: return L10n.tr("vault.suggestedGoogle")
        }
    }

    var isEmpty: Bool { self == .none }
}

protocol SuggestedContextProviding {
    var context: SuggestedContext { get }
}

final class MockSuggestedContextProvider: SuggestedContextProviding {
    var context: SuggestedContext = .checkout
}

enum VaultSuggestionEngine {
    static func suggest(rows: [VaultListRow], context: SuggestedContext) -> [VaultListRow] {
        switch context {
        case .none:
            return []
        case .checkout:
            return rows.filter { $0.kind == .payment }
        case .googleSignIn:
            return rows.filter { row in
                guard row.kind == .login else { return false }
                let hay = (row.subtitle + row.displayName).lowercased()
                return hay.contains("google") || hay.contains("gmail") || hay.contains("accounts.google")
            }
        }
    }
}
