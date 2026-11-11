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
                    VStack(alignment: .leading, spacing: 8) {
                        group(L10n.tr("you.about", locale: prefs.locale), [.terms, .privacy])
                        // Solar is CC BY 4.0: the credit sits under About, as on Android.
                        Text(L10n.tr("you.iconsCredit", locale: prefs.locale))
                            .font(.qasa(12))
                            .foregroundColor(theme.textSecondary)
                            .padding(.horizontal, 4)
                    }
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
                    QasaIconImage(.person, size: 26)
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
        SettingsSection(title: label) {
            ForEach(Array(pages.enumerated()), id: \.element) { index, page in
                if index > 0 { RowDivider() }
                Button { onOpen(page) } label: {
                    NavChevronRow(icon: page.youIcon, title: page.title)
                }
                .buttonStyle(.qasaRow)
            }
        }
    }
}

private extension AppPage {
    /// Linear glyphs, like every row icon.
    var youIcon: QasaIcon {
        switch self {
        case .vault: return .folder
        case .notifications: return .alerts
        case .generator: return .generate
        case .passwordHealth: return .health
        case .breachedPasswords: return .breach
        case .vpn: return .vpn
        case .collections: return .folder
        case .settings: return .settings
        case .terms: return .note
        case .privacy: return .privacy
        case .premiumUpgrade: return .premium
        }
    }
}
