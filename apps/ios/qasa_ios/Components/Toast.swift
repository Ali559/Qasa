import SwiftUI
import UIKit

/// App-wide confirmation toasts ("Gmail added", "Changes saved"). One `ToastHost` sits at
/// the app root, so a toast fired from inside a sheet is still on screen after the sheet
/// closes. Fire one with `ToastCenter.shared.show(_:)`.
@Observable
@MainActor
final class ToastCenter {
    static let shared = ToastCenter()

    struct Toast: Identifiable, Equatable {
        let id = UUID()
        let message: String
        let systemImage: String
    }

    /// How long a toast stays up before it dismisses itself.
    static let displayDuration: Duration = .milliseconds(2500)

    private(set) var current: Toast?
    @ObservationIgnored private var dismissTask: Task<Void, Never>?

    private init() {}

    /// Shows `message` (replacing any toast already up) and announces it to VoiceOver.
    func show(_ message: String, systemImage: String = "checkmark.circle.fill") {
        let toast = Toast(message: message, systemImage: systemImage)
        current = toast
        dismissTask?.cancel()
        dismissTask = Task { [weak self] in
            // Let the sheet's dismissal (a screen change VoiceOver reacts to) settle first,
            // or the announcement gets cut off.
            try? await Task.sleep(for: .milliseconds(450))
            guard !Task.isCancelled else { return }
            Self.announce(message)
            try? await Task.sleep(for: Self.displayDuration - .milliseconds(450))
            guard !Task.isCancelled, self?.current?.id == toast.id else { return }
            self?.current = nil
        }
    }

    func dismiss() {
        dismissTask?.cancel()
        dismissTask = nil
        current = nil
    }

    private static func announce(_ message: String) {
        guard UIAccessibility.isVoiceOverRunning else { return }
        var text = AttributedString(message)
        text.accessibilitySpeechAnnouncementPriority = .high
        AccessibilityNotification.Announcement(text).post()
    }
}

/// The toast itself: accent fill, `onAccent` text, a checkmark, the control corner radius
/// and a soft shadow. `HStack` order follows the layout direction, so it mirrors in RTL.
struct ToastView: View {
    @Environment(\.themePalette) private var theme
    let toast: ToastCenter.Toast

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: toast.systemImage)
                .font(.system(size: QasaTokens.Size.toastGlyph, weight: .semibold))
                .accessibilityHidden(true)
            Text(toast.message)
                .font(.qasa(QasaTokens.Typography.toast.size, weight: QasaTokens.Typography.toast.weight))
                .lineLimit(2)
                .multilineTextAlignment(.leading)
        }
        .foregroundColor(theme.onAccent)
        .padding(.horizontal, QasaTokens.Space.toastHorizontal)
        .padding(.vertical, QasaTokens.Space.toastVertical)
        .frame(minHeight: QasaTokens.Size.toastMinHeight)
        .background(
            RoundedRectangle(cornerRadius: Theme.controlCornerRadius, style: .continuous)
                .fill(theme.accent)
        )
        .shadow(color: QasaTokens.Shadow.toast.color, radius: QasaTokens.Shadow.toast.radius, x: QasaTokens.Shadow.toast.x, y: QasaTokens.Shadow.toast.y)
        .accessibilityElement(children: .combine)
    }
}

private struct ToastHostModifier: ViewModifier {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var center: ToastCenter
    var bottomClearance: CGFloat

    func body(content: Content) -> some View {
        content.overlay(alignment: .bottom) {
            ZStack {
                if let toast = center.current {
                    ToastView(toast: toast)
                        .id(toast.id)
                        .onTapGesture { center.dismiss() }
                        .padding(.horizontal, Theme.screenPadding)
                        .padding(.bottom, bottomClearance)
                        // Reduce Motion: fade only, no slide.
                        .transition(
                            reduceMotion
                                ? .opacity
                                : .move(edge: .bottom).combined(with: .opacity)
                        )
                }
            }
            .animation(
                reduceMotion ? .easeInOut(duration: 0.2) : .spring(response: 0.35, dampingFraction: 0.85),
                value: center.current?.id
            )
        }
    }
}

extension View {
    /// Hosts `ToastCenter` toasts at the bottom of this view, inside the safe area and
    /// `bottomClearance` points above it (enough to clear the tab bar or a bottom action bar).
    /// Apply once, at the app root.
    func toastHost(_ center: ToastCenter = .shared, bottomClearance: CGFloat = 76) -> some View {
        modifier(ToastHostModifier(center: center, bottomClearance: bottomClearance))
    }
}
