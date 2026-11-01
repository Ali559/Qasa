import SwiftUI
import UniformTypeIdentifiers

struct LANSyncView: View {
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.dismiss) private var dismiss
    @State private var pairCode = ""
    @State private var mode: Mode = .host
    @State private var exportedURL: URL?
    @State private var validationMessage: String?

    enum Mode { case host, join }

    private var sync: LANVaultSyncService { .shared }
    private var locale: Locale { prefs.locale }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if !session.isPremium {
                        Text(L10n.tr("lan.requiresPremium", locale: locale))
                            .foregroundColor(Theme.textSecondary)
                        PrimaryButton(
                            title: L10n.tr("nav.upgrade", locale: locale),
                            icon: "crown.fill",
                            cornerRadius: 8
                        ) {
                            onUpgrade()
                        }
                    } else {
                        premiumBody
                    }
                }
                .padding(Theme.screenPadding)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(L10n.tr("lan.title", locale: locale))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(L10n.tr("common.close", locale: locale)) { dismiss() }
                }
            }
        }
    }

    private var premiumBody: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("lan.secureTitle", locale: locale))
                .font(.qasa(22, weight: .bold))
                .foregroundColor(Theme.textPrimary)
            Text(L10n.tr("lan.secureBody", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)

            Picker(L10n.tr("common.mode", locale: locale), selection: $mode) {
                Text(L10n.tr("lan.hostMode", locale: locale)).tag(Mode.host)
                Text(L10n.tr("lan.joinMode", locale: locale)).tag(Mode.join)
            }
            .pickerStyle(.segmented)

            ThemedTextField(icon: "number", placeholder: "6-digit code", text: $pairCode)

            Text(validationMessage ?? sync.statusMessage)
                .font(.qasaLabel(12))
                .foregroundColor(Theme.accent)

            PrimaryButton(
                title: mode == .host
                    ? L10n.tr("lan.startHost", locale: locale)
                    : L10n.tr("lan.findHost", locale: locale),
                icon: "wifi",
                cornerRadius: 8
            ) {
                runSync()
            }

            Button(L10n.tr("lan.stop", locale: locale)) { sync.stop() }
                .foregroundColor(Theme.danger)
        }
    }

    private func runSync() {
        guard session.isPremium else {
            validationMessage = L10n.tr("lan.requiresPremium", locale: locale)
            return
        }
        let code = pairCode.trimmingCharacters(in: .whitespacesAndNewlines)
        guard code.count >= 4 else {
            validationMessage = L10n.tr("lan.enterCode", locale: locale)
            return
        }
        validationMessage = nil
        if mode == .host {
            let blob = Data("qasa-vault-placeholder:\(VaultKeychainStore.accountId ?? "")".utf8)
            sync.startHost(pairCode: code, vaultBlob: blob)
        } else {
            sync.startBrowse(pairCode: code) { data in
                if let s = String(data: data, encoding: .utf8),
                   s.hasPrefix("qasa-vault-placeholder:"),
                   let id = s.split(separator: ":").last.map(String.init) {
                    VaultKeychainStore.accountId = id
                }
            }
        }
    }
}
