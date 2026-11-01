import Foundation

enum AppGroup {
    static let identifier = "group.alibarznji.qasa"
    static let keychainAccessGroup = "R6H3Y7XP76.alibarznji.qasa.shared"

    static var containerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }

    static var autofillIndexURL: URL? {
        containerURL?.appendingPathComponent("autofill-index.json", isDirectory: false)
    }
}
