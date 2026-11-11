import SwiftUI
import SwiftData

/// A new device joins an existing vault. The main path is Wi‑Fi sync: the other device
/// shows a code, this one (no vault yet) adopts its vault and account from inside the
/// authenticated channel, then the user unlocks with the same main password. Pasting an
/// account ID (advanced) only links the account; the user then creates a vault here.
struct PairExistingVaultView: View {
    var onPaired: () -> Void
    var onCancel: () -> Void

    @Environment(AppPreferences.self) private var prefs
    @Environment(\.modelContext) private var modelContext
    @State private var sync = WiFiSyncController()
    @State private var accountIdText = ""
    @State private var errorMessage: String?
    @State private var isChecking = false
    @State private var adoptedFrom: String?
    @State private var step: Step = .intro

    private var locale: Locale { prefs.locale }

    enum Step {
        case intro, advanced, needsPremium, success, adopted
    }

    var body: some View {
        VStack(spacing: 0) {
            header

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case .intro:
                        introBody
                    case .advanced:
                        advancedBody
                    case .needsPremium:
                        premiumBody
                    case .success:
                        successBody
                    case .adopted:
                        adoptedBody
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 24)
            }

            if step != .needsPremium && step != .intro {
                bottomBar
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .onChange(of: sync.stage) { _, stage in
            guard case .done(let result) = stage else { return }
            sync.stop()
            adoptedFrom = result.peerName
            step = .adopted
        }
        .onDisappear { sync.stop() }
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
            Text(L10n.tr("pair.phoneBody", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)

            WiFiJoinPanel(sync: sync, container: modelContext.container)
            SyncOutcomeView(stage: sync.stage)

            Button {
                sync.stop()
                step = .advanced
            } label: {
                Text(L10n.tr("pair.accountHint", locale: locale))
                    .font(.qasa(13, weight: .medium))
                    .foregroundColor(Theme.accent)
                    .multilineTextAlignment(.leading)
            }
            .buttonStyle(PressableStyle())
            .padding(.top, 8)
        }
    }

    private var adoptedBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(String(format: L10n.tr("sync.adoptedTitle", locale: locale), adoptedFrom ?? ""))
                .font(.qasa(22, weight: .bold))
                .foregroundColor(Theme.accent)
                .multilineTextAlignment(.leading)
            Text(String(format: L10n.tr("sync.adoptedBody", locale: locale), adoptedFrom ?? ""))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.leading)
        }
    }

    private var advancedBody: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L10n.tr("pair.accountHint", locale: locale))
                .font(.qasa(12))
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.leading)

            FieldGroup {
                TextFieldRow(
                    label: L10n.tr("pair.accountLabel", locale: locale),
                    text: $accountIdText,
                    isPlain: true,
                    error: errorMessage
                )
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
        BottomActionBar {
            Group {
                if step == .success || step == .adopted {
                    PrimaryButton(
                        title: L10n.tr("pair.done", locale: locale),
                        icon: "checkmark"
                    ) {
                        onPaired()
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
                }
            }
        }
    }

    @MainActor
    private func applyPairing() async {
        errorMessage = nil
        guard let accountId = UUID(uuidString: accountIdText.trimmingCharacters(in: .whitespacesAndNewlines)) else {
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
        step = .success
    }
}
