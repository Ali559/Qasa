import AuthenticationServices
import Foundation

enum AutofillIdentitySync {
    static func sync(entries: [AutofillIndexEntry]) {
        let store = ASCredentialIdentityStore.shared
        store.getState { state in
            guard state.isEnabled else { return }
            let identities: [ASPasswordCredentialIdentity] = entries.compactMap { entry in
                guard entry.kind == .login else { return nil }
                let host = entry.websiteHost?.trimmingCharacters(in: .whitespacesAndNewlines)
                let service: ASCredentialServiceIdentifier
                if let host, !host.isEmpty {
                    service = ASCredentialServiceIdentifier(identifier: host, type: .domain)
                } else {
                    service = ASCredentialServiceIdentifier(identifier: entry.title, type: .domain)
                }
                let user = entry.subtitle.trimmingCharacters(in: .whitespacesAndNewlines)
                let recordIdentifier = entry.id.uuidString
                return ASPasswordCredentialIdentity(
                    serviceIdentifier: service,
                    user: user.isEmpty ? entry.title : user,
                    recordIdentifier: recordIdentifier
                )
            }
            store.replaceCredentialIdentities(with: identities) { _, _ in }
        }
    }
}
