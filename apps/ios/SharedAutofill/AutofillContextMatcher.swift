import Foundation

enum AutofillContextHint: Equatable, Sendable {
    case login
    case paymentCard
    case bank
    case personal
    case unknown
}

enum AutofillContextMatcher {
    /// Rank index entries for Autofill service identifiers / hint.
    static func candidates(
        from entries: [AutofillIndexEntry],
        serviceIdentifiers: [String],
        hint: AutofillContextHint
    ) -> [AutofillCandidate] {
        let hosts = serviceIdentifiers.compactMap { normalizeHost($0) }
        var scored: [AutofillCandidate] = []

        for entry in entries {
            let score = scoreEntry(entry, hosts: hosts, hint: hint)
            guard score > 0 || hint == .unknown || hosts.isEmpty else { continue }
            let finalScore = score > 0 ? score : baseScore(for: entry.kind, hint: hint)
            guard finalScore > 0 else { continue }
            scored.append(
                AutofillCandidate(
                    id: entry.id,
                    kind: entry.kind,
                    title: entry.title,
                    subtitle: entry.subtitle,
                    last4: entry.last4,
                    score: finalScore,
                    encryptedSecrets: entry.encryptedSecrets
                )
            )
        }

        if scored.isEmpty {
            // Fallback: show type-appropriate items even without host match.
            scored = entries.compactMap { entry in
                let s = baseScore(for: entry.kind, hint: hint)
                guard s > 0 || hint == .unknown else { return nil }
                return AutofillCandidate(
                    id: entry.id,
                    kind: entry.kind,
                    title: entry.title,
                    subtitle: entry.subtitle,
                    last4: entry.last4,
                    score: max(s, 1),
                    encryptedSecrets: entry.encryptedSecrets
                )
            }
        }

        return scored.sorted {
            if $0.score != $1.score { return $0.score > $1.score }
            return $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending
        }
    }

    static func normalizeHost(_ raw: String) -> String? {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if s.isEmpty { return nil }
        if s.hasPrefix("http://") || s.hasPrefix("https://") {
            if let url = URL(string: s), let host = url.host { s = host }
        }
        if s.hasPrefix("www.") { s = String(s.dropFirst(4)) }
        return s.isEmpty ? nil : s
    }

    static func registrableDomain(_ host: String) -> String {
        let parts = host.split(separator: ".")
        guard parts.count >= 2 else { return host }
        return parts.suffix(2).joined(separator: ".")
    }

    private static func scoreEntry(_ entry: AutofillIndexEntry, hosts: [String], hint: AutofillContextHint) -> Int {
        var score = baseScore(for: entry.kind, hint: hint)
        guard entry.kind == .login, let entryHost = entry.websiteHost, !hosts.isEmpty else {
            return score
        }
        let normalizedEntry = normalizeHost(entryHost) ?? entryHost
        for host in hosts {
            if normalizedEntry == host {
                score += 100
            } else if normalizedEntry.hasSuffix("." + host) || host.hasSuffix("." + normalizedEntry) {
                score += 80
            } else if registrableDomain(normalizedEntry) == registrableDomain(host) {
                score += 60
            } else if normalizedEntry.contains(host) || host.contains(normalizedEntry) {
                score += 30
            } else {
                let titleHay = (entry.title + " " + entry.subtitle).lowercased()
                if titleHay.contains(registrableDomain(host)) || titleHay.contains(host) {
                    score += 20
                }
            }
        }
        return score
    }

    private static func baseScore(for kind: AutofillItemKind, hint: AutofillContextHint) -> Int {
        switch hint {
        case .login:
            return kind == .login ? 10 : 0
        case .paymentCard:
            return kind == .card ? 10 : (kind == .bankAccount ? 3 : 0)
        case .bank:
            return kind == .bankAccount ? 10 : (kind == .card ? 3 : 0)
        case .personal:
            return 0
        case .unknown:
            return 5
        }
    }
}
