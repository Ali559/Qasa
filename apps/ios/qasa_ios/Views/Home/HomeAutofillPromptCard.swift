import AuthenticationServices
import SwiftUI

/// Whether Home asks the user to turn on Qasa in AutoFill & Passwords: shown while iOS
/// doesn't list Qasa as an AutoFill provider, until it does or the user taps Not now,
/// which hides it for three days. Rechecked whenever the app comes back to the front.
@MainActor
@Observable
final class AutofillPromptState {
    static let shared = AutofillPromptState()
    private static let snoozeKey = "qasa.autofill.promptSnoozedUntil"
    private static let snooze: TimeInterval = 3 * 24 * 60 * 60

    private(set) var show = false

    func refresh() async {
        let snoozedUntil = UserDefaults.standard.double(forKey: Self.snoozeKey)
        if Date().timeIntervalSince1970 < snoozedUntil {
            show = false
            return
        }
        let enabled = await ASCredentialIdentityStore.shared.state().isEnabled
        show = !enabled
    }

    func notNow() {
        UserDefaults.standard.set(Date().timeIntervalSince1970 + Self.snooze, forKey: Self.snoozeKey)
        show = false
    }

    /// iOS 18's own "Allow Qasa to fill passwords?" prompt; no trip through Settings.
    /// iOS 17 has no prompt, so it opens Qasa's AutoFill page in Settings instead; the
    /// card refreshes when the app comes back to the foreground.
    func activate() {
        if #available(iOS 18.0, *) {
            ASSettingsHelper.requestToTurnOnCredentialProviderExtension { _ in
                Task { @MainActor in await self.refresh() }
            }
        } else {
            ASSettingsHelper.openCredentialProviderAppSettings { _ in }
        }
    }
}

/// "Fill passwords with Qasa": the Qasa mark, a title, why, and Activate / Not now.
struct HomeAutofillPromptCard: View {
    @Environment(\.themePalette) private var theme
    @Environment(AppPreferences.self) private var prefs
    let state: AutofillPromptState

    var body: some View {
        let l = prefs.locale
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                QasaMark(size: 24)
                    .foregroundColor(theme.onAccent)
                    .frame(width: 44, height: 44)
                    .background(theme.accent)
                    .clipShape(Circle())
                    .accessibilityHidden(true)
                Text(L10n.tr("autofill.prompt.title", locale: l))
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
            }
            Text(L10n.tr("autofill.prompt.body", locale: l))
                .font(.qasa(13))
                .foregroundColor(theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 12) {
                Button(L10n.tr("autofill.prompt.activate", locale: l), action: state.activate)
                    .buttonStyle(.qasaPrimary)
                Button(L10n.tr("autofill.prompt.notNow", locale: l), action: state.notNow)
                    .buttonStyle(QasaButtonStyle(kind: .plain, fullWidth: false))
            }
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(theme.stroke, lineWidth: 1)
        )
    }
}
