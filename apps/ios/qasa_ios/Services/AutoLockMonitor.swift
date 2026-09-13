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
            break
        case .background:
            backgroundedAt = Date()
        @unknown default:
            break
        }
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
