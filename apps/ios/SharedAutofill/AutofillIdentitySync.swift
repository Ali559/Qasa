import AuthenticationServices
import Foundation

/// What iOS shows in the QuickType bar: a password identity per login, and a one-time-code
/// identity per login with 2FA (iOS 18+ only, docs/autofill-plan.md §3.4). Profile items are not
/// registered: they are inserted as text, which needs no identity.
enum AutofillIdentitySync {
    static func identities(for entries: [AutofillIndexEntry]) -> [any ASCredentialIdentity] {
        var out: [any ASCredentialIdentity] = []
        for entry in entries where entry.kind == .login {
            // A login without a website has no domain: its title ("Netflix") is not one.
            guard let service = serviceIdentifier(for: entry) else { continue }
            let user = entry.subtitle.trimmingCharacters(in: .whitespacesAndNewlines)
            let label = user.isEmpty ? entry.title : user
            out.append(ASPasswordCredentialIdentity(serviceIdentifier: service, user: label, recordIdentifier: entry.id.uuidString))
            if #available(iOS 18.0, *), entry.hasTOTP == true {
                out.append(ASOneTimeCodeCredentialIdentity(serviceIdentifier: service, label: label, recordIdentifier: entry.id.uuidString))
            }
        }
        return out
    }

    static func serviceIdentifier(for entry: AutofillIndexEntry) -> ASCredentialServiceIdentifier? {
        guard let raw = entry.websiteHost, let host = AutofillContextMatcher.normalizeHost(raw),
              host.contains(".") || host == "localhost" else { return nil }
        return ASCredentialServiceIdentifier(identifier: host, type: .domain)
    }

    /// Replaces the QuickType identities; returns once the store has them, so syncs run in
    /// order (`VaultRepository.scheduleAutofillSync`).
    static func sync(entries: [AutofillIndexEntry]) async {
        let store = ASCredentialIdentityStore.shared
        let identities = identities(for: entries)
        let enabled = await withCheckedContinuation { continuation in
            store.getState { continuation.resume(returning: $0.isEnabled) }
        }
        guard enabled else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            store.replaceCredentialIdentities(identities) { _, _ in continuation.resume() }
        }
    }

    /// The vault is gone from this phone: nothing may stay in the QuickType bar.
    static func clear() {
        ASCredentialIdentityStore.shared.removeAllCredentialIdentities { _, _ in }
    }
}
