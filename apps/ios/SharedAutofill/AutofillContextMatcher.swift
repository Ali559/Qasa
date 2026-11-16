import Foundation

nonisolated enum AutofillContextHint: Equatable, Sendable {
    case login
    case paymentCard
    case bank
    case personal
    case unknown
}

nonisolated enum AutofillContextMatcher {
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

    /// The host of a website as the user typed it or a service identifier: `https://www.example.com/login`,
    /// `example.com/login` and `example.com:8443` all give `example.com`. Nil when there is
    /// no host (an empty field, free text with spaces).
    static func normalizeHost(_ raw: String) -> String? {
        let s = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if s.isEmpty { return nil }
        // No scheme: read it as a web address, so a path or a port isn't part of the host.
        let withScheme = s.contains("://") ? s : "https://" + s
        var host: String
        if let parsed = URLComponents(string: withScheme)?.host, !parsed.isEmpty {
            host = parsed
        } else {
            let rest = withScheme.components(separatedBy: "://").last ?? s
            host = String(rest.prefix { !"/?#:".contains($0) })
        }
        if host.hasSuffix(".") { host.removeLast() }
        if host.hasPrefix("www.") { host = String(host.dropFirst(4)) }
        guard !host.isEmpty, !host.contains(where: \.isWhitespace) else { return nil }
        return host
    }

    /// Public suffixes of more than one label that people actually sign in under: the common
    /// country second levels and hosting platforms where every subdomain is someone else's.
    /// A trimmed Public Suffix List; anything not here counts as a one-label suffix.
    static let multiLabelSuffixes: Set<String> = [
        // United Kingdom, Australia, New Zealand, South Africa, Ireland-adjacent
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "ltd.uk", "plc.uk", "net.uk", "nhs.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au", "id.au",
        "co.nz", "org.nz", "net.nz", "govt.nz", "ac.nz",
        "co.za", "org.za", "gov.za", "ac.za",
        // Asia
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "ad.jp",
        "co.kr", "or.kr", "go.kr", "ac.kr",
        "com.cn", "net.cn", "org.cn", "gov.cn", "edu.cn",
        "com.hk", "org.hk", "com.tw", "org.tw", "com.sg", "edu.sg", "gov.sg", "com.my", "com.ph",
        "com.vn", "co.th", "in.th", "go.th", "co.id", "or.id", "go.id", "ac.id",
        "co.in", "net.in", "org.in", "gen.in", "firm.in", "ind.in", "ac.in", "gov.in", "com.pk", "com.bd", "com.np", "com.lk",
        // Middle East, Turkey, North Africa
        "com.iq", "net.iq", "org.iq", "gov.iq", "edu.iq",
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr", "gen.tr",
        "com.sa", "net.sa", "org.sa", "gov.sa", "edu.sa",
        "co.ae", "net.ae", "org.ae", "gov.ae", "ac.ae",
        "com.eg", "gov.eg", "edu.eg", "com.jo", "gov.jo", "com.lb", "edu.lb", "com.kw", "gov.kw",
        "com.qa", "gov.qa", "com.om", "gov.om", "com.bh", "com.sy", "com.ye", "com.ly", "com.tn", "com.dz", "co.ma", "gov.ma",
        "co.il", "org.il", "ac.il", "gov.il", "co.ir", "ac.ir", "gov.ir",
        // Europe
        "com.es", "com.gr", "com.cy", "com.mt", "com.pl", "net.pl", "org.pl", "com.ua", "org.ua", "gov.ua", "com.ru", "co.at", "or.at", "com.pt",
        // Americas
        "com.br", "net.br", "org.br", "gov.br", "edu.br", "com.mx", "org.mx", "gob.mx", "com.ar", "gob.ar", "com.co", "gov.co",
        "com.pe", "gob.pe", "com.ve", "com.ec", "com.uy", "com.py", "com.bo", "gob.cl",
        // Africa
        "com.ng", "gov.ng", "co.ke", "or.ke", "go.ke", "co.tz", "co.ug", "com.gh",
        // Hosting platforms
        "github.io", "gitlab.io", "herokuapp.com", "vercel.app", "netlify.app", "pages.dev", "workers.dev",
        "web.app", "firebaseapp.com", "appspot.com", "blogspot.com", "azurewebsites.net", "cloudfront.net",
        "amplifyapp.com", "onrender.com", "fly.dev", "glitch.me", "repl.co", "wixsite.com", "myshopify.com",
    ]

    /// The part a person registers: `login.amazon.co.uk` → `amazon.co.uk`,
    /// `accounts.google.com` → `google.com`. An IP address stays whole.
    static func registrableDomain(_ host: String) -> String {
        let parts = host.split(separator: ".").map(String.init)
        guard parts.count >= 2 else { return host }
        if parts.allSatisfy({ $0.allSatisfy(\.isNumber) }) { return host }
        let lastTwo = parts.suffix(2).joined(separator: ".")
        if multiLabelSuffixes.contains(lastTwo) {
            return parts.count >= 3 ? parts.suffix(3).joined(separator: ".") : lastTwo
        }
        return lastTwo
    }

    /// Whether `host` is only a public suffix (`co.uk`, `github.io`, `com`): nothing to match on.
    static func isPublicSuffix(_ host: String) -> Bool {
        !host.contains(".") || multiLabelSuffixes.contains(host)
    }

    /// `host` is `domain` or one of its subdomains, on a label boundary (`x.com` is not part of
    /// `netflix.com`).
    static func host(_ host: String, isWithin domain: String) -> Bool {
        host == domain || host.hasSuffix("." + domain)
    }

    /// How well a saved login's host matches the page's: the same host, one inside the other,
    /// or the same registrable domain; 0 for anything else.
    static func hostScore(entry: String, page: String) -> Int {
        if entry == page { return 100 }
        let site = registrableDomain(entry)
        guard !isPublicSuffix(site), site == registrableDomain(page) else { return 0 }
        if host(entry, isWithin: page) || host(page, isWithin: entry) { return 80 }
        return 60
    }

    private static func scoreEntry(_ entry: AutofillIndexEntry, hosts: [String], hint: AutofillContextHint) -> Int {
        var score = baseScore(for: entry.kind, hint: hint)
        guard entry.kind == .login, !hosts.isEmpty else { return score }
        if let raw = entry.websiteHost, let entryHost = normalizeHost(raw) {
            for host in hosts { score += hostScore(entry: entryHost, page: host) }
            return score
        }
        // No website saved: a title or subtitle that names the site ("netflix.com").
        let words = (entry.title + " " + entry.subtitle).split(whereSeparator: { $0.isWhitespace || $0 == "," })
        let named = words.filter { $0.contains(".") }.compactMap { normalizeHost(String($0)) }
        for host in hosts where named.contains(where: { hostScore(entry: $0, page: host) > 0 }) {
            score += 20
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
