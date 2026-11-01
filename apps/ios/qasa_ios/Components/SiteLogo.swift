import SwiftUI

/// A recognisable stand-in for a site's logo: its first letter on its brand colour, so a
/// login is spotted at a glance without fetching icons from the network (which would leak
/// the user's list of sites). Unknown sites get a stable colour picked from their name.
/// Same table as Android `SiteLogo.kt`.
struct SiteLogo: Equatable {
    let letter: String
    let color: Color

    private static let knownBrands: [(String, String)] = [
        ("facebook", "1877F2"), ("messenger", "0084FF"), ("whatsapp", "25D366"),
        ("instagram", "D62976"), ("gmail", "EA4335"), ("google", "4285F4"),
        ("youtube", "FF0000"), ("snapchat", "F7C600"), ("tiktok", "111111"),
        ("telegram", "229ED9"), ("viber", "7360F2"), ("twitter", "1D9BF0"),
        ("linkedin", "0A66C2"), ("apple", "333333"), ("icloud", "3693F3"),
        ("microsoft", "00A4EF"), ("outlook", "0078D4"), ("amazon", "FF9900"),
        ("netflix", "E50914"), ("spotify", "1DB954"), ("github", "24292F"),
        ("fib", "0E7A5A"), ("fastpay", "E2136E"), ("superqi", "F57C20"),
        ("zain", "6B2C91"), ("asiacell", "E30613"), ("korek", "00843D"),
    ]

    /// Soft, readable-with-white colours for sites we do not recognise.
    private static let fallbackColors = ["7C6CF2", "E07A5F", "3FA7C9", "2F9E6C", "D4537E", "B7791F", "5B7CF0"]

    init(name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let key = trimmed.lowercased()
        letter = trimmed.first(where: { $0.isLetter || $0.isNumber }).map { String($0).uppercased() } ?? "?"
        if let brand = Self.knownBrands.first(where: { key.contains($0.0) }) {
            color = Color(hex: brand.1)
        } else {
            // Java's `String.hashCode` + `floorMod`, so a site gets the same colour as on Android
            // (and across launches, which Swift's seeded `hashValue` would not give).
            let hash = key.utf16.reduce(Int32(0)) { $0 &* 31 &+ Int32($1) }
            let count = Int32(Self.fallbackColors.count)
            color = Color(hex: Self.fallbackColors[Int(((hash % count) + count) % count)])
        }
    }
}
