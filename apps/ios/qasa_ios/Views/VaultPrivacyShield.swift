import SwiftUI

/// Opaque cover drawn whenever the app is not frontmost.
///
/// iOS snapshots the UI for the app switcher and Control Centre *before* the app is
/// suspended, so clearing key material in `scenePhase` is not enough — without this,
/// every password visible in a vault row ends up in the system snapshot, readable by
/// anyone who can open the app switcher and from a device backup.
///
/// The shield is driven by `AutoLockMonitor` rather than `scenePhase` directly so the
/// app and the credential-provider host stay in sync.
struct VaultPrivacyShield: View {
    @Environment(AppPreferences.self) private var prefs
    var onTapToReveal: (() -> Void)?

    private var locale: Locale { prefs.locale }

    var body: some View {
        ZStack {
            Theme.background
                .ignoresSafeArea()
            VStack(spacing: 14) {
                Image(systemName: "lock.shield.fill")
                    .font(.qasa(44, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)
                Text(L10n.tr("security.hidden", locale: locale))
                    .font(.qasa(15, weight: .medium))
                    .foregroundColor(Theme.textSecondary)
                if let onTapToReveal {
                    Button {
                        onTapToReveal()
                    } label: {
                        Text(L10n.tr("common.ok", locale: locale))
                            .font(.qasa(14, weight: .semibold))
                            .padding(.horizontal, 18)
                            .padding(.vertical, 8)
                            .background(Theme.cardBackground)
                            .foregroundColor(Theme.textPrimary)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        // `.resistsUnauthorized`, plus a plain opaque fill, keeps the snapshot from
        // capturing the covered content through any live view underneath.
        .accessibilityHidden(true)
    }
}

extension View {
    /// Covers `self` whenever `isActive` is false. Used to shield the whole vault UI.
    func privacyShield(_ isActive: Bool, onTapToReveal: (() -> Void)? = nil) -> some View {
        overlay {
            if !isActive {
                VaultPrivacyShield(onTapToReveal: onTapToReveal)
                    .transition(.opacity)
            }
        }
    }
}
