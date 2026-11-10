import SwiftUI

/// One-time setup shown before vault creation so language and theme are chosen first.
struct WelcomePreferencesView: View {
    @Environment(AppPreferences.self) private var prefs
    var onContinue: () -> Void

    private var locale: Locale { prefs.locale }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader()

            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L10n.tr("onboarding.headline", locale: locale))
                            .font(.qasa(28, weight: .heavy))
                            .foregroundColor(Theme.textPrimary)
                        Text(L10n.tr("onboarding.subtitle", locale: locale))
                            .font(.qasa(15))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    SettingsSection(title: L10n.tr("onboarding.language", locale: locale)) {
                        ForEach(Array(AppLanguage.allCases.enumerated()), id: \.element) { index, language in
                            if index > 0 { RowDivider() }
                            languageRow(language)
                        }
                    }

                    SettingsSection(title: L10n.tr("onboarding.theme", locale: locale)) {
                        ForEach(Array(AppThemePreference.allCases.enumerated()), id: \.element) { index, theme in
                            if index > 0 { RowDivider() }
                            themeRow(theme)
                        }
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 28)
                .padding(.bottom, 24)
            }
        }
        .background(welcomeBackground.ignoresSafeArea())
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("onboarding.continue", locale: locale), icon: "arrow.forward") {
                prefs.markWelcomeCompleted()
                onContinue()
            }
        }
    }

    private var welcomeBackground: some View {
        Theme.background
            .overlay(alignment: .top) {
                LinearGradient(
                    colors: [Theme.accent.opacity(0.12), Theme.accent.opacity(0)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 220)
                .allowsHitTesting(false)
            }
    }

    private func languageRow(_ language: AppLanguage) -> some View {
        let selected = prefs.language == language
        return Button {
            withAnimation(.easeInOut(duration: 0.18)) {
                prefs.language = language
            }
        } label: {
            SettingsRow(
                icon: "character.bubble",
                title: language.displayName,
                iconColor: selected ? Theme.accent : Theme.textSecondary
            ) {
                selectionMark(selected)
            }
            .environment(\.layoutDirection, prefs.layoutDirection)
        }
        .buttonStyle(.qasaRow)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func themeRow(_ theme: AppThemePreference) -> some View {
        let selected = prefs.theme == theme
        return Button {
            withAnimation(.easeInOut(duration: 0.18)) {
                prefs.theme = theme
            }
        } label: {
            SettingsRow(
                icon: themeIcon(theme),
                title: L10n.tr(theme.displayNameKey, locale: locale),
                iconColor: selected ? Theme.accent : Theme.textSecondary
            ) {
                selectionMark(selected)
            }
        }
        .buttonStyle(.qasaRow)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// Trailing check for the selected option (space kept so titles never shift).
    private func selectionMark(_ selected: Bool) -> some View {
        Image(systemName: "checkmark")
            .font(.system(size: 15, weight: .bold))
            .foregroundColor(Theme.accent)
            .opacity(selected ? 1 : 0)
            .scaleEffect(selected ? 1 : 0.6)
            .accessibilityHidden(true)
    }

    private func themeIcon(_ theme: AppThemePreference) -> String {
        switch theme {
        case .system: return "circle.lefthalf.filled"
        case .light: return "sun.max"
        case .dark: return "moon"
        }
    }
}

#Preview {
    WelcomePreferencesView(onContinue: {})
        .environment(AppPreferences.shared)
}
