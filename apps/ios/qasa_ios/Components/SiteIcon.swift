import SwiftUI

/// Monochrome icons for common sites, bundled (`assets/site-icons`, generated into the
/// `SiteIcons` asset folder and `SiteIconData`) so nothing is fetched and no third party
/// learns which sites the user has. A login whose website isn't in the table keeps its
/// `SiteLogo` letter tile. Same table and matching as Android `SiteIcons.kt` and the
/// desktop `siteIcon.ts`.
enum SiteIcon {
    /// The icon id for a login's website, or nil. The host is read like AutoFill reads it
    /// (`AutofillContextMatcher.normalizeHost`, with a bare host taken as `https://<host>`);
    /// then the host and each parent domain are looked up, so `accounts.google.com` and
    /// `mail.google.co.uk` find their site. The table lists only registrable domains (the
    /// desktop tests check each against the Public Suffix List), so the first hit is the
    /// host's registrable domain, the same answer on every platform.
    static func id(forWebsite website: String) -> String? {
        let trimmed = website.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.contains("://") {
            let scheme = trimmed.lowercased()
            guard scheme.hasPrefix("http://") || scheme.hasPrefix("https://") else { return nil }
        }
        let withScheme = trimmed.contains("://") ? trimmed : "https://\(trimmed)"
        guard let normalized = AutofillContextMatcher.normalizeHost(withScheme) else { return nil }
        var host = Substring(normalized.hasSuffix(".") ? String(normalized.dropLast()) : normalized)
        while host.contains(".") {
            if let id = SiteIconData.domains[String(host)] { return id }
            host = host[host.index(after: host.firstIndex(of: ".")!)...]
        }
        return nil
    }

    /// The asset name (`site-<id>`, a template image) for a login's website, or nil.
    static func assetName(forWebsite website: String) -> String? {
        id(forWebsite: website).map { "site-\($0)" }
    }
}
