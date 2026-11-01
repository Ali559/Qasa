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

    var autofillHint: AutofillContextHint {
        switch self {
        case .none: return .unknown
        case .checkout: return .paymentCard
        case .googleSignIn: return .login
        }
    }

    var serviceIdentifiers: [String] {
        switch self {
        case .none, .checkout: return []
        case .googleSignIn: return ["accounts.google.com", "google.com"]
        }
    }
}

protocol SuggestedContextProviding {
    var context: SuggestedContext { get }
}

final class MockSuggestedContextProvider: SuggestedContextProviding {
    var context: SuggestedContext = .checkout
}

enum VaultSuggestionEngine {
    static func suggest(rows: [VaultListRow], context: SuggestedContext) -> [VaultListRow] {
        guard context != .none else { return [] }

        // Prefer ranking via the shared Autofill matcher when an App Group index exists.
        let index = AutofillIndexStore.load().entries
        if !index.isEmpty {
            let ranked = AutofillContextMatcher.candidates(
                from: index,
                serviceIdentifiers: context.serviceIdentifiers,
                hint: context.autofillHint
            )
            let byId = Dictionary(uniqueKeysWithValues: rows.map { ($0.id, $0) })
            let matched = ranked.compactMap { byId[$0.id] }
            if !matched.isEmpty { return matched }
        }

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
