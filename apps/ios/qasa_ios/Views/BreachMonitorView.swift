import SwiftUI
import SwiftData

struct BreachMonitorView: View {
    var onOpenMenu: () -> Void = {}

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

    private let maxSlots = 4

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: "Breach Monitoring", onMenu: onOpenMenu)

            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    slotsHeader
                    addEmailCard
                    monitoredList
                    if session.isPremium {
                        breachTabs
                        breachList
                    } else {
                        Text("Upgrade to Premium to run dark-web breach scans.")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                    }

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13))
                            .foregroundColor(Theme.danger)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 24)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .alert("Remove Email?", isPresented: Binding(
            get: { pendingRemoval != nil },
            set: { if !$0 { pendingRemoval = nil } }
        )) {
            Button("Cancel", role: .cancel) { pendingRemoval = nil }
            Button("Remove", role: .destructive) {
                if let pendingRemoval {
                    modelContext.delete(pendingRemoval)
                    try? modelContext.save()
                }
                pendingRemoval = nil
            }
        } message: {
            if let pendingRemoval {
                Text("Stop monitoring \(pendingRemoval.email)?")
            }
        }
        .alert("Slot Limit Reached", isPresented: $showLimitAlert) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("You can monitor up to \(maxSlots) emails on your plan.")
        }
        .onAppear {
            Task { await rescanAll() }
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
                Text("Email Monitoring")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Text("\(monitored.count) of \(maxSlots) slots used")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            }
            Spacer()
            StatusPill(
                text: session.isPremium ? "Premium" : "Locked",
                dotColor: session.isPremium ? Theme.success : Theme.gold,
                textColor: session.isPremium ? Theme.success : Theme.gold,
                background: Theme.cardBackground
            )
        }
    }

    private var addEmailCard: some View {
        CardContainer(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                SectionLabel(text: "Add Email")
                HStack(spacing: 12) {
                    Image(systemName: "at")
                        .foregroundColor(Theme.textSecondary)
                    TextField("Enter email address...", text: $monitorEmail)
                        .textInputAutocapitalization(.never)
                        .keyboardType(.emailAddress)
                        .autocorrectionDisabled()
                        .foregroundColor(Theme.textPrimary)
                        .tint(Theme.mint)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .background(Theme.cardBackgroundLight)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.stroke, lineWidth: 1)
                )

                PrimaryButton(title: isChecking ? "Checking…" : "Add to Monitoring", icon: "plus", cornerRadius: 8) {
                    Task { await addEmail() }
                }
                .opacity(monitored.count >= maxSlots || !session.isPremium ? 0.5 : 1)
                .disabled(!session.isPremium || isChecking)
            }
        }
    }

    private var monitoredList: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "Monitored Emails")
            if monitored.isEmpty {
                Text("No emails monitored yet.")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .padding(.vertical, 8)
            } else {
                ForEach(monitored, id: \.id) { item in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.email)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Text(item.status.title)
                                .font(.system(size: 13))
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
                        .buttonStyle(.plain)
                    }
                    .padding(14)
                    .background(Theme.cardBackground)
                    .cornerRadius(Theme.cornerRadius)
                    .overlay(
                        RoundedRectangle(cornerRadius: Theme.cornerRadius)
                            .stroke(Theme.stroke, lineWidth: 1)
                    )
                }
            }
        }
    }

    private var breachTabs: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(text: "Breaches")
            HStack(spacing: 8) {
                tabChip(.unresolved, title: "Unresolved")
                tabChip(.resolved, title: "Resolved")
            }
        }
    }

    private func tabChip(_ status: BreachStatus, title: String) -> some View {
        let selected = selectedTab == status
        return Button {
            selectedTab = status
        } label: {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(selected ? Theme.mintOnPrimary : Theme.textSecondary)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? Theme.mint : Theme.cardBackground)
                .clipShape(Capsule())
                .overlay(Capsule().stroke(selected ? Theme.mint : Theme.stroke, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var breachList: some View {
        VStack(alignment: .leading, spacing: 12) {
            if filteredResults.isEmpty {
                Text("No \(selectedTab == .unresolved ? "unresolved" : "resolved") breaches.")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            } else {
                ForEach(filteredResults, id: \.email) { result in
                    BreachCard(breach: Breach(
                        title: result.sources.first ?? "Breach detected",
                        breachDate: "—",
                        discoveredDate: "Recent scan",
                        compromisedFields: ["Emails"],
                        status: result.breached ? .unresolved : .resolved
                    ))
                }
            }
        }
    }

    private func addEmail() async {
        guard session.isPremium else { return }
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
            let result = try await BreachMonitorService.checkEmail(trimmed)
            let item = MonitoredEmail(email: trimmed, status: result.breached ? .atRisk : .secure)
            modelContext.insert(item)
            try? modelContext.save()
            scanResults.append(result)
            monitorEmail = ""
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
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

struct BreachCard: View {
    let breach: Breach

    private var accent: Color {
        breach.status == .unresolved ? Theme.danger : Theme.success
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Text(breach.title)
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                PillBadge(
                    text: breach.status.rawValue,
                    color: breach.status == .unresolved ? Theme.dangerText : Theme.success
                )
                Spacer(minLength: 0)
            }

            Text("Breach Date: \(breach.breachDate) • Discovered: \(breach.discoveredDate)")
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)

            FlowLayout(spacing: 8) {
                Text("Compromised:")
                    .font(.kasoMono(12))
                    .tracking(0.6)
                    .foregroundColor(Theme.textTertiary)
                    .padding(.trailing, 4)

                ForEach(breach.compromisedFields, id: \.self) { field in
                    HStack(spacing: 4) {
                        Image(systemName: fieldIcon(field))
                            .font(.system(size: 10))
                        Text(field)
                            .font(.kasoMono(12))
                            .tracking(0.6)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(Theme.cardBackgroundLight)
                    .foregroundColor(Theme.textPrimary)
                    .cornerRadius(4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Theme.stroke, lineWidth: 1)
                    )
                }
            }
            .padding(.top, 4)

            if breach.status == .unresolved {
                Button {} label: {
                    HStack(spacing: 8) {
                        Image(systemName: "key.fill")
                        Text("Rotate Password")
                    }
                    .font(.system(size: 14))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .foregroundColor(Theme.danger)
                    .background(Theme.chrome)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.danger, lineWidth: 1)
                    )
                }
            } else {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark")
                    Text("Action Taken")
                }
                .font(.system(size: 14))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .foregroundColor(Theme.textTertiary)
                .background(Theme.cardBackgroundLight)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.strokeChrome, lineWidth: 1)
                )
            }
        }
        .padding(21)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(accent, lineWidth: 1)
        )
        .overlay(alignment: .top) {
            Rectangle()
                .fill(accent)
                .frame(height: 2)
                .clipShape(
                    UnevenRoundedRectangle(
                        topLeadingRadius: Theme.cornerRadius,
                        bottomLeadingRadius: 0,
                        bottomTrailingRadius: 0,
                        topTrailingRadius: Theme.cornerRadius
                    )
                )
        }
        .opacity(breach.status == .resolved ? 0.8 : 1)
    }

    private func fieldIcon(_ field: String) -> String {
        switch field {
        case "Passwords": return "keyboard"
        case "Emails": return "envelope"
        case "Names": return "person"
        case "Job Titles": return "briefcase"
        default: return "tag"
        }
    }
}

/// Simple wrapping layout for monitored chips / compromised tags.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        arrange(proposal: proposal, subviews: subviews).size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, origin) in result.origins.enumerated() {
            subviews[index].place(
                at: CGPoint(x: bounds.minX + origin.x, y: bounds.minY + origin.y),
                proposal: .unspecified
            )
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, origins: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var origins: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxX: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth, x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            origins.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
            maxX = max(maxX, x)
        }

        return (CGSize(width: maxX, height: y + rowHeight), origins)
    }
}

#Preview {
    BreachMonitorView(onOpenMenu: {})
        .environment(AppSession(isPremium: true))
        .modelContainer(for: VaultSchema.models, inMemory: true)
}
