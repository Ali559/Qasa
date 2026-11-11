import Combine
import Foundation
import SwiftUI
import UIKit
import UniformTypeIdentifiers

@MainActor
final class EphemeralPasteboardSession: ObservableObject {
    static let shared = EphemeralPasteboardSession()
    static let ttlSeconds: TimeInterval = 30

    struct Field: Identifiable, Equatable {
        let id: String
        let label: String
        let value: String
    }

    @Published private(set) var isPresented = false
    @Published private(set) var title = ""
    @Published private(set) var subtitle = ""
    @Published private(set) var fields: [Field] = []
    @Published private(set) var secondsRemaining: Int = 30
    @Published private(set) var lastCopiedFieldId: String?

    private var expiresAt: Date?
    private var timer: Timer?
    private var ownedPasteboardChangeCount: Int?
    private var sessionFingerprint: String?

    private init() {}

    func present(title: String, subtitle: String, fields: [Field], fingerprint: String, copyFieldId: String? = nil) {
        let cleaned = fields.filter { !$0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        // Absolute TTL: reopen only when switching items; sibling copies keep the same countdown.
        if isPresented, sessionFingerprint == fingerprint {
            self.fields = cleaned
            if let copyFieldId, let field = cleaned.first(where: { $0.id == copyFieldId }) {
                copy(field)
            }
            return
        }
        dismiss(clearPasteboard: false)
        self.title = title
        self.subtitle = subtitle
        self.fields = cleaned
        self.sessionFingerprint = fingerprint
        self.isPresented = true
        self.expiresAt = Date().addingTimeInterval(Self.ttlSeconds)
        self.secondsRemaining = Int(Self.ttlSeconds)
        startTimer()
        if let copyFieldId, let field = self.fields.first(where: { $0.id == copyFieldId }) {
            copy(field)
        }
    }

    func present(secrets: AutofillSecrets, copyFieldId: String? = nil) {
        switch secrets {
        case .login, .name, .email, .phone, .address, .company:
            return
        case .card(let c):
            present(
                title: c.itemName.isEmpty ? AutofillL10n.tr("autofill.card", "Card") : c.itemName,
                subtitle: maskedLast4(c.number),
                fields: [
                    Field(id: "holder", label: AutofillL10n.tr("autofill.nameOnCard", "Name on card"), value: c.accountHolder),
                    Field(id: "number", label: AutofillL10n.tr("autofill.cardNumber", "Card number"), value: c.number),
                    Field(id: "cvv", label: AutofillL10n.tr("autofill.cvv", "CVV"), value: c.securityCode),
                    Field(id: "expiry", label: AutofillL10n.tr("autofill.expiryDate", "Expiry date"), value: c.expiryDate)
                ],
                fingerprint: "card:\(c.number)|\(c.securityCode)|\(c.expiryDate)",
                copyFieldId: copyFieldId
            )
        case .bank(let b):
            let fallback = b.bankName.isEmpty ? AutofillL10n.tr("autofill.bank", "Bank") : b.bankName
            present(
                title: b.itemName.isEmpty ? fallback : b.itemName,
                subtitle: maskedLast4(b.accountNumber),
                fields: [
                    Field(id: "account", label: AutofillL10n.tr("field.accountNumber", "Account Number"), value: b.accountNumber),
                    Field(id: "routing", label: AutofillL10n.tr("field.routingNumber", "Routing Number"), value: b.routingNumber),
                    Field(id: "holder", label: AutofillL10n.tr("field.accountHolder", "Account Holder"), value: b.accountHolder),
                    Field(id: "bank", label: AutofillL10n.tr("field.bankName", "Bank Name"), value: b.bankName)
                ],
                fingerprint: "bank:\(b.accountNumber)|\(b.routingNumber)",
                copyFieldId: copyFieldId
            )
        }
    }

    func copy(_ field: Field) {
        // This phone only, and iOS drops it after the drawer's lifetime.
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: field.value]],
            options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(Self.ttlSeconds)]
        )
        ownedPasteboardChangeCount = UIPasteboard.general.changeCount
        lastCopiedFieldId = field.id
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }

    func dismiss(clearPasteboard: Bool = true) {
        timer?.invalidate()
        timer = nil
        expiresAt = nil
        if clearPasteboard {
            clearOwnedPasteboardIfNeeded()
        }
        isPresented = false
        title = ""
        subtitle = ""
        fields = []
        lastCopiedFieldId = nil
        sessionFingerprint = nil
        secondsRemaining = Int(Self.ttlSeconds)
    }

    private func startTimer() {
        timer?.invalidate()
        // Capture only inside the MainActor task — avoids Swift 6 "captured var self" in the Timer callback.
        timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { _ in
            Task { @MainActor [weak self] in
                self?.tick()
            }
        }
    }

    private func tick() {
        guard let expiresAt else { return }
        let remaining = max(0, Int(ceil(expiresAt.timeIntervalSinceNow)))
        secondsRemaining = remaining
        if remaining <= 0 {
            dismiss(clearPasteboard: true)
        }
    }

    private func clearOwnedPasteboardIfNeeded() {
        guard let owned = ownedPasteboardChangeCount else { return }
        if UIPasteboard.general.changeCount == owned {
            UIPasteboard.general.string = ""
        }
        ownedPasteboardChangeCount = nil
    }

    private func maskedLast4(_ raw: String) -> String {
        let digits = raw.filter(\.isNumber)
        let last4 = String(digits.suffix(4))
        return last4.isEmpty ? "" : "•••• \(last4)"
    }
}

enum AutofillL10n {
    private static let languageKey = "qasa.language"

    /// Matches Settings → Language (`qasa.language`), mirrored into the App Group for extensions.
    static var preferredLocale: Locale {
        let raw =
            UserDefaults(suiteName: AppGroup.identifier)?.string(forKey: languageKey)
            ?? UserDefaults.standard.string(forKey: languageKey)
            ?? "en"
        return Locale(identifier: raw)
    }

    static func tr(_ key: String, _ fallback: String) -> String {
        let resource = LocalizedStringResource(
            String.LocalizationValue(stringLiteral: key),
            locale: preferredLocale
        )
        let value = String(localized: resource)
        if value == key || value.isEmpty { return fallback }
        return value
    }

    static func format(_ key: String, _ fallback: String, _ args: CVarArg...) -> String {
        let format = tr(key, fallback)
        return String(format: format, locale: preferredLocale, arguments: args)
    }
}

struct EphemeralSecretDrawer: View {
    @ObservedObject var session: EphemeralPasteboardSession

    private let mint = QasaSharedPalette.accent
    private let cardBg = QasaSharedPalette.card
    private let textPrimary = QasaSharedPalette.textPrimary
    private let textSecondary = QasaSharedPalette.textSecondary
    private let danger = QasaSharedPalette.danger

    var body: some View {
        if session.isPresented {
            VStack {
                Spacer()
                drawerCard
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            .animation(.spring(response: 0.35, dampingFraction: 0.86), value: session.isPresented)
        }
    }

    private var drawerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.title)
                        .font(QasaFontFace.font(size: 16, weight: .semibold))
                        .foregroundColor(textPrimary)
                    if !session.subtitle.isEmpty {
                        Text(session.subtitle)
                            .font(QasaFontFace.font(size: 13))
                            .foregroundColor(textSecondary)
                    }
                }
                Spacer()
                Text(AutofillL10n.format("autofill.expiresIn", "Expires in %ds", session.secondsRemaining))
                    .font(QasaFontFace.font(size: 12, weight: .semibold))
                    .foregroundColor(session.secondsRemaining <= 5 ? danger : mint)
                Button {
                    session.dismiss()
                } label: {
                    QasaIconImage(.close, size: 12)
                        .foregroundColor(textSecondary)
                        .frame(width: 28, height: 28)
                        .background(Color(uiColor: .tertiarySystemFill))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
            }

            ForEach(session.fields) { field in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(field.label)
                            .font(QasaFontFace.font(size: 11))
                            .foregroundColor(textSecondary)
                        Text(displayValue(for: field))
                            .font(.system(size: 14, design: .monospaced))
                            .foregroundColor(textPrimary)
                            .lineLimit(1)
                    }
                    Spacer()
                    Button {
                        session.copy(field)
                    } label: {
                        Text(
                            session.lastCopiedFieldId == field.id
                                ? AutofillL10n.tr("autofill.copied", "Copied")
                                : AutofillL10n.tr("autofill.copy", "Copy")
                        )
                        .font(QasaFontFace.font(size: 13, weight: .semibold))
                        .foregroundColor(mint)
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 6)
            }
        }
        .padding(16)
        .background(cardBg)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(mint.opacity(0.45), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.35), radius: 18, y: 8)
    }

    private func displayValue(for field: EphemeralPasteboardSession.Field) -> String {
        EphemeralPaymentNotificationSupport.displayValue(
            for: .init(id: field.id, label: field.label, value: field.value)
        )
    }
}

extension View {
    func ephemeralSecretDrawer(_ session: EphemeralPasteboardSession) -> some View {
        overlay { EphemeralSecretDrawer(session: session) }
    }

    func ephemeralSecretDrawer() -> some View {
        modifier(EphemeralSecretDrawerModifier())
    }
}

@MainActor
private struct EphemeralSecretDrawerModifier: ViewModifier {
    func body(content: Content) -> some View {
        content.overlay {
            EphemeralSecretDrawer(session: EphemeralPasteboardSession.shared)
        }
    }
}
