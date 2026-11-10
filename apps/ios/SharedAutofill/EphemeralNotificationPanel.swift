import Combine
import SwiftUI
import UIKit

/// Interactive payment secrets UI shown inside the OS notification content extension.
struct EphemeralNotificationPanel: View {
    let title: String
    let subtitle: String
    let fields: [EphemeralPaymentNotificationSupport.FieldPayload]
    let expiresAt: Date
    var onExpired: (() -> Void)? = nil

    @State private var secondsRemaining: Int = 30
    @State private var lastCopiedFieldId: String?
    @State private var didExpire = false

    private let mint = QasaSharedPalette.accent
    private let danger = QasaSharedPalette.danger

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(QasaFontFace.font(size: 16, weight: .semibold))
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(QasaFontFace.font(size: 13))
                            .foregroundStyle(.secondary)
                    }
                }
                Spacer()
                Text(AutofillL10n.format("autofill.expiresIn", "Expires in %ds", secondsRemaining))
                    .font(QasaFontFace.font(size: 12, weight: .semibold))
                    .foregroundStyle(secondsRemaining <= 5 ? danger : mint)
            }

            ForEach(fields, id: \.id) { field in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(field.label)
                            .font(QasaFontFace.font(size: 11))
                            .foregroundStyle(.secondary)
                        Text(EphemeralPaymentNotificationSupport.displayValue(for: field))
                            .font(.system(size: 14, design: .monospaced))
                            .lineLimit(1)
                    }
                    Spacer()
                    Button {
                        EphemeralPaymentNotificationSupport.copyField(field)
                        lastCopiedFieldId = field.id
                    } label: {
                        Text(
                            lastCopiedFieldId == field.id
                                ? AutofillL10n.tr("autofill.copied", "Copied")
                                : AutofillL10n.tr("autofill.copy", "Copy")
                        )
                        .font(QasaFontFace.font(size: 13, weight: .semibold))
                        .foregroundStyle(mint)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16)
        .environment(\.layoutDirection, layoutDirection)
        .environment(\.locale, AutofillL10n.preferredLocale)
        .onAppear { refreshCountdown() }
        .onReceive(Timer.publish(every: 0.25, on: .main, in: .common).autoconnect()) { _ in
            refreshCountdown()
        }
    }

    private var layoutDirection: LayoutDirection {
        let id = AutofillL10n.preferredLocale.identifier.lowercased()
        if id.hasPrefix("ar") || id.hasPrefix("ckb") || id.hasPrefix("ku") {
            return .rightToLeft
        }
        return .leftToRight
    }

    private func refreshCountdown() {
        let remaining = max(0, Int(ceil(expiresAt.timeIntervalSinceNow)))
        secondsRemaining = remaining
        guard remaining <= 0, !didExpire else { return }
        didExpire = true
        EphemeralPaymentNotificationSupport.removeDeliveredEphemeralNotification()
        EphemeralPaymentNotificationSupport.clearOwnedPasteboardIfNeeded()
        onExpired?()
    }
}
