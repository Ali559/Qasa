import AuthenticationServices
import Foundation

/// What iOS shows in the QuickType bar: a password identity per login, and a one-time-code
/// identity per login with 2FA (iOS 18+ only, docs/autofill-plan.md §3.4). Profile items are not
/// registered: they are inserted as text, which needs no identity.
enum AutofillIdentitySync {
    static func identities(for entries: [AutofillIndexEntry]) -> [any ASCredentialIdentity] {
        var out: [any ASCredentialIdentity] = []
        for entry in entries where entry.kind == .login {
            let service = serviceIdentifier(for: entry)
            let user = entry.subtitle.trimmingCharacters(in: .whitespacesAndNewlines)
            let label = user.isEmpty ? entry.title : user
            out.append(ASPasswordCredentialIdentity(serviceIdentifier: service, user: label, recordIdentifier: entry.id.uuidString))
            if #available(iOS 18.0, *), entry.hasTOTP == true {
                out.append(ASOneTimeCodeCredentialIdentity(serviceIdentifier: service, label: label, recordIdentifier: entry.id.uuidString))
            }
        }
        return out
    }

    static func serviceIdentifier(for entry: AutofillIndexEntry) -> ASCredentialServiceIdentifier {
        let host = entry.websiteHost?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let host, !host.isEmpty {
            return ASCredentialServiceIdentifier(identifier: host, type: .domain)
        }
        return ASCredentialServiceIdentifier(identifier: entry.title, type: .domain)
    }

    static func sync(entries: [AutofillIndexEntry]) {
        let store = ASCredentialIdentityStore.shared
        let identities = identities(for: entries)
        store.getState { state in
            guard state.isEnabled else { return }
            store.replaceCredentialIdentities(identities) { _, _ in }
        }
    }

    /// The vault is gone from this phone: nothing may stay in the QuickType bar.
    static func clear() {
        ASCredentialIdentityStore.shared.removeAllCredentialIdentities { _, _ in }
    }
}
