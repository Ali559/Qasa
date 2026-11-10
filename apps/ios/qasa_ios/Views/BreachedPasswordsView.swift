import SwiftUI

/// The Alerts tab: saved passwords that show up in HIBP Pwned Passwords. Premium-only, and
/// checked on device (`PwnedPasswordsService`): only a 5-character hash prefix leaves the phone.
/// Free accounts get `BreachLockedCard` and HIBP is never called.
/// Replaced the Premium email leak alerts, which are parked (todo email-leak-alerts in
/// docs/server-followups-plan.md). Mirrors Android `BreachedPasswordsScreen`.
struct BreachedPasswordsView: View {
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session
    @Bindable private var cache = VaultRepository.shared.cache
    @State private var report: PasswordHealthReport?
    @State private var isChecking = false
    @State private var editorMode: EditorPresentation?

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(
                title: L10n.tr("nav.breachedPasswords", locale: AppPreferences.shared.locale),
                onMenu: nil
            )
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if isLocked {
                        BreachLockedCard(onUpgrade: onUpgrade)
                    } else {
                        summary
                    }
                    if !isLocked, let report, !report.breachedItems.isEmpty {
                        LazyVStack(spacing: 12) {
                            ForEach(report.breachedItems) { row in
                                breachedRow(row, count: report.breachCounts[row.id] ?? 0)
                            }
                        }
                    }
                    privacyCard
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task { await check() }
        .onChange(of: cache.rows) { _, _ in Task { await check() } }
        // Purchase or refresh turns the check on; a lapse drops the results.
        .onChange(of: session.isPremium) { _, _ in Task { await check() } }
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
            .appLocalization()
        }
    }

    private var isLocked: Bool {
        !session.isPremium || report?.breachLocked == true
    }

    @ViewBuilder
    private var summary: some View {
        if let report, !isChecking || report.breachedCount > 0 {
            if report.breachCheckFailed {
                CardContainer(padding: 20) {
                    VStack(alignment: .leading, spacing: 14) {
                        Label(L10n.tr("breached.offline"), systemImage: "wifi.exclamationmark")
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                        checkAgainButton
                    }
                }
            } else if report.breachedCount > 0 {
                CardContainer(padding: 20) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(String(
                            format: L10n.tr("breached.found"),
                            locale: AppLanguage.current.locale,
                            arguments: [report.breachedCount]
                        ))
                        .font(.qasa(18, weight: .semibold))
                        .foregroundColor(Theme.danger)
                        Text(L10n.tr("breached.changeHint"))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            } else {
                CardContainer(padding: 20) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label(L10n.tr("breached.noneTitle"), systemImage: "checkmark.shield.fill")
                            .font(.qasa(18, weight: .semibold))
                            .foregroundColor(Theme.success)
                        Text(L10n.tr("breached.noneBody"))
                            .font(.qasa(14))
                            .foregroundColor(Theme.textSecondary)
                        checkAgainButton
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        } else {
            HStack(spacing: 12) {
                ProgressView()
                Text(L10n.tr("breached.checking"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
            }
            .padding(.vertical, 8)
        }
    }

    private var checkAgainButton: some View {
        Button {
            Task { await check() }
        } label: {
            Label(L10n.tr("breached.checkAgain"), systemImage: "arrow.clockwise")
                .font(.qasa(14, weight: .semibold))
                .foregroundColor(Theme.accent)
        }
        .buttonStyle(PressableStyle())
        .disabled(isChecking)
    }

    private func breachedRow(_ row: VaultListRow, count: Int) -> some View {
        NavigationLink {
            VaultItemDetailView(itemId: row.id) {
                editorMode = EditorPresentation(mode: .edit(row.id))
            } onDeleted: {}
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                VaultItemRow(row: row) {
                    editorMode = EditorPresentation(mode: .edit(row.id))
                } onDeleted: {}
                Text(String(
                    format: L10n.tr("breached.seenCount"),
                    count.formatted(.number.grouping(.automatic).locale(AppLanguage.current.locale))
                ))
                .font(.qasa(13, weight: .medium))
                .foregroundColor(Theme.danger)
                .padding(.leading, 64)
            }
        }
        .buttonStyle(.plain)
    }

    private var privacyCard: some View {
        CardContainer(padding: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text(L10n.tr("breached.privacyTitle"))
                    .font(.qasa(16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(L10n.tr("breached.privacyBody"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func check() async {
        guard session.isPremium else {
            report = nil
            await PwnedPasswordsService.shared.clearCache()
            return
        }
        guard !isChecking else { return }
        isChecking = true
        defer { isChecking = false }
        report = await PasswordHealthAnalyzer.analyzeWithBreaches(
            logins: PasswordHealthAnalyzer.vaultLogins(),
            isPremium: session.isPremium
        )
    }
}

/// What Free accounts see wherever breach results would be: the breached-password check is
/// Premium-only. The button opens the Premium upgrade page.
struct BreachLockedCard: View {
    var onUpgrade: () -> Void

    var body: some View {
        CardContainer(padding: 20) {
            VStack(alignment: .leading, spacing: 10) {
                Label(L10n.tr("breached.lockedTitle"), systemImage: "lock.shield.fill")
                    .font(.qasa(18, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(L10n.tr("breached.lockedBody"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
                PrimaryButton(
                    title: L10n.tr("breached.unlock"),
                    icon: "crown.fill",
                    action: onUpgrade
                )
                .padding(.top, 6)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

#Preview {
    NavigationStack {
        BreachedPasswordsView()
    }
    .environment(AppSession())
    .environment(AppPreferences.shared)
}
