import SwiftUI

/// Pair with an existing Qasa install (adopt account_id) then optionally LAN-sync vault.
struct PairExistingVaultView: View {
    var onPaired: (UUID) -> Void
    var onCancel: () -> Void

    @State private var code = ""
    @State private var accountIdText = ""
    @State private var errorMessage: String?
    @State private var step: Step = .intro

    enum Step {
        case intro, enterCode, success
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button("Cancel", action: onCancel)
                    .foregroundColor(Theme.mint)
                Spacer()
                Text("Link Device")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Spacer()
                Color.clear.frame(width: 60)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case .intro:
                        Text("Already have Qasa on another device?")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        Text("Both devices must be on the same Wi‑Fi. On your other phone open Settings → Linked Devices → Show pairing code, then enter it here. You will not create a second vault.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                        PrimaryButton(title: "Enter pairing code", icon: "qrcode", cornerRadius: 8) {
                            step = .enterCode
                        }
                    case .enterCode:
                        Text("Pairing code")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                        ThemedTextField(
                            icon: "number",
                            placeholder: "6-digit code",
                            text: $code
                        )
                        Text("Or paste account ID from the other device (advanced)")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.textSecondary)
                        ThemedTextField(
                            icon: "person.badge.key",
                            placeholder: "Account UUID",
                            text: $accountIdText
                        )
                        if let errorMessage {
                            Text(errorMessage).foregroundColor(Theme.danger).font(.system(size: 13))
                        }
                        PrimaryButton(title: "Continue", icon: "link", cornerRadius: 8) {
                            applyPairing()
                        }
                    case .success:
                        Text("Account linked")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Theme.mint)
                        Text("Next: unlock or sync the vault over LAN from Settings → Sync on this network (Premium).")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
                .padding(Theme.screenPadding)
            }
        }
        .background(Theme.background.ignoresSafeArea())
    }

    private func applyPairing() {
        errorMessage = nil
        // Dev / LAN pairing: host encodes account UUID in the 6-digit session locally.
        // Production: Multipeer/Bonjour exchange — for now accept explicit UUID paste
        // or derive from a locally stored pending pair map keyed by code.
        if let id = UUID(uuidString: accountIdText.trimmingCharacters(in: .whitespacesAndNewlines)) {
            VaultKeychainStore.accountId = id.uuidString.lowercased()
            step = .success
            onPaired(id)
            return
        }
        let trimmed = code.trimmingCharacters(in: .whitespacesAndNewlines)
        if let pending = PairingSessionStore.shared.resolve(code: trimmed) {
            VaultKeychainStore.accountId = pending.uuidString.lowercased()
            step = .success
            onPaired(pending)
            return
        }
        errorMessage = "Invalid code. Ask the other device to show a fresh pairing code, or paste the account ID."
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
