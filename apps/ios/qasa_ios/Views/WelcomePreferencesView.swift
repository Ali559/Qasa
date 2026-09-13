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

                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(text: L10n.tr("onboarding.language", locale: locale))
                        VStack(spacing: 0) {
                            ForEach(Array(AppLanguage.allCases.enumerated()), id: \.element) { index, language in
                                if index > 0 {
                                    Divider().background(Theme.stroke)
                                }
                                languageRow(language)
                            }
                        }
                        .background(Theme.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        SectionLabel(text: L10n.tr("onboarding.theme", locale: locale))
                        VStack(spacing: 0) {
                            ForEach(Array(AppThemePreference.allCases.enumerated()), id: \.element) { index, theme in
                                if index > 0 {
                                    Divider().background(Theme.stroke)
                                }
                                themeRow(theme)
                            }
                        }
                        .background(Theme.cardBackground)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
                        .overlay(
                            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 28)
                .padding(.bottom, 24)
            }

            VStack(spacing: 0) {
                Divider().background(Theme.stroke)
                PrimaryButton(title: L10n.tr("onboarding.continue", locale: locale), icon: "arrow.forward") {
                    prefs.markWelcomeCompleted()
                    onContinue()
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 17)
                .padding(.bottom, 32)
            }
            .background(Theme.chrome.opacity(0.92))
        }
        .background(welcomeBackground.ignoresSafeArea())
    }

    private var welcomeBackground: some View {
        Theme.background
            .overlay(alignment: .top) {
                LinearGradient(
                    colors: [Theme.mint.opacity(0.12), Theme.mint.opacity(0)],
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
            HStack(spacing: 14) {
                Text(language.displayName)
                    .font(.qasa(17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                    .environment(\.layoutDirection, language.layoutDirection)
                Spacer(minLength: 8)
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Theme.mint)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(selected ? Theme.mint.opacity(0.08) : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func themeRow(_ theme: AppThemePreference) -> some View {
        let selected = prefs.theme == theme
        return Button {
            withAnimation(.easeInOut(duration: 0.18)) {
                prefs.theme = theme
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: themeIcon(theme))
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(selected ? Theme.mint : Theme.textSecondary)
                    .frame(width: 28)
                Text(L10n.tr(theme.displayNameKey, locale: locale))
                    .font(.qasa(17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Spacer(minLength: 8)
                if selected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Theme.mint)
                        .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .background(selected ? Theme.mint.opacity(0.08) : Color.clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
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
