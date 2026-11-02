import SwiftUI

/// The "You" tab: what the side menu used to hold, grouped into short cards so nothing is
/// more than one tap away. Mirrors Android `YouScreen`.
struct YouView: View {
    var onOpen: (AppPage) -> Void

    @Environment(\.themePalette) private var theme
    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("tab.you", locale: prefs.locale), onMenu: nil)
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    profileCard
                    group(L10n.tr("nav.tools", locale: prefs.locale),
                          [.notifications, .passwordHealth, .generator, .collections, .vpn])
                    group(L10n.tr("you.account", locale: prefs.locale), [.settings])
                    group(L10n.tr("you.about", locale: prefs.locale), [.terms, .privacy])
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 16)
                .padding(.bottom, 40)
            }
        }
        .themedBackground()
    }

    private var profileCard: some View {
        CardContainer {
            VStack(spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "person.fill")
                        .font(.qasa(26))
                        .foregroundColor(theme.textSecondary)
                        .frame(width: 56, height: 56)
                        .overlay(Circle().stroke(theme.accent, lineWidth: 2))
                    VStack(alignment: .leading, spacing: 4) {
                        Text(session.displayName)
                            .font(.qasa(16, weight: .semibold))
                            .foregroundColor(theme.textPrimary)
                        Text(L10n.tr(session.isPremium ? "settings.premiumAccount" : "settings.freeAccount",
                                     locale: prefs.locale))
                            .font(.qasa(13))
                            .foregroundColor(theme.textSecondary)
                    }
                    Spacer()
                }
                if !session.isPremium {
                    PrimaryButton(title: L10n.tr("settings.upgradePremium", locale: prefs.locale)) {
                        onOpen(.premiumUpgrade)
                    }
                }
            }
        }
    }

    private func group(_ label: String, _ pages: [AppPage]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            SectionLabel(text: label)
            CardContainer {
                VStack(spacing: 0) {
                    ForEach(pages) { page in
                        Button { onOpen(page) } label: {
                            NavChevronRow(icon: page.youIcon, title: page.title)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private extension AppPage {
    /// Outlined symbols: lighter than the filled set the side menu used.
    var youIcon: String {
        switch self {
        case .vault: return "folder"
        case .notifications: return "bell"
        case .generator: return "wand.and.stars"
        case .passwordHealth: return "heart.text.square"
        case .breachedPasswords: return "exclamationmark.shield"
        case .vpn: return "network.badge.shield.half.filled"
        case .collections: return "folder"
        case .settings: return "gearshape"
        case .terms: return "doc.text"
        case .privacy: return "hand.raised"
        case .premiumUpgrade: return "crown"
        }
    }
}
