import SwiftUI
import SwiftData

enum PasswordIssueTab: String, CaseIterable, Identifiable, Hashable {
    case reused
    case weak

    var id: String { rawValue }

    var title: String {
        switch self {
        case .reused: return "Reused"
        case .weak: return "Weak"
        }
    }
}

struct PasswordHealthView: View {
    var onOpenMenu: () -> Void

    @Query(sort: \VaultItemRecord.sortKey) private var items: [VaultItemRecord]
    @State private var animatedProgress: Double = 0
    @State private var animatedScore: Int = 0

    private var report: PasswordHealthReport {
        PasswordHealthAnalyzer.analyze(items: items)
    }

    var body: some View {
        VStack(spacing: 0) {
            AppMenuHeader(title: "Password Health", onMenu: onOpenMenu)

            ScrollView {
                VStack(spacing: 28) {
                    scoreGauge

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        NavigationLink(value: PasswordIssueTab.reused) {
                            statCard(title: "Passwords", value: "\(report.totalPasswords)", accent: Theme.mint)
                        }
                        .buttonStyle(.plain)

                        NavigationLink(value: PasswordIssueTab.reused) {
                            statCard(title: "Reused", value: "\(report.reusedCount)", accent: Theme.gold)
                        }
                        .buttonStyle(.plain)

                        NavigationLink(value: PasswordIssueTab.weak) {
                            statCard(title: "Weak", value: "\(report.weakCount)", accent: Theme.danger)
                        }
                        .buttonStyle(.plain)
                    }

                    CardContainer {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("How scoring works")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundColor(Theme.textPrimary)
                            Text("Score starts at 100 and drops for weak or reused login passwords. Aim for unique, long passwords with mixed character types.")
                                .font(.system(size: 14))
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
                weakItems: report.weakItems
            )
        }
        .onAppear { animateScore() }
        .onChange(of: report.score) { _, _ in animateScore() }
    }

    private var scoreGauge: some View {
        VStack(spacing: 20) {
            ZStack(alignment: .bottom) {
                HalfCircleGauge(progress: animatedProgress)
                    .frame(width: 220, height: 118)

                VStack(spacing: 2) {
                    Text("\(animatedScore)")
                        .font(.system(size: 44, weight: .bold))
                        .foregroundColor(scoreColor(for: animatedScore))
                        .contentTransition(.numericText())
                    Text("out of 100")
                        .font(.kasoMono(12))
                        .foregroundColor(Theme.textSecondary)
                }
                .padding(.bottom, 8)
            }
            .padding(.top, 8)

            Text(scoreLabel(for: animatedScore))
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
    }

    private func scoreColor(for score: Int) -> Color {
        switch score {
        case 80...100: return Theme.mint
        case 50..<80: return Theme.gold
        default: return Theme.danger
        }
    }

    private func scoreLabel(for score: Int) -> String {
        switch score {
        case 90...100: return "Excellent"
        case 75..<90: return "Strong"
        case 50..<75: return "Fair"
        default: return "Needs Attention"
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

    private func statCard(title: String, value: String, accent: Color) -> some View {
        VStack(spacing: 8) {
            Text(value)
                .font(.system(size: 28, weight: .bold))
                .foregroundColor(accent)
            Text(title)
                .font(.system(size: 13))
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
    let reusedItems: [VaultItemRecord]
    let weakItems: [VaultItemRecord]

    @State private var selectedTab: PasswordIssueTab
    @State private var editorMode: EditorPresentation?

    private struct EditorPresentation: Identifiable {
        let id = UUID()
        let mode: VaultItemEditor.Mode
    }

    init(initialTab: PasswordIssueTab, reusedItems: [VaultItemRecord], weakItems: [VaultItemRecord]) {
        self.initialTab = initialTab
        self.reusedItems = reusedItems
        self.weakItems = weakItems
        _selectedTab = State(initialValue: initialTab)
    }

    private var currentItems: [VaultItemRecord] {
        switch selectedTab {
        case .reused: return reusedItems
        case .weak: return weakItems
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ForEach(PasswordIssueTab.allCases) { tab in
                    let count = tab == .reused ? reusedItems.count : weakItems.count
                    let selected = selectedTab == tab
                    Button {
                        selectedTab = tab
                    } label: {
                        Text("\(tab.title) (\(count))")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(selected ? Theme.mintOnPrimary : Theme.textSecondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(selected ? Theme.mint : Theme.cardBackground)
                            .clipShape(Capsule())
                            .overlay(
                                Capsule().stroke(selected ? Theme.mint : Theme.stroke, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(.horizontal, Theme.screenPadding)
            .padding(.vertical, 14)

            ScrollView {
                LazyVStack(spacing: 12) {
                    if currentItems.isEmpty {
                        ContentUnavailableView(
                            "No \(selectedTab.title) Passwords",
                            systemImage: selectedTab == .reused ? "arrow.triangle.2.circlepath" : "exclamationmark.triangle",
                            description: Text(
                                selectedTab == .reused
                                    ? "None of your login passwords are reused."
                                    : "None of your login passwords are marked weak."
                            )
                        )
                        .padding(.top, 48)
                    } else {
                        ForEach(currentItems, id: \.id) { item in
                            NavigationLink {
                                VaultItemDetailView(item: item) {
                                    editorMode = EditorPresentation(mode: .edit(item))
                                } onDeleted: {}
                            } label: {
                                VaultItemRow(item: item) {
                                    editorMode = EditorPresentation(mode: .edit(item))
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
        .navigationTitle("Password Issues")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.visible, for: .navigationBar)
        .sheet(item: $editorMode) { presentation in
            VaultItemEditor(mode: presentation.mode) {
                editorMode = nil
            }
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
                        colors: [Theme.danger, Theme.gold, Theme.mint],
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
