import Foundation
import UIKit
import UniformTypeIdentifiers

/// Puts a login's current 2FA code on the pasteboard after AutoFill fills it, so the user
/// can paste it into the site's next screen. Local only (no Universal Clipboard) and gone
/// after a minute, which outlives the code by at most one period.
enum TOTPPasteboard {
    static let lifetime: TimeInterval = 60

    static func copyCurrentCode(stored: String?) {
        guard let totp = TOTP(stored: stored) else { return }
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: totp.code()]],
            options: [
                .localOnly: true,
                .expirationDate: Date().addingTimeInterval(lifetime)
            ]
        )
    }
}
