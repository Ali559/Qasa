import Foundation
import SwiftUI

/// Locks the vault after `AppPreferences.autoLockMinutes` of inactivity
/// (or when returning from background past that threshold).
@MainActor
final class AutoLockMonitor {
    static let shared = AutoLockMonitor()

    static let forceLockNotification = Notification.Name("qasa.forceLock")

    private var lastActivityAt = Date()
    private var backgroundedAt: Date?
    private var timer: Timer?
    private var onLock: (() -> Void)?

    /// Mirrors "is the app frontmost". iOS captures the app-switcher snapshot while the
    /// scene is still `.active` but the UI is covered by system UI, so the shield has to
    /// be driven by this rather than by locking.
    private(set) var isObscured = false

    /// Posted when the UI must be covered because the app is no longer frontmost.
    static let obscureNotification = Notification.Name("qasa.obscure")
    /// Posted when the app is frontmost again and the shield may be lifted.
    static let revealNotification = Notification.Name("qasa.reveal")

    private init() {}

    func start(onLock: @escaping () -> Void) {
        self.onLock = onLock
        noteActivity()
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.evaluateWhileActive()
            }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        onLock = nil
        backgroundedAt = nil
    }

    func noteActivity() {
        lastActivityAt = Date()
    }

    func handleScenePhase(_ phase: ScenePhase) {
        switch phase {
        case .active:
            // Lift the shield only once the app is genuinely frontmost again. Clearing
            // secrets here is not enough: iOS has already taken the snapshot by now.
            setObscured(false)
            if let backgroundedAt {
                let elapsed = Date().timeIntervalSince(backgroundedAt)
                self.backgroundedAt = nil
                if elapsed >= timeoutInterval {
                    fireLock()
                    return
                }
            }
            noteActivity()
        case .inactive:
            // Control Centre, incoming call, notification shade: the UI is about to be
            // captured. Cover it now rather than waiting for `.background`.
            setObscured(true)
        case .background:
            setObscured(true)
            backgroundedAt = Date()
        @unknown default:
            setObscured(true)
        }
    }

    private func setObscured(_ obscured: Bool) {
        guard isObscured != obscured else { return }
        isObscured = obscured
        NotificationCenter.default.post(
            name: obscured ? Self.obscureNotification : Self.revealNotification,
            object: nil
        )
    }

    /// Sign out / lock from Linked Devices (current device).
    func requestLock() {
        fireLock()
    }

    private var timeoutInterval: TimeInterval {
        TimeInterval(AppPreferences.shared.autoLockMinutes * 60)
    }

    private func evaluateWhileActive() {
        guard VaultSession.shared.isUnlocked else { return }
        guard backgroundedAt == nil else { return }
        if Date().timeIntervalSince(lastActivityAt) >= timeoutInterval {
            fireLock()
        }
    }

    private func fireLock() {
        guard VaultSession.shared.isUnlocked else { return }
        if let onLock {
            onLock()
        } else {
            AppBootstrap.onLogout()
            NotificationCenter.default.post(name: Self.forceLockNotification, object: nil)
        }
    }
}
