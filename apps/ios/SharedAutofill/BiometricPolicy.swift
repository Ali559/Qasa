import Foundation
import LocalAuthentication

/// Biometric unlock is a convenience on top of the master password, not a replacement:
/// once biometrics are on, the master password is still asked for every
/// `masterPasswordInterval`, in the app and in the AutoFill extension alike.
nonisolated enum BiometricPolicy {
    /// Days between required master-password unlocks (the number shown to the user).
    static let masterPasswordIntervalDays = 14
    static let masterPasswordInterval: TimeInterval = TimeInterval(masterPasswordIntervalDays) * 24 * 60 * 60

    /// Call after every successful unlock, creation or change *with the master password*.
    /// A biometric unlock must not call this.
    static func recordMasterPasswordUnlock(at date: Date = Date()) {
        SharedVaultKeychain.lastMasterPasswordUnlock = date
    }

    static func clearMasterPasswordUnlock() {
        SharedVaultKeychain.lastMasterPasswordUnlock = nil
    }

    /// Whether the next unlock has to use the master password. A missing timestamp is due
    /// (first run after upgrading, or a reset), and so is one in the future: the clock was
    /// rolled back, and trusting it would stretch the interval indefinitely.
    static func isMasterPasswordDue(lastUnlock: Date?, now: Date = Date()) -> Bool {
        guard let lastUnlock else { return true }
        if lastUnlock > now { return true }
        return now.timeIntervalSince(lastUnlock) >= masterPasswordInterval
    }

    static var isMasterPasswordDue: Bool {
        isMasterPasswordDue(lastUnlock: SharedVaultKeychain.lastMasterPasswordUnlock)
    }

    /// The device can evaluate Face ID / Touch ID right now (enrolled, not locked out).
    static var isBiometryAvailable: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
    }
}

/// Biometric state for the extension's unlock UI (`BiometricPolicy`, shared with the app).
enum AutofillBiometricGate {
    /// Biometrics on, but the 14-day master-password unlock is due: hide Face ID / Touch ID.
    static var masterPasswordDue: Bool {
        SharedVaultKeychain.biometricsEnabled && BiometricPolicy.isMasterPasswordDue
    }

    static var showsBiometricButton: Bool {
        SharedVaultKeychain.biometricsEnabled && !BiometricPolicy.isMasterPasswordDue
    }

    static var dueHint: String {
        AutofillL10n.format(
            "auth.unlock.masterPasswordDueFmt",
            "It's been %lld days, so enter your main password to keep it fresh in your mind. Face or fingerprint unlock works again after this.",
            BiometricPolicy.masterPasswordIntervalDays
        )
    }
}
