import Foundation

/// The privacy policy and terms of use on qasa-app.com, in the app's language.
enum LegalLinks {
    static func url(locale: Locale, terms: Bool) -> URL {
        let page = terms ? "terms" : "privacy"
        let prefix: String
        switch locale.language.languageCode?.identifier {
        case "ar": prefix = "/ar"
        case "ckb", "ku": prefix = "/ckb"
        default: prefix = ""
        }
        return URL(string: "https://qasa-app.com\(prefix)/\(page)")!
    }
}
