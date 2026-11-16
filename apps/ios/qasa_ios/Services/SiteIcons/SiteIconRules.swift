import Foundation

/// The pure rules of `docs/site-icons.md`: which host a login's website asks, which hosts
/// are never asked, the fallback letter, and which `<link>` icons a page offers (in the
/// order they are tried). Every case in `docs/site-icons-vectors.json` runs against these in
/// `SiteIconRulesTests`; Android, the desktop and the extension run the same cases.
nonisolated enum SiteIconRules {
    // MARK: - Host

    /// The host a login's `website` asks (and the cache key), or nil when it is never asked.
    ///
    /// Read like AutoFill reads it (trim; a scheme must be `http`/`https`, none means
    /// `https://`; lowercase, one trailing `.` and a leading `www.` dropped), but parsed here
    /// rather than through `AutofillContextMatcher.normalizeHost`, which falls back to the raw
    /// string when `URL` can't parse it (`https://2001:db8::1`) and keeps a trailing dot.
    static func host(forWebsite website: String) -> String? {
        let trimmed = website.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        var rest = Substring(trimmed)
        if let sep = trimmed.range(of: "://") {
            let scheme = trimmed[..<sep.lowerBound].lowercased()
            guard scheme == "http" || scheme == "https" else { return nil }
            rest = trimmed[sep.upperBound...]
        }
        // The authority ends at the path, query or fragment.
        var authority = rest.prefix { $0 != "/" && $0 != "?" && $0 != "#" && $0 != "\\" }
        if let at = authority.lastIndex(of: "@") {
            authority = authority[authority.index(after: at)...]
        }
        // `[::1]`, or an unbracketed IPv6 literal (more than one colon).
        if authority.hasPrefix("[") { return nil }
        let colons = authority.filter { $0 == ":" }.count
        if colons > 1 { return nil }
        if colons == 1, let colon = authority.firstIndex(of: ":") {
            let port = authority[authority.index(after: colon)...]
            guard port.allSatisfy(\.isASCIIDigit) else { return nil }
            authority = authority[..<colon]
        }
        return askableHost(String(authority))
    }

    /// The normalised host when it may be asked, else nil (the skip rules of the spec). Also
    /// applied to every redirect target and icon URL.
    static func askableHost(_ raw: String) -> String? {
        var host = raw.lowercased()
        if host.hasSuffix(".") { host.removeLast() }
        if host.hasPrefix("www.") { host.removeFirst(4) }
        guard !host.isEmpty, host.contains(".") else { return nil }
        guard host.allSatisfy({ $0.isLetter || $0.isNumber || $0 == "-" || $0 == "." }) else { return nil }
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.allSatisfy({ !$0.isEmpty }) else { return nil }
        // IPv4 (and its odd spellings): no real top-level domain is all digits.
        if let last = labels.last, last.allSatisfy(\.isASCIIDigit) { return nil }
        if blockedSuffixes.contains(where: { host == $0 || host.hasSuffix("." + $0) }) { return nil }
        return host
    }

    /// Names that are never on the public internet.
    static let blockedSuffixes = [
        "local", "localhost", "lan", "home", "internal", "intranet", "corp",
        "home.arpa", "test", "example", "invalid", "onion",
    ]

    /// Whether `url` may be requested: `https` to an askable host.
    static func isAskable(_ url: URL) -> Bool {
        guard url.scheme?.lowercased() == "https", let host = url.host else { return false }
        if host.hasPrefix("[") || host.contains(":") { return false }
        return askableHost(host) != nil
    }

    // MARK: - Letter

    /// The calm-letter fallback: the name's first letter or digit, upper-cased, `?` when none.
    static func letter(for name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
            .first(where: { $0.isLetter || $0.isNumber })
            .map { String($0).uppercased() } ?? "?"
    }

    // MARK: - Icons in a page

    /// The `<link>` icons `html` offers, resolved against `pageURL`, in the order they are
    /// tried: `apple-touch-icon*` first, then the largest `sizes` first, ties in document
    /// order. Non-`https` (except base64 PNG / ICO `data:`) and SVG icons are skipped. The
    /// `/favicon.ico` fallback is not included.
    static func pickIcons(html: String, pageURL: URL) -> [URL] {
        struct Candidate { let url: URL; let apple: Bool; let size: Int; let order: Int }
        var candidates: [Candidate] = []
        let ns = html as NSString
        for (order, match) in linkTag.matches(in: html, range: NSRange(location: 0, length: ns.length)).enumerated() {
            let attrs = attributes(ns.substring(with: match.range(at: 1)))
            let rels = (attrs["rel"] ?? "").lowercased().split(whereSeparator: \.isWhitespace).map(String.init)
            let apple = rels.contains("apple-touch-icon") || rels.contains("apple-touch-icon-precomposed")
            guard apple || rels.contains("icon") else { continue }
            guard let href = attrs["href"]?.trimmingCharacters(in: .whitespacesAndNewlines), !href.isEmpty else { continue }
            if (attrs["type"] ?? "").trimmingCharacters(in: .whitespaces).lowercased() == "image/svg+xml" { continue }
            guard let url = resolve(href, against: pageURL) else { continue }
            candidates.append(Candidate(url: url, apple: apple, size: largestSize(attrs["sizes"]), order: order))
        }
        let sorted = candidates.sorted { a, b in
            if a.apple != b.apple { return a.apple }
            if a.size != b.size { return a.size > b.size }
            return a.order < b.order
        }
        var seen = Set<String>()
        return sorted.map(\.url).filter { seen.insert($0.absoluteString).inserted }
    }

    /// An icon `href` resolved to an absolute URL, or nil when it may not be used.
    static func resolve(_ href: String, against pageURL: URL) -> URL? {
        let lower = href.lowercased()
        if lower.hasPrefix("data:") {
            guard lower.hasPrefix("data:image/png;base64,") || lower.hasPrefix("data:image/x-icon;base64,") else { return nil }
            return URL(string: href)
        }
        guard let url = URL(string: href, relativeTo: pageURL)?.absoluteURL else { return nil }
        guard isAskable(url) else { return nil }
        if url.path.lowercased().hasSuffix(".svg") { return nil }
        return url
    }

    /// The largest edge in a `sizes` attribute (`16x16 48x48` → 48); `any` or missing is 0.
    static func largestSize(_ sizes: String?) -> Int {
        guard let sizes else { return 0 }
        return sizes.lowercased().split(whereSeparator: \.isWhitespace).map { token -> Int in
            let parts = token.split(separator: "x")
            guard parts.count == 2, let w = Int(parts[0]), let h = Int(parts[1]) else { return 0 }
            return max(w, h)
        }.max() ?? 0
    }

    /// The decoded bytes of a base64 PNG / ICO `data:` URL.
    static func dataURLBytes(_ url: URL) -> Data? {
        let s = url.absoluteString
        guard s.lowercased().hasPrefix("data:"), let comma = s.firstIndex(of: ",") else { return nil }
        let payload = String(s[s.index(after: comma)...]).removingPercentEncoding ?? ""
        return Data(base64Encoded: payload, options: .ignoreUnknownCharacters)
    }

    private static let linkTag = try! NSRegularExpression(pattern: "<link\\b([^>]*)>", options: [.caseInsensitive])
    private static let attribute = try! NSRegularExpression(
        pattern: "([^\\s\"'=<>/]+)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?",
        options: []
    )

    /// A tag's attributes, names lower-cased, the first of a repeated name kept.
    private static func attributes(_ source: String) -> [String: String] {
        let ns = source as NSString
        var result: [String: String] = [:]
        for m in attribute.matches(in: source, range: NSRange(location: 0, length: ns.length)) {
            let name = ns.substring(with: m.range(at: 1)).lowercased()
            guard result[name] == nil else { continue }
            var value = ""
            for group in 2...4 where m.range(at: group).location != NSNotFound {
                value = ns.substring(with: m.range(at: group))
                break
            }
            result[name] = decodeEntities(value)
        }
        return result
    }

    private static func decodeEntities(_ s: String) -> String {
        guard s.contains("&") else { return s }
        return s.replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&amp;", with: "&")
    }
}

private extension Character {
    nonisolated var isASCIIDigit: Bool { isASCII && isNumber }
}
