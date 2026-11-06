import SwiftUI
import SwiftData

enum PasswordIssueTab: String, CaseIterable, Identifiable, Hashable {
    case reused
    case weak
    case breached

    var id: String { rawValue }

    var title: String {
        switch self {
        case .reused: return L10n.tr("health.reused")
        case .weak: return L10n.tr("health.weak")
        case .breached: return L10n.tr("health.breached")
        }
    }
}

struct PasswordHealthView: View {
    var onOpenMenu: (() -> Void)? = nil
    var onUpgrade: () -> Void = {}

    @Environment(AppSession.self) private var session

    @State private var animatedProgress: Double = 0
    @State private var animatedScore: Int = 0
    @State private var report: PasswordHealthReport = .empty

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: L10n.tr("nav.passwordHealth", locale: AppPreferences.shared.locale), onMenu: onOpenMenu)

            ScrollView {
                VStack(spacing: 28) {
                    scoreGauge

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        NavigationLink(value: PasswordIssueTab.reused) {
                            statCard(title: L10n.tr("health.passwords"), value: "\(report.totalPasswords)", accent: Theme.accent)
                        }
                        .buttonStyle(.plain)

                        NavigationLink(value: PasswordIssueTab.reused) {
                            statCard(title: L10n.tr("health.reused"), value: "\(report.reusedCount)", accent: Theme.gold)
                        }
                        .buttonStyle(.plain)

                        NavigationLink(value: PasswordIssueTab.weak) {
                            statCard(title: L10n.tr("health.weak"), value: "\(report.weakCount)", accent: Theme.danger)
                        }
                        .buttonStyle(.plain)

                        if breachLocked {
                            // Premium-only: no count on Free, the card opens the upgrade page.
                            Button(action: onUpgrade) {
                                statCard(title: L10n.tr("health.breached"), systemImage: "lock.fill", accent: Theme.textSecondary)
                            }
                            .buttonStyle(.plain)
                        } else {
                            NavigationLink(value: PasswordIssueTab.breached) {
                                statCard(
                                    title: L10n.tr("health.breached"),
                                    value: report.breachCheckFailed ? "—" : "\(report.breachedCount)",
                                    accent: Theme.danger
                                )
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    if breachLocked {
                        BreachLockedCard(onUpgrade: onUpgrade)
                    } else if report.breachCheckFailed {
                        Text(L10n.tr("breached.offline"))
                            .font(.qasa(13))
                            .foregroundColor(Theme.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    CardContainer {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(L10n.tr("health.howScoring"))
                                .font(.qasa(16, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Text(L10n.tr("health.howScoringBody"))
                                .font(.qasa(14))
                                .foregroundColor(Theme.textSecondary)
                        }
                    }
                }
                .padding(Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .navigationDestination(for: PasswordIssueTab.self) { tab in
            PasswordHealthIssuesView(
                initialTab: tab,
                reusedItems: report.reusedItems,
                weakItems: report.weakItems,
                breachedItems: report.breachedItems,
                breachLocked: breachLocked,
                onUpgrade: onUpgrade
            )
        }
        .onAppear {
            refreshReport()
            animateScore()
        }
        .onChange(of: report.score) { _, _ in animateScore() }
        // Purchase or license refresh runs the check; a lapse rescores without it.
        .onChange(of: session.isPremium) { _, _ in refreshReport() }
    }

    private var breachLocked: Bool {
        !session.isPremium || report.breachLocked
    }

    /// Local report straight away, then again once the Pwned Passwords check is back.
    /// On Free the score has no breach deduction and HIBP is never called.
    private func refreshReport() {
        let logins = PasswordHealthAnalyzer.vaultLogins()
        let isPremium = session.isPremium
        report = PasswordHealthAnalyzer.analyze(logins: logins, breachLocked: !isPremium)
        Task {
            let checked = await PasswordHealthAnalyzer.analyzeWithBreaches(logins: logins, isPremium: isPremium)
            // A check that finishes after the plan changed would bring back stale counts.
            if session.isPremium == isPremium { report = checked }
        }
    }

    private var scoreGauge: some View {
        VStack(spacing: 20) {
            ZStack(alignment: .bottom) {
                HalfCircleGauge(progress: animatedProgress)
                    .frame(width: 220, height: 118)

                VStack(spacing: 2) {
                    Text("\(animatedScore)")
                        .font(.qasa(44, weight: .bold))
                        .foregroundColor(scoreColor(for: animatedScore))
                        .contentTransition(.numericText())
                    Text(L10n.tr("health.outOf100"))
                        .font(.qasaLabel(12))
                        .foregroundColor(Theme.textSecondary)
                }
                .padding(.bottom, 8)
            }
            .padding(.top, 8)

            Text(scoreLabel(for: animatedScore))
                .font(.qasa(18, weight: .semibold))
                .foregroundColor(Theme.textPrimary)

            if breachLocked {
                Text(L10n.tr("health.breachedLocked"))
                    .font(.qasa(13))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private func scoreColor(for score: Int) -> Color {
        switch score {
        case 80...100: return Theme.accent
        case 50..<80: return Theme.gold
        default: return Theme.danger
        }
    }

    private func scoreLabel(for score: Int) -> String {
        switch score {
        case 90...100: return L10n.tr("health.excellent")
        case 75..<90: return L10n.tr("health.strong")
        case 50..<75: return L10n.tr("health.fair")
        default: return L10n.tr("health.needsAttention")
        }
    }

    private func animateScore() {
        animatedProgress = 0
        animatedScore = 0
        let target = report.score
        withAnimation(.easeOut(duration: 1.1)) {
            animatedProgress = Double(target) / 100.0
            animatedScore = target
        }
    }

    private func statCard(title: String, value: String = "", systemImage: String? = nil, accent: Color) -> some View {
        VStack(spacing: 8) {
            if let systemImage {
                Image(systemName: systemImage)
                    .font(.qasa(24, weight: .semibold))
                    .foregroundColor(accent)
                    .frame(height: 34)
            } else {
                Text(value)
                    .font(.qasa(28, weight: .bold))
                    .foregroundColor(accent)
            }
            Text(title)
                .font(.qasa(13))
                .foregroundColor(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 18)
        .background(Theme.cardBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }
}

struct PasswordHealthIssuesView: View {
    let initialTab: PasswordIssueTab
    let reusedItems: [VaultListRow]
    let weakItems: [VaultListRow]
    let breachedItems: [VaultListRow]
    var breachLocked = false
    var onUpgrade: () -> Void = {}

    @State private var selectedTab: PasswordIssueTab
    @State private var editorMode: EditorPresentation?

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    init(
        initialTab: PasswordIssueTab,
        reusedItems: [VaultListRow],
        weakItems: [VaultListRow],
        breachedItems: [VaultListRow],
        breachLocked: Bool = false,
        onUpgrade: @escaping () -> Void = {}
    ) {
        self.initialTab = initialTab
        self.reusedItems = reusedItems
        self.weakItems = weakItems
        self.breachedItems = breachedItems
        self.breachLocked = breachLocked
        self.onUpgrade = onUpgrade
        _selectedTab = State(initialValue: initialTab)
    }

    private var currentItems: [VaultListRow] { items(for: selectedTab) }

    private var emptyCopy: (title: String, icon: String, body: String) {
        switch selectedTab {
        case .reused:
            return (L10n.tr("health.noReusedTitle"), "arrow.triangle.2.circlepath", L10n.tr("health.noReusedBody"))
        case .weak:
            return (L10n.tr("health.noWeakTitle"), "exclamationmark.triangle", L10n.tr("health.noWeakBody"))
        case .breached:
            return (L10n.tr("breached.noneTitle"), "checkmark.shield", L10n.tr("breached.noneBody"))
        }
    }

    private func items(for tab: PasswordIssueTab) -> [VaultListRow] {
        switch tab {
        case .reused: return reusedItems
        case .weak: return weakItems
        case .breached: return breachedItems
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(PasswordIssueTab.allCases) { tab in
                        let count = items(for: tab).count
                        let selected = selectedTab == tab
                        let locked = tab == .breached && breachLocked
                        Button {
                            selectedTab = tab
                        } label: {
                            Text(locked ? tab.title : "\(tab.title) (\(count))")
                                .font(.qasa(14, weight: .semibold))
                                .foregroundColor(selected ? Theme.onAccent : Theme.textSecondary)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 9)
                                .background(selected ? Theme.accent : Theme.cardBackground)
                                .clipShape(Capsule())
                                .overlay(
                                    Capsule().stroke(selected ? Theme.accent : Theme.stroke, lineWidth: 1)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
            }
            .padding(.vertical, 14)

            ScrollView {
                LazyVStack(spacing: 12) {
                    if selectedTab == .breached && breachLocked {
                        BreachLockedCard(onUpgrade: onUpgrade)
                            .padding(.top, 8)
                    } else if currentItems.isEmpty {
                        ContentUnavailableView(
                            emptyCopy.title,
                            systemImage: emptyCopy.icon,
                            description: Text(emptyCopy.body)
                        )
                        .padding(.top, 48)
                    } else {
                        ForEach(currentItems) { row in
                            NavigationLink {
                                VaultItemDetailView(itemId: row.id) {
                                    editorMode = EditorPresentation(mode: .edit(row.id))
                                } onDeleted: {}
                            } label: {
                                VaultItemRow(row: row) {
                                    editorMode = EditorPresentation(mode: .edit(row.id))
                                } onDeleted: {}
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(L10n.tr("health.issuesTitle"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
            .appLocalization()
        }
    }
}

struct HalfCircleGauge: View {
    var progress: Double

    var body: some View {
        ZStack {
            HalfCircleShape()
                .stroke(Theme.stroke, style: StrokeStyle(lineWidth: 14, lineCap: .round))

            HalfCircleShape()
                .trim(from: 0, to: min(max(progress, 0), 1))
                .stroke(
                    AngularGradient(
                        colors: [Theme.danger, Theme.gold, Theme.accent],
                        center: .center,
                        startAngle: .degrees(180),
                        endAngle: .degrees(360)
                    ),
                    style: StrokeStyle(lineWidth: 14, lineCap: .round)
                )
                .animation(.easeOut(duration: 1.1), value: progress)
        }
    }
}

/// Upper semicircle (arch facing down / ∩).
private struct HalfCircleShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        let radius = min(rect.width / 2, rect.height)
        path.addArc(
            center: CGPoint(x: rect.midX, y: rect.minY + radius),
            radius: radius,
            startAngle: .degrees(180),
            endAngle: .degrees(0),
            clockwise: false
        )
        return path
    }
}
