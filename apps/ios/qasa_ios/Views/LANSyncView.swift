import SwiftUI
import SwiftData

/// Settings → Sync on this network: show a code (this device has the vault) or find a
/// device and type its code. Same behaviour as the desktop's sync screen.
struct LANSyncView: View {
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session
    @Environment(AppPreferences.self) private var prefs
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @State private var sync = WiFiSyncController()
    @State private var mode: Mode = .host

    enum Mode { case host, join }

    private var locale: Locale { prefs.locale }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if !session.isPremium {
                        Text(L10n.tr("lan.requiresPremium", locale: locale))
                            .font(.qasa(15))
                            .foregroundColor(Theme.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    } else {
                        premiumBody
                    }
                }
                .padding(Theme.screenPadding)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.background.ignoresSafeArea())
            .safeAreaInset(edge: .bottom, spacing: 0) {
                if !session.isPremium {
                    BottomActionBar {
                        PrimaryButton(title: L10n.tr("nav.upgrade", locale: locale), icon: "crown.fill") {
                            onUpgrade()
                        }
                    }
                }
            }
            .sheetHeader(L10n.tr("lan.title", locale: locale))
        }
        .onChange(of: mode) { _, newMode in
            sync.stop()
            if newMode == .join { sync.startBrowsing() }
        }
        .onDisappear { sync.stop() }
    }

    private var premiumBody: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("lan.secureTitle", locale: locale))
                .font(.qasa(22, weight: .bold))
                .foregroundColor(Theme.textPrimary)
            Text(L10n.tr("lan.secureBody", locale: locale))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Picker(L10n.tr("common.mode", locale: locale), selection: $mode) {
                Text(L10n.tr("lan.hostMode", locale: locale)).tag(Mode.host)
                Text(L10n.tr("lan.joinMode", locale: locale)).tag(Mode.join)
            }
            .pickerStyle(.segmented)

            switch mode {
            case .host:
                WiFiHostPanel(sync: sync, container: modelContext.container)
            case .join:
                WiFiJoinPanel(sync: sync, container: modelContext.container)
            }

            // Joining, a failure belongs on the code row; hosting has no field, so it gets the line.
            SyncOutcomeView(stage: sync.stage, showsFailure: mode == .host)
        }
    }
}

/// Host side: the code, its countdown, status and Stop.
struct WiFiHostPanel: View {
    let sync: WiFiSyncController
    let container: ModelContainer

    @Environment(AppPreferences.self) private var prefs
    private var locale: Locale { prefs.locale }

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.tr("sync.hostTitle", locale: locale))
                    .font(.qasa(17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(L10n.tr("sync.hostBody", locale: locale))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                if let code = sync.hostCode, let expiresAt = sync.hostExpiresAt {
                    Text(Self.spaced(code))
                        .font(.kasoMono(44, weight: .bold))
                        .foregroundColor(Theme.accent)
                        .environment(\.layoutDirection, .leftToRight)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .accessibilityLabel(code.map(String.init).joined(separator: " "))

                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        let left = max(0, Int(expiresAt.timeIntervalSince(context.date).rounded(.up)))
                        Text(String(format: L10n.tr("sync.codeExpires", locale: locale), left))
                            .font(.qasaLabel(12))
                            .foregroundColor(Theme.textSecondary)
                    }

                    HStack(spacing: 8) {
                        ProgressView()
                        Text(L10n.tr(sync.stage == .connecting ? "sync.connecting" : "sync.waiting", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                    }

                    Button(L10n.tr("lan.stop", locale: locale)) { sync.stop() }
                        .buttonStyle(.qasaSecondary)
                } else {
                    PrimaryButton(
                        title: L10n.tr("lan.startHost", locale: locale),
                        icon: "wifi"
                    ) {
                        sync.startHost(container: container)
                    }
                }
            }
        }
    }

    static func spaced(_ code: String) -> String {
        guard code.count == 6 else { return code }
        return "\(code.prefix(3)) \(code.suffix(3))"
    }
}

/// Join side: the live list of devices showing a code, the code field and Sync.
struct WiFiJoinPanel: View {
    let sync: WiFiSyncController
    let container: ModelContainer

    @Environment(AppPreferences.self) private var prefs
    @State private var selected: WiFiSyncController.Peer?
    @State private var code = ""
    private var locale: Locale { prefs.locale }

    private var canSync: Bool {
        selected != nil && code.filter(\.isNumber).count == 6 && !sync.isJoining
    }

    var body: some View {
        CardContainer {
            VStack(alignment: .leading, spacing: 12) {
                Text(L10n.tr("sync.joinTitle", locale: locale))
                    .font(.qasa(17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                if sync.peers.isEmpty {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(L10n.tr("sync.searching", locale: locale))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                    }
                    Text(L10n.tr("sync.noPeers", locale: locale))
                        .font(.qasa(12))
                        .foregroundColor(Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                } else {
                    VStack(spacing: 0) {
                        ForEach(sync.peers) { peer in
                            Button { selected = peer } label: { peerRow(peer) }
                                .buttonStyle(.qasaRow)
                            if peer != sync.peers.last {
                                RowDivider()
                            }
                        }
                    }
                }

                FieldGroup {
                    TextFieldRow(
                        label: L10n.tr("pair.codeLabel", locale: locale),
                        text: $code,
                        hint: L10n.tr("pair.codePlaceholder", locale: locale),
                        isPlain: true,
                        keyboard: .numberPad,
                        contentType: .oneTimeCode,
                        error: joinFailure
                    )
                }
                .onChange(of: code) { _, new in
                    let digits = String(new.filter(\.isNumber).prefix(6))
                    if digits != new { code = digits }
                }

                PrimaryButton(
                    title: sync.isJoining
                        ? L10n.tr("sync.connecting", locale: locale)
                        : L10n.tr("sync.syncButton", locale: locale),
                    icon: sync.isJoining ? nil : "arrow.triangle.2.circlepath"
                ) {
                    guard canSync, let selected else { return }
                    sync.join(peer: selected, code: code, container: container)
                }
                .disabled(!canSync)
            }
        }
        .onAppear { sync.startBrowsing() }
        .onChange(of: sync.peers) { _, peers in
            if let selected, !peers.contains(selected) { self.selected = nil }
            if selected == nil, peers.count == 1 { selected = peers.first }
        }
    }

    /// A failed join, shown on the code row.
    private var joinFailure: String? {
        if case .failed(let failure) = sync.stage { return failure.message(locale: locale) }
        return nil
    }

    private func peerRow(_ peer: WiFiSyncController.Peer) -> some View {
        SettingsRow(icon: Self.icon(for: peer.platform), title: peer.name) {
            Image(systemName: selected == peer ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 20))
                .foregroundColor(selected == peer ? Theme.accent : Theme.textTertiary)
        }
    }

    private static func icon(for platform: String) -> String {
        switch platform {
        case "ios", "android": return "iphone"
        case "macos": return "laptopcomputer"
        default: return "desktopcomputer"
        }
    }
}

/// The success or failure line under either panel.
struct SyncOutcomeView: View {
    let stage: WiFiSyncController.Stage
    var showsFailure: Bool = true

    @Environment(AppPreferences.self) private var prefs
    private var locale: Locale { prefs.locale }

    var body: some View {
        switch stage {
        case .done(let result):
            VStack(alignment: .leading, spacing: 6) {
                Text(String(
                    format: L10n.tr(result.adopted ? "sync.adoptedTitle" : "sync.doneTitle", locale: locale),
                    result.peerName
                ))
                .font(.qasa(18, weight: .bold))
                .foregroundColor(Theme.accent)
                Text(result.adopted
                     ? String(format: L10n.tr("sync.adoptedBody", locale: locale), result.peerName)
                     : String(format: L10n.tr("sync.doneBody", locale: locale), result.received, result.sent))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
            }
        case .failed(let failure) where showsFailure:
            Text(failure.message(locale: locale))
                .font(.qasa(13, weight: .medium))
                .foregroundColor(Theme.danger)
                .fixedSize(horizontal: false, vertical: true)
        default:
            EmptyView()
        }
    }
}
