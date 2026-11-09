import Foundation

/// When to re-sync `AppSession.isPremium` with the server, so the locked screens follow
/// the license mid-session instead of waiting for the next unlock. Pure: the caller
/// passes the clock and the last refresh attempt.
///
/// Two expiry times are known on the phone:
/// - the license token's `exp` (30 days after issue). The check runs `tokenLead` before
///   it, while the token can still authenticate the refresh, and again just after it,
///   when the local license turns Free unless a new token arrived;
/// - the subscription's `expires_at`, when Manage subscription has fetched it. The
///   token can outlive the subscription, so the check runs just after it.
///
/// A due time at or before the last attempt counts as handled. When every one is handled
/// and the user still shows Premium (the refresh failed offline), the check retries every
/// `retryInterval` rather than spinning. A failed refresh never changes the license; only
/// the locally verified token decides, the same rule the breach analyzer applies.
nonisolated enum PremiumRefreshPolicy {
    /// Foreground re-syncs run at most this often, unless a known expiry has passed.
    static let foregroundThrottle: TimeInterval = 15 * 60
    static let retryInterval: TimeInterval = 15 * 60
    static let tokenLead: TimeInterval = 5 * 60
    /// After an expiry, so the server has already settled it.
    static let grace: TimeInterval = 5

    /// When the next scheduled check should run, or nil when none is needed (Free, or no
    /// known expiry). Never earlier than `now`.
    static func nextCheck(
        isPremium: Bool,
        licenseExpiresAt: Date?,
        subscriptionExpiresAt: Date?,
        lastAttempt: Date?,
        now: Date
    ) -> Date? {
        guard isPremium else { return nil }
        var due: [Date] = []
        if let token = licenseExpiresAt {
            due.append(token.addingTimeInterval(-tokenLead))
            due.append(token.addingTimeInterval(grace))
        }
        if let subscription = subscriptionExpiresAt {
            due.append(subscription.addingTimeInterval(grace))
        }
        guard !due.isEmpty else { return nil }
        let pending = due.filter { time in lastAttempt.map { time > $0 } ?? true }
        if let next = pending.min() {
            return max(next, now)
        }
        // Everything due was attempted and the user still shows Premium: retry later.
        return max((lastAttempt ?? now).addingTimeInterval(retryInterval), now)
    }

    /// Whether returning to the foreground should refresh the license now.
    static func shouldRefreshOnForeground(now: Date, lastAttempt: Date?, nextCheck: Date?) -> Bool {
        if let nextCheck, nextCheck <= now { return true }
        guard let lastAttempt else { return true }
        return now.timeIntervalSince(lastAttempt) >= foregroundThrottle
    }

    /// Server timestamps (`expires_at`), with or without fractional seconds.
    static func parseDate(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let plain = ISO8601DateFormatter()
        if let date = plain.date(from: raw) { return date }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: raw)
    }
}
