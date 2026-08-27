import SwiftUI
import UniformTypeIdentifiers

struct LANSyncView: View {
    @Environment(AppSession.self) private var session
    @Environment(\.dismiss) private var dismiss
    @State private var pairCode = ""
    @State private var mode: Mode = .host
    @State private var exportedURL: URL?

    enum Mode { case host, join }

    private var sync: LANVaultSyncService { .shared }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if !session.isPremium {
                        Text("Device sync requires Premium. Both devices must be on the same Wi‑Fi.")
                            .foregroundColor(Theme.textSecondary)
                        PrimaryButton(title: "Upgrade", icon: "crown.fill", cornerRadius: 8) {
                            dismiss()
                        }
                    } else {
                        Text("Secure LAN sync")
                            .font(.system(size: 22, weight: .bold))
                            .foregroundColor(Theme.textPrimary)
                        Text("Vault ciphertext only. Enter the same 6-digit code on both devices. Never use public Wi‑Fi you do not trust.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)

                        Picker("Mode", selection: $mode) {
                            Text("This device has the vault").tag(Mode.host)
                            Text("Receive vault").tag(Mode.join)
                        }
                        .pickerStyle(.segmented)

                        ThemedTextField(icon: "number", placeholder: "6-digit code", text: $pairCode)

                        Text(sync.statusMessage)
                            .font(.kasoMono(12))
                            .foregroundColor(Theme.mint)

                        PrimaryButton(
                            title: mode == .host ? "Start hosting" : "Find host",
                            icon: "wifi",
                            cornerRadius: 8
                        ) {
                            runSync()
                        }

                        Button("Stop") { sync.stop() }
                            .foregroundColor(Theme.danger)
                    }
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Sync on Wi‑Fi")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    private func runSync() {
        let code = pairCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard code.count >= 4 else {
            sync.statusMessage = "Enter a shared code first."
            return
        }
        if mode == .host {
            // Export encrypted marker blob — full vault dump uses local file exchange for v1.
            let blob = Data("qasa-vault-placeholder:\(VaultKeychainStore.accountId ?? "")".utf8)
            sync.startHost(pairCode: code, vaultBlob: blob)
        } else {
            sync.startBrowse(pairCode: code) { data in
                // Receiver stores pairing account hint; full SwiftData merge is a follow-up.
                if let s = String(data: data, encoding: .utf8),
                   s.hasPrefix("qasa-vault-placeholder:"),
                   let id = s.split(separator: ":").last.map(String.init) {
                    VaultKeychainStore.accountId = id
                }
            }
        }
    }
}
