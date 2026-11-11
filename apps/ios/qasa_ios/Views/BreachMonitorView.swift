import SwiftUI
import SwiftData

// Email leak alerts are parked (todo email-leak-alerts in docs/server-followups-plan.md).
// Nothing opens this screen any more; the Alerts tab shows `BreachedPasswordsView`.
struct BreachMonitorView: View {
    var onOpenMenu: (() -> Void)? = nil
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \MonitoredEmail.createdAt) private var monitored: [MonitoredEmail]

    @State private var monitorEmail = ""
    @State private var selectedTab: BreachStatus = .unresolved
    @State private var pendingRemoval: MonitoredEmail?
    @State private var showLimitAlert = false
    @State private var isChecking = false
    @State private var scanResults: [BreachCheckResult] = []
    @State private var errorMessage: String?
    @State private var statusMessage: String?
    @State private var rotateTarget: RotateTarget?

    private let maxSlots = 10

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(
                title: L10n.tr("nav.breachMonitoring", locale: AppPreferences.shared.locale),
                onMenu: onOpenMenu
            )
            if !session.isPremium {
                premiumGate
            } else {
                premiumContent
            }
        }
        .background(Theme.background.ignoresSafeArea())
        // Dismissal clears the target on the next turn, so the confirm action still sees it.
        .confirmSheet(
            isPresented: Binding(
                get: { pendingRemoval != nil },
                set: { if !$0 { DispatchQueue.main.async { pendingRemoval = nil } } }
            ),
            title: L10n.tr("breach.removeEmailTitle"),
            message: pendingRemoval.map { String(format: L10n.tr("breach.removeEmailMessage"), $0.email) },
            icon: "envelope.badge.shield.half.filled",
            confirmTitle: L10n.tr("common.remove"),
            cancelTitle: L10n.tr("common.cancel"),
            destructive: true
        ) {
            if let pendingRemoval {
                let email = pendingRemoval.email
                modelContext.delete(pendingRemoval)
                try? modelContext.save()
                Task { await BreachMonitorService.unregisterMonitor(email: email) }
            }
            pendingRemoval = nil
        }
        .confirmSheet(
            isPresented: $showLimitAlert,
            title: L10n.tr("breach.slotLimitTitle"),
            message: String(format: L10n.tr("breach.slotLimitMessage", locale: AppLanguage.current.locale), maxSlots),
            icon: "exclamationmark.circle",
            confirmTitle: L10n.tr("common.ok")
        ) {}
        .sheet(item: $rotateTarget) { target in
            RotatePasswordSheet(email: target.email) { itemName in
                errorMessage = nil
                statusMessage = String(format: L10n.tr("breach.rotateSaved"), itemName)
            }
            .appLocalization()
        }
        .onAppear {
            Task { await rescanAll() }
        }
    }

    private var premiumGate: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(L10n.tr("breach.upgradeHint"))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            PrimaryButton(title: L10n.tr("settings.upgradePremium"), icon: "star.fill") {
                onUpgrade()
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, Theme.screenPadding)
        .padding(.top, 24)
    }

    private var premiumContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                slotsHeader
                addEmailCard
                monitoredList
                breachTabs
                breachList
                if let statusMessage {
                    Text(statusMessage)
                        .font(.qasa(13))
                        .foregroundColor(Theme.success)
                }
                if let errorMessage {
                    Text(errorMessage)
                        .font(.qasa(13))
                        .foregroundColor(Theme.danger)
                }
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.top, 24)
            .padding(.bottom, 40)
        }
    }

    private var filteredResults: [BreachCheckResult] {
        scanResults.filter { result in
            selectedTab == .unresolved ? result.breached : !result.breached
        }
    }

    private var slotsHeader: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(L10n.tr("breach.emailMonitoring"))
                    .font(.qasa(22, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Text(String(
                    format: L10n.tr("breach.slotsUsed"),
                    locale: AppLanguage.current.locale,
                    arguments: [monitored.count, maxSlots]
                ))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)
            }
            Spacer()
            StatusPill(
                text: L10n.tr("breach.premium"),
                dotColor: Theme.success,
                textColor: Theme.success,
                background: Theme.cardBackground
            )
        }
    }

    private var addEmailCard: some View {
        CardContainer(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                SectionLabel(text: L10n.tr("breach.addEmail"))
                FieldGroup {
                    TextFieldRow(
                        label: L10n.tr("field.email"),
                        text: $monitorEmail,
                        hint: L10n.tr("field.emailPlaceholder"),
                        isPlain: true,
                        keyboard: .emailAddress,
                        contentType: .emailAddress
                    )
                }

                PrimaryButton(
                    title: isChecking ? L10n.tr("breach.saving") : L10n.tr("breach.addToMonitoring"),
                    icon: "plus"
                ) {
                    Task { await addEmail() }
                }
                .disabled(monitored.count >= maxSlots || isChecking)
            }
        }
    }

    private var monitoredList: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: L10n.tr("breach.monitoredEmails"))
            if monitored.isEmpty {
                Text(L10n.tr("breach.noneMonitored"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
                    .padding(.vertical, 8)
            } else {
                ForEach(monitored, id: \.id) { item in
                    monitoredRow(item)
                }
            }
        }
    }

    private func monitoredRow(_ item: MonitoredEmail) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(item.email)
                    .font(.qasa(15, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text(item.status.title)
                    .font(.qasa(13))
                    .foregroundColor(item.status == .secure ? Theme.success : Theme.danger)
            }
            Spacer()
            Button {
                pendingRemoval = item
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(Theme.chrome)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Theme.strokeChrome, lineWidth: 1))
            }
            .buttonStyle(PressableStyle())
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .frame(minHeight: Theme.rowMinHeight)
        .background(Theme.cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius, style: .continuous)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }

    private var breachTabs: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: L10n.tr("breach.breaches"))
            HStack(spacing: 8) {
                tabChip(.unresolved, title: L10n.tr("breach.unresolved"))
                tabChip(.resolved, title: L10n.tr("breach.resolved"))
            }
        }
    }

    private func tabChip(_ status: BreachStatus, title: String) -> some View {
        let selected = selectedTab == status
        return Button {
            selectedTab = status
        } label: {
            Text(title)
                .font(.qasa(14, weight: .semibold))
                .foregroundColor(selected ? Theme.onAccent : Theme.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Theme.accent : Theme.cardBackground)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Theme.accent : Theme.stroke, lineWidth: 1))
        }
        .buttonStyle(PressableStyle())
    }

    private var breachList: some View {
        VStack(alignment: .leading, spacing: 12) {
            if filteredResults.isEmpty {
                Text(selectedTab == .unresolved ? L10n.tr("breach.noUnresolved") : L10n.tr("breach.noResolved"))
                    .font(.qasa(14))
                    .foregroundColor(Theme.textSecondary)
            } else {
                ForEach(filteredResults, id: \.email) { result in
                    BreachCard(breach: Breach(
                        title: result.sources.first ?? L10n.tr("breach.detected"),
                        breachDate: "—",
                        discoveredDate: L10n.tr("breach.recentScan"),
                        compromisedFields: [L10n.tr("breach.emailsField")],
                        status: result.breached ? .unresolved : .resolved
                    ), onRotate: {
                        statusMessage = nil
                        rotateTarget = RotateTarget(email: result.email)
                    })
                }
            }
        }
    }

    private func addEmail() async {
        guard monitored.count < maxSlots else {
            showLimitAlert = true
            return
        }
        let trimmed = monitorEmail.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard trimmed.contains("@"), trimmed.contains(".") else { return }
        guard !monitored.contains(where: { $0.email.lowercased() == trimmed }) else {
            monitorEmail = ""
            return
        }

        isChecking = true
        defer { isChecking = false }

        do {
            _ = try await BreachMonitorService.registerMonitor(email: trimmed)
            let result = try await BreachMonitorService.checkEmail(trimmed)
            let status: MonitorStatus = result.breached ? .atRisk : .secure
            scanResults.append(result)
            modelContext.insert(MonitoredEmail(email: trimmed, status: status))
            try? modelContext.save()
            monitorEmail = ""
            errorMessage = nil
        } catch {
            errorMessage = L10n.message(for: error)
        }
    }

    private func rescanAll() async {
        guard session.isPremium, !monitored.isEmpty else { return }
        isChecking = true
        defer { isChecking = false }
        scanResults = await BreachMonitorService.checkEmails(monitored.map(\.email))
        for result in scanResults {
            if let item = monitored.first(where: { $0.email.lowercased() == result.email.lowercased() }) {
                item.status = result.breached ? .atRisk : .secure
            }
        }
        try? modelContext.save()
    }
}

private struct RotateTarget: Identifiable {
    let email: String
    var id: String { email }
}

#Preview {
    BreachMonitorView(onOpenMenu: {}, onUpgrade: {})
        .environment(AppSession(isPremium: true))
        .modelContainer(for: VaultSchema.models, inMemory: true)
}
