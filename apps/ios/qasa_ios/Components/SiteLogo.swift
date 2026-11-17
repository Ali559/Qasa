import SwiftUI

/// The calm letter a login shows while it has no site icon (`docs/site-icons.md`): the name's
/// first letter or digit, upper-cased (`?` when none). No colour per site: it is drawn in the
/// theme accent on `cardMuted`.
struct SiteLogo: Equatable {
    let letter: String

    init(name: String) {
        letter = SiteIconRules.letter(for: name)
    }
}

/// A login's circle: its site's own icon when one is cached (`SiteIconStore`), else the calm
/// letter. The row tile is `vaultTile` 44 (letter 18 bold); the detail hero 88 (letter 36
/// bold). The cached icon is already shaped for its circle (`docs/site-icons.md` step 7), so
/// it is drawn filling the tile on `siteIconTile`, cut to the circle; a glyph icon gets a 1pt
/// `stroke` ring, a bleed or edge icon none. An arriving icon replaces the letter without
/// animation.
struct LoginSiteTile: View {
    @Environment(\.themePalette) private var theme
    let name: String
    /// The login's website, nil until its payload is read (the letter shows meanwhile).
    let website: String?
    var hero = false

    private var side: CGFloat { hero ? 88 : QasaTokens.Size.vaultTile }

    var body: some View {
        let host = website.flatMap(SiteIconRules.host(forWebsite:))
        Group {
            if let icon = SiteIconStore.shared.icon(forHost: host) {
                Image(uiImage: icon.image)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFill()
                    .frame(width: side, height: side)
                    .background(theme.siteIconTile)
                    .clipShape(Circle())
                    .overlay {
                        if icon.ring { Circle().stroke(theme.stroke, lineWidth: QasaTokens.Size.stroke) }
                    }
            } else {
                Text(SiteLogo(name: name).letter)
                    .font(.qasa(hero ? 36 : 18, weight: .bold))
                    .foregroundColor(theme.accent)
                    .frame(width: side, height: side)
                    .background(theme.cardMuted)
                    .clipShape(Circle())
            }
        }
        .transaction { $0.animation = nil }
        .accessibilityHidden(true)
        // Asked again when the setting is turned back on.
        .task(id: "\(AppPreferences.shared.siteIconsEnabled)|\(website ?? "")") {
            if let website { SiteIconStore.shared.request(website: website) }
        }
    }
}
