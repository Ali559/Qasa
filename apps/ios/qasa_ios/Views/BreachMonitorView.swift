import SwiftUI

struct BreachMonitorView: View {
    @State private var monitorEmail = ""

    let monitored: [(email: String, ok: Bool)] = [
        ("admin@kaso.io", true),
        ("operations@kaso.io", false)
    ]

    let breaches: [Breach] = [
        Breach(title: "Collection #1 Dump", breachDate: "Jan 17, 2019", discoveredDate: "Jan 18, 2019", compromisedFields: ["Passwords", "Emails"], status: .unresolved),
        Breach(title: "LinkedIn Data Scraping", breachDate: "Jun 22, 2021", discoveredDate: "Jun 29, 2021", compromisedFields: ["Names", "Job Titles"], status: .resolved)
    ]

    var body: some View {
        VStack(spacing: 0) {
            KasoBrandHeader(
                trailing: AnyView(
                    StatusPill(
                        text: "Secure",
                        dotColor: Theme.success,
                        textColor: Theme.success,
                        background: Theme.cardBackground
                    )
                )
            )

            ScrollView {
                VStack(alignment: .leading, spacing: 32) {
                    pageHeader
                    monitorCard
                    actionBanner
                    leakHistory
                }
                .padding(.horizontal, Theme.screenPadding)
                .padding(.top, 32)
                .padding(.bottom, 40)
            }
        }
        .background(Theme.chrome.ignoresSafeArea())
    }

    private var pageHeader: some View {
        HStack(spacing: 16) {
            Image(systemName: "exclamationmark.shield.fill")
                .font(.system(size: 22))
                .foregroundColor(Theme.mint)
                .frame(width: 44, height: 44)
                .background(Theme.cardBackground)
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Theme.stroke, lineWidth: 1)
                )

            VStack(alignment: .leading, spacing: 4) {
                Text("Breach Monitor")
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundColor(Theme.textPrimary)
                Text("k-Anonymity privacy-preserving leak detection.")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            }
        }
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.stroke).frame(height: 1)
        }
    }

    private var monitorCard: some View {
        CardContainer(padding: 25) {
            VStack(alignment: .leading, spacing: 16) {
                SectionLabel(text: "Add Target to Monitor")

                HStack(spacing: 12) {
                    Image(systemName: "at")
                        .foregroundColor(Theme.textSecondary)
                    TextField("Enter email address...", text: $monitorEmail)
                        .foregroundColor(Theme.textPrimary)
                        .tint(Theme.mint)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 18)
                .background(Theme.cardBackgroundLight)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.stroke, lineWidth: 1)
                )

                PrimaryButton(title: "Check Leaks", icon: "magnifyingglass", cornerRadius: 8) {}

                FlowLayout(spacing: 8) {
                    Text("Monitored:")
                        .font(.kasoMono(12))
                        .tracking(0.6)
                        .foregroundColor(Theme.textTertiary)
                        .padding(.trailing, 4)

                    ForEach(monitored, id: \.email) { item in
                        HStack(spacing: 8) {
                            Text(item.email)
                                .font(.system(size: 14))
                                .foregroundColor(Theme.textPrimary)
                            Image(systemName: item.ok ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                                .font(.system(size: 12))
                                .foregroundColor(item.ok ? Theme.success : Theme.danger)
                        }
                        .padding(.horizontal, 13)
                        .padding(.vertical, 7)
                        .background(Theme.cardBackgroundLight)
                        .cornerRadius(4)
                        .overlay(
                            RoundedRectangle(cornerRadius: 4)
                                .stroke(Theme.stroke, lineWidth: 1)
                        )
                    }
                }
            }
        }
    }

    private var actionBanner: some View {
        HStack(alignment: .top, spacing: 16) {
            Image(systemName: "exclamationmark.circle.fill")
                .foregroundColor(Theme.dangerText)
                .font(.system(size: 20))
            VStack(alignment: .leading, spacing: 4) {
                Text("Action Required")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Theme.dangerText)
                Text("1 active breach detected for monitored addresses. Immediate password rotation recommended.")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.dangerTextMuted)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.dangerBackground)
        .cornerRadius(Theme.cornerRadius)
        .overlay(
            RoundedRectangle(cornerRadius: Theme.cornerRadius)
                .stroke(Theme.danger.opacity(0.3), lineWidth: 1)
        )
        .shadow(color: Theme.dangerBackground.opacity(0.4), radius: 10)
    }

    private var leakHistory: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: "clock.arrow.circlepath")
                    .foregroundColor(Theme.textSecondary)
                Text("Leak History")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
            }
            ForEach(breaches) { breach in
                BreachCard(breach: breach)
            }
        }
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
                    .font(.system(size: 20, weight: .semibold))
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
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
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
    BreachMonitorView()
}
