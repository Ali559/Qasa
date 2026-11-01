import SwiftUI

struct BreachCard: View {
    let breach: Breach
    var onRotate: () -> Void = {}

    private var accent: Color {
        breach.status == .unresolved ? Theme.danger : Theme.success
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Text(breach.title)
                    .font(.qasa(18, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                PillBadge(
                    text: breach.status == .unresolved ? L10n.tr("breach.unresolved") : L10n.tr("breach.resolved"),
                    color: breach.status == .unresolved ? Theme.dangerText : Theme.success
                )
                Spacer(minLength: 0)
            }

            Text(String(format: L10n.tr("breach.breachDate"), breach.breachDate, breach.discoveredDate))
                .font(.qasa(14))
                .foregroundColor(Theme.textSecondary)

            FlowLayout(spacing: 8) {
                Text(L10n.tr("breach.compromised"))
                    .font(.qasaLabel(12))
                    .foregroundColor(Theme.textTertiary)
                    .padding(.trailing, 4)

                ForEach(breach.compromisedFields, id: \.self) { field in
                    compromisedChip(field)
                }
            }
            .padding(.top, 4)

            if breach.status == .unresolved {
                unresolvedAction
            } else {
                resolvedAction
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

    private func compromisedChip(_ field: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: fieldIcon(field))
                .font(.qasa(10))
            Text(field)
                .font(.qasaLabel(12))
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

    private var unresolvedAction: some View {
        Button(action: onRotate) {
            HStack(spacing: 8) {
                Image(systemName: "key.fill")
                Text(L10n.tr("breach.rotatePassword"))
            }
            .font(.qasa(14))
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
    }

    private var resolvedAction: some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark")
            Text(L10n.tr("breach.actionTaken"))
        }
        .font(.qasa(14))
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
