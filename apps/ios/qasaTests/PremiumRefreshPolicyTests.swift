import Foundation
import Testing
@testable import qasa

struct PremiumRefreshPolicyTests {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)
    private let day: TimeInterval = 24 * 60 * 60
    private typealias Policy = PremiumRefreshPolicy

    @Test func freeNeedsNoCheck() {
        #expect(Policy.nextCheck(
            isPremium: false,
            licenseExpiresAt: now.addingTimeInterval(day),
            subscriptionExpiresAt: now.addingTimeInterval(day),
            lastAttempt: nil,
            now: now
        ) == nil)
    }

    @Test func premiumWithoutKnownExpiryNeedsNoCheck() {
        #expect(Policy.nextCheck(
            isPremium: true, licenseExpiresAt: nil, subscriptionExpiresAt: nil, lastAttempt: nil, now: now
        ) == nil)
    }

    @Test func tokenOnlyChecksJustBeforeTokenExpiry() {
        let exp = now.addingTimeInterval(30 * day)
        let next = Policy.nextCheck(
            isPremium: true, licenseExpiresAt: exp, subscriptionExpiresAt: nil, lastAttempt: now, now: now
        )
        #expect(next == exp.addingTimeInterval(-Policy.tokenLead))
    }

    @Test func earlierSubscriptionExpiryWins() {
        let exp = now.addingTimeInterval(30 * day)
        let sub = now.addingTimeInterval(3600)
        let next = Policy.nextCheck(
            isPremium: true, licenseExpiresAt: exp, subscriptionExpiresAt: sub, lastAttempt: now, now: now
        )
        #expect(next == sub.addingTimeInterval(Policy.grace))
    }

    @Test func afterTheLeadCheckTheNextIsJustAfterTokenExpiry() {
        let exp = now.addingTimeInterval(60)
        let leadCheck = exp.addingTimeInterval(-Policy.tokenLead)
        let next = Policy.nextCheck(
            isPremium: true, licenseExpiresAt: exp, subscriptionExpiresAt: nil, lastAttempt: leadCheck, now: now
        )
        #expect(next == exp.addingTimeInterval(Policy.grace))
    }

    @Test func unattemptedPastExpiryIsDueNow() {
        let sub = now.addingTimeInterval(-3600)
        let next = Policy.nextCheck(
            isPremium: true,
            licenseExpiresAt: now.addingTimeInterval(30 * day),
            subscriptionExpiresAt: sub,
            lastAttempt: sub.addingTimeInterval(-60),
            now: now
        )
        #expect(next == now)
    }

    @Test func failedRefreshRetriesLaterInsteadOfSpinning() {
        // Subscription ended, the refresh just failed offline, the token still says Premium.
        let token = now.addingTimeInterval(-10)
        let sub = now.addingTimeInterval(-120)
        let next = Policy.nextCheck(
            isPremium: true, licenseExpiresAt: token, subscriptionExpiresAt: sub, lastAttempt: now, now: now
        )
        #expect(next == now.addingTimeInterval(Policy.retryInterval))
    }

    @Test func nextCheckIsNeverInThePast() throws {
        let next = try #require(Policy.nextCheck(
            isPremium: true,
            licenseExpiresAt: now.addingTimeInterval(-day),
            subscriptionExpiresAt: nil,
            lastAttempt: now.addingTimeInterval(-2 * day),
            now: now
        ))
        #expect(next >= now)
    }

    @Test func foregroundIsThrottled() {
        let future = now.addingTimeInterval(day)
        #expect(!Policy.shouldRefreshOnForeground(
            now: now, lastAttempt: now.addingTimeInterval(-60), nextCheck: future
        ))
        #expect(!Policy.shouldRefreshOnForeground(
            now: now, lastAttempt: now.addingTimeInterval(-Policy.foregroundThrottle + 1), nextCheck: nil
        ))
        #expect(Policy.shouldRefreshOnForeground(
            now: now, lastAttempt: now.addingTimeInterval(-Policy.foregroundThrottle), nextCheck: future
        ))
        #expect(Policy.shouldRefreshOnForeground(now: now, lastAttempt: nil, nextCheck: nil))
    }

    @Test func foregroundAlwaysRefreshesOncePassedExpiry() {
        #expect(Policy.shouldRefreshOnForeground(
            now: now, lastAttempt: now.addingTimeInterval(-60), nextCheck: now.addingTimeInterval(-1)
        ))
        #expect(Policy.shouldRefreshOnForeground(
            now: now, lastAttempt: now.addingTimeInterval(-60), nextCheck: now
        ))
    }

    @Test func parsesServerTimestamps() {
        #expect(Policy.parseDate("2027-01-02T03:04:05Z") == Date(timeIntervalSince1970: 1_798_859_045))
        let fractional = Policy.parseDate("2027-01-02T03:04:05.250Z")?.timeIntervalSince1970 ?? 0
        #expect(abs(fractional - 1_798_859_045.25) < 0.001)
        #expect(Policy.parseDate("2027-01-02T03:04:05+00:00") != nil)
        #expect(Policy.parseDate("not a date") == nil)
        #expect(Policy.parseDate(nil) == nil)
    }
}
