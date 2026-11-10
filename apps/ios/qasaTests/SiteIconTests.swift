import Foundation
import Testing
import UIKit
@testable import qasa

/// Bundled site icons: same cases as Android `SiteIconsTest` and the extension's
/// `siteIcon.test.ts`.
struct SiteIconTests {
    @Test func matchesByRegistrableDomain() {
        #expect(SiteIcon.id(forWebsite: "https://accounts.google.com/signin") == "google")
        #expect(SiteIcon.id(forWebsite: "gmail.com") == "google")
        #expect(SiteIcon.id(forWebsite: "mail.google.co.uk") == "google")
        #expect(SiteIcon.id(forWebsite: "https://www.YouTube.com/") == "youtube")
        #expect(SiteIcon.id(forWebsite: "twitter.com") == "x")
        #expect(SiteIcon.id(forWebsite: "https://www.icloud.com") == "apple")
        #expect(SiteIcon.id(forWebsite: "github.com") == "github")
    }

    @Test func unknownAndLookAlikeSitesKeepTheLetterTile() {
        #expect(SiteIcon.id(forWebsite: "example.com") == nil)
        #expect(SiteIcon.id(forWebsite: "") == nil)
        #expect(SiteIcon.id(forWebsite: "google.com.evil.io") == nil)
        #expect(SiteIcon.id(forWebsite: "notgoogle.com") == nil)
        #expect(SiteIcon.id(forWebsite: "alice.github.io") == nil)
        #expect(SiteIcon.id(forWebsite: "androidapp://com.instagram.android") == nil)
        #expect(SiteIcon.id(forWebsite: "http://10.0.0.1") == nil)
    }

    @Test func everyIconIsInTheAssetCatalog() {
        for id in Set(SiteIconData.domains.values) {
            #expect(UIImage(named: "site-\(id)") != nil, "missing asset site-\(id)")
        }
    }
}
