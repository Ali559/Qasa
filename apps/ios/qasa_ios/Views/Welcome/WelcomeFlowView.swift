import SwiftUI

/// First launch (`design/README.md` rule 6d): language, then the live tour of free features,
/// then an optional name. Finishing marks the welcome done; the root then shows the main
/// password screen.
struct WelcomeFlowView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    enum Step { case language, tour, name }
    @State private var step: Step = .language

    var body: some View {
        ZStack {
            switch step {
            case .language:
                WelcomeLanguageView { go(.tour) }
                    .transition(stepTransition)
            case .tour:
                OnboardingTourView(onSkip: { go(.name) }, onDone: { go(.name) })
                    .transition(stepTransition)
            case .name:
                WelcomeNameView { name in
                    prefs.userName = name
                    prefs.markWelcomeCompleted()
                }
                .transition(stepTransition)
            }
        }
    }

    private var stepTransition: AnyTransition {
        reduceMotion ? .opacity : .asymmetric(
            insertion: .move(edge: .trailing).combined(with: .opacity),
            removal: .opacity
        )
    }

    private func go(_ next: Step) {
        withAnimation(reduceMotion ? QasaTokens.Motion.fade : QasaTokens.Motion.standard) { step = next }
    }
}

// MARK: - Language

/// Screen 1: the language only. The theme follows the system until it is changed in Settings.
private struct WelcomeLanguageView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    var onContinue: () -> Void

    private var locale: Locale { prefs.locale }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader()
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L10n.tr("onboarding.welcomeTitle", locale: locale))
                            .font(.qasa(28, weight: .bold))
                            .foregroundColor(theme.textPrimary)
                        Text(L10n.tr("onboarding.welcomeSubtitle", locale: locale))
                            .font(.qasa(15))
                            .foregroundColor(theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    SettingsSection {
                        ForEach(Array(AppLanguage.allCases.enumerated()), id: \.element) { index, language in
                            if index > 0 { RowDivider() }
                            languageRow(language)
                        }
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 28)
                .padding(.bottom, 24)
            }
        }
        .background(
            theme.background
                .overlay(alignment: .top) {
                    LinearGradient(colors: [theme.accent.opacity(0.12), theme.accent.opacity(0)], startPoint: .top, endPoint: .bottom)
                        .frame(height: 220)
                        .allowsHitTesting(false)
                }
                .ignoresSafeArea()
        )
        .bottomActionBar {
            PrimaryButton(title: L10n.tr("onboarding.getStarted", locale: locale), action: onContinue)
        }
    }

    private func languageRow(_ language: AppLanguage) -> some View {
        let selected = prefs.language == language
        return Button {
            withAnimation(QasaTokens.Motion.fast) { prefs.language = language }
        } label: {
            SettingsRow(
                icon: .language,
                title: language.displayName,
                iconColor: selected ? theme.accent : theme.textSecondary
            ) {
                QasaIconImage(.check, size: 15)
                    .foregroundColor(theme.accent)
                    .opacity(selected ? 1 : 0)
                    .scaleEffect(selected ? 1 : 0.6)
                    .accessibilityHidden(true)
            }
        }
        .buttonStyle(.qasaRow)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

// MARK: - Name

/// The last screen: an optional name, kept on this device and used in greetings.
private struct WelcomeNameView: View {
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.themePalette) private var theme
    var onDone: (String) -> Void
    @State private var name = ""

    private var locale: Locale { prefs.locale }

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader()
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(L10n.tr("onboarding.nameTitle", locale: locale))
                            .font(.qasa(28, weight: .bold))
                            .foregroundColor(theme.textPrimary)
                        Text(L10n.tr("onboarding.nameBody", locale: locale))
                            .font(.qasa(15))
                            .foregroundColor(theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    FieldGroup {
                        TextFieldRow(
                            label: L10n.tr("common.name", locale: locale),
                            text: $name,
                            hint: L10n.tr("onboarding.namePlaceholder", locale: locale),
                            contentType: .givenName,
                            onSubmit: { onDone(name) }
                        ) { EmptyView() }
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 28)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(theme.background.ignoresSafeArea())
        .bottomActionBar {
            VStack(spacing: QasaTokens.Space.sheetButtonGap) {
                PrimaryButton(title: L10n.tr("onboarding.nameGo", locale: locale)) { onDone(name) }
                Button(L10n.tr("common.skip", locale: locale)) { onDone("") }
                    .buttonStyle(.qasaSecondary)
            }
        }
    }
}

#Preview {
    WelcomeFlowView()
        .environment(AppPreferences.shared)
}
