import SwiftUI

/// Pair with an existing Qasa install (adopt account_id) then optionally LAN-sync vault.
struct PairExistingVaultView: View {
    var onPaired: (UUID) -> Void
    var onCancel: () -> Void

    @Environment(AppPreferences.self) private var prefs
    @State private var code = ""
    @State private var accountIdText = ""
    @State private var errorMessage: String?
    @State private var isChecking = false
    @State private var linkedAccountId: UUID?
    @State private var step: Step = .intro

    private var locale: Locale { prefs.locale }

    enum Step {
        case intro, enterCode, needsPremium, success
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case .intro:
                        introBody
                    case .enterCode:
                        enterCodeBody
                    case .needsPremium:
                        premiumBody
                    case .success:
                        successBody
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }

            if step != .needsPremium {
                bottomBar
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .environment(\.layoutDirection, prefs.layoutDirection)
        .environment(\.locale, locale)
    }

    private var header: some View {
        ZStack {
            Text(L10n.tr("pair.title", locale: locale))
                .font(.qasa(18, weight: .bold))
                .foregroundColor(Theme.textOnChrome)
                .lineLimit(1)

            HStack {
                Button(action: onCancel) {
                    Text(L10n.tr("settings.cancel", locale: locale))
                        .font(.qasa(16, weight: .semibold))
                        .foregroundColor(Theme.accent)
                }
                Spacer()
            }
        }
        .padding(.horizontal, 16)
        .frame(height: 64)
        .frame(maxWidth: .infinity)
        .background(Theme.chrome)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.strokeChrome).frame(height: 1)
        }
    }

    private var introBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.tr("pair.headline", locale: locale))
                .font(.qasa(22, weight: .bold))
                .foregroundColor(Theme.textPrimary)
                .multilineTextAlignment(.leading)
            Text(L10n.tr("pair.body", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
    }

    private var enterCodeBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: L10n.tr("pair.codeLabel", locale: locale))
            ThemedTextField(
                icon: "number",
                placeholder: L10n.tr("pair.codePlaceholder", locale: locale),
                text: $code
            )
            .keyboardType(.numberPad)
            .textContentType(.oneTimeCode)

            Text(L10n.tr("pair.accountHint", locale: locale))
                .font(.qasa(12))
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.leading)

            ThemedTextField(
                icon: "person.badge.key",
                placeholder: L10n.tr("pair.accountPlaceholder", locale: locale),
                text: $accountIdText
            )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()

            if let errorMessage {
                Text(errorMessage)
                    .font(.qasa(13, weight: .medium))
                    .foregroundColor(Theme.danger)
                    .multilineTextAlignment(.leading)
            }
        }
    }

    private var premiumBody: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 10) {
                Image(systemName: "star.fill")
                    .foregroundColor(Theme.gold)
                Text(L10n.tr("pair.premiumTitle", locale: locale))
                    .font(.qasa(20, weight: .bold))
                    .foregroundColor(Theme.gold)
            }
            Text(L10n.tr("pair.premiumBody", locale: locale))
                .font(.qasa(15))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
        .padding(21)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            LinearGradient(
                colors: [Theme.gold.opacity(0.12), Theme.gold.opacity(0.04)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.gold.opacity(0.25), lineWidth: 1)
        )
    }

    private var successBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.tr("pair.successTitle", locale: locale))
                .font(.qasa(22, weight: .bold))
                .foregroundColor(Theme.accent)
                .multilineTextAlignment(.leading)
            Text(L10n.tr("pair.successBody", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
    }

    private var bottomBar: some View {
        VStack(spacing: 0) {
            Divider().background(Theme.stroke)
            Group {
                if step == .intro {
                    PrimaryButton(
                        title: L10n.tr("pair.enterCode", locale: locale),
                        icon: "qrcode"
                    ) {
                        step = .enterCode
                    }
                } else if step == .success {
                    PrimaryButton(
                        title: L10n.tr("pair.done", locale: locale),
                        icon: "checkmark"
                    ) {
                        if let linkedAccountId {
                            onPaired(linkedAccountId)
                        } else {
                            onCancel()
                        }
                    }
                } else {
                    PrimaryButton(
                        title: isChecking
                            ? L10n.tr("pair.checking", locale: locale)
                            : L10n.tr("pair.continue", locale: locale),
                        icon: isChecking ? nil : "link"
                    ) {
                        Task { await applyPairing() }
                    }
                    .disabled(isChecking)
                    .opacity(isChecking ? 0.7 : 1)
                }
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 17)
            .padding(.bottom, 32)
        }
        .background(Theme.chrome.opacity(0.92))
    }

    @MainActor
    private func applyPairing() async {
        errorMessage = nil
        let accountId: UUID?
        if let id = UUID(uuidString: accountIdText.trimmingCharacters(in: .whitespacesAndNewlines)) {
            accountId = id
        } else {
            let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
            accountId = PairingSessionStore.shared.resolve(code: trimmed)
        }

        guard let accountId else {
            errorMessage = L10n.tr("pair.invalidCode", locale: locale)
            return
        }

        isChecking = true
        defer { isChecking = false }

        do {
            let check = try await DeviceRegistrationService.shared.checkLinkEligibility(accountId: accountId)
            if !check.eligible {
                if check.error == "premium_required" || check.tier.lowercased() != "premium" {
                    step = .needsPremium
                    return
                }
                errorMessage = L10n.tr("pair.limitExceeded", locale: locale)
                return
            }
        } catch {
            errorMessage = L10n.tr("pair.checkFailed", locale: locale)
            return
        }

        VaultKeychainStore.accountId = accountId.uuidString.lowercased()
        linkedAccountId = accountId
        step = .success
    }
}

@Observable
final class PairingSessionStore {
    static let shared = PairingSessionStore()
    private var codeToAccount: [String: (UUID, Date)] = [:]

    func publish(accountId: UUID) -> String {
        let code = String(format: "%06d", Int.random(in: 0...999_999))
        codeToAccount[code] = (accountId, Date().addingTimeInterval(600))
        return code
    }

    func resolve(code: String) -> UUID? {
        guard let entry = codeToAccount[code], entry.1 > Date() else { return nil }
        return entry.0
    }

    func currentPublishedCode(for accountId: UUID) -> String? {
        codeToAccount.first(where: { $0.value.0 == accountId && $0.value.1 > Date() })?.key
    }
}
