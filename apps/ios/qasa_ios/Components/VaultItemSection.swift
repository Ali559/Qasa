import SwiftUI

// The vault list (design/README.md rule 6e, option C in `previews/vault-list.html`): each
// section is one card holding its rows, split by a divider that starts at the text; the label
// sits above the card. Used by the Passwords page, a collection's items and the password
// health lists.

/// A section: its label (when given), then its rows in one card. `isFirst` drops the
/// `vaultSectionTop` gap (the list's own `formGap` stands in).
struct VaultSection<Content: View>: View {
    @Environment(\.themePalette) private var theme
    var title: String?
    var isFirst = false
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: QasaTokens.Space.sectionLabelGap) {
            if let title {
                Text(title)
                    .font(.qasa(QasaTokens.Typography.sectionLabel.size, weight: QasaTokens.Typography.sectionLabel.weight))
                    .foregroundColor(theme.textSecondary)
                    .padding(.leading, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityAddTraits(.isHeader)
            }
            VaultSectionCard { content }
        }
        .padding(.top, isFirst ? 0 : QasaTokens.Space.vaultSectionTop)
    }
}

/// One card of vault rows. Put rows directly inside (`ForEach` is fine) so each becomes a row
/// with a divider above it. The card isn't clipped (a row's copy toast may hang below it); the
/// press tint is cut to the card's corners by `VaultRowButtonStyle` instead.
struct VaultSectionCard<Content: View>: View {
    @Environment(\.themePalette) private var theme
    @ViewBuilder var content: Content

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: QasaTokens.Radius.group, style: .continuous)
    }

    var body: some View {
        _VariadicView.Tree(VaultDividedRows()) { content }
            .background(shape.fill(theme.cardBackground))
            .overlay(shape.stroke(theme.stroke, lineWidth: QasaTokens.Size.stroke))
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The divider's leading inset: where a row's text starts (14 + 44 + 14 = 72).
private let vaultDividerInset = QasaTokens.Space.vaultRowHorizontal + QasaTokens.Size.vaultTile + QasaTokens.Space.rowIconGap

private struct VaultDividedRows: _VariadicView_MultiViewRoot {
    func body(children: _VariadicView.Children) -> some View {
        let firstId = children.first?.id
        let lastId = children.last?.id
        VStack(spacing: 0) {
            ForEach(children) { child in
                if child.id != firstId {
                    VaultRowDivider()
                }
                child.environment(\.vaultRowEnds, VaultRowEnds(top: child.id == firstId, bottom: child.id == lastId))
            }
        }
    }
}

private struct VaultRowDivider: View {
    @Environment(\.themePalette) private var theme
    var body: some View {
        Rectangle()
            .fill(theme.stroke)
            .frame(height: QasaTokens.Size.stroke)
            .padding(.leading, vaultDividerInset)
            .accessibilityHidden(true)
    }
}

/// Whether a row is the first and / or last in its card (its press tint takes the card's
/// corners there).
struct VaultRowEnds: Equatable {
    var top = false
    var bottom = false
}

private struct VaultRowEndsKey: EnvironmentKey {
    static let defaultValue = VaultRowEnds()
}

extension EnvironmentValues {
    var vaultRowEnds: VaultRowEnds {
        get { self[VaultRowEndsKey.self] }
        set { self[VaultRowEndsKey.self] = newValue }
    }
}

/// Press feedback for a vault row: only that row tints (`rowPressedOpacity`), cut to the
/// card's corners when it is the first or last row. No dimming, no scale.
struct VaultRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        PressedRow(configuration: configuration)
    }

    private struct PressedRow: View {
        @Environment(\.themePalette) private var theme
        @Environment(\.vaultRowEnds) private var ends
        let configuration: ButtonStyle.Configuration

        var body: some View {
            let r = QasaTokens.Radius.group
            configuration.label
                .background(
                    UnevenRoundedRectangle(
                        topLeadingRadius: ends.top ? r : 0,
                        bottomLeadingRadius: ends.bottom ? r : 0,
                        bottomTrailingRadius: ends.bottom ? r : 0,
                        topTrailingRadius: ends.top ? r : 0,
                        style: .continuous
                    )
                    .fill(theme.textPrimary.opacity(configuration.isPressed ? QasaTokens.Motion.rowPressedOpacity : 0))
                )
                .contentShape(Rectangle())
                .animation(QasaTokens.Motion.press, value: configuration.isPressed)
        }
    }
}

extension ButtonStyle where Self == VaultRowButtonStyle {
    static var vaultRow: VaultRowButtonStyle { VaultRowButtonStyle() }
}
