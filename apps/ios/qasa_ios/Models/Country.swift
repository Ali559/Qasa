import Foundation

struct Country: Identifiable, Hashable {
    let code: String
    let name: String
    let dialCode: String

    var id: String { code }

    /// Country name only — for address, bank, ID, and other non-phone fields.
    var displayName: String { name }

    /// Country name with dial code — for phone number forms.
    var displayNameWithDialCode: String { "\(name) (\(dialCode))" }

    func pickerLabel(showsDialCode: Bool) -> String {
        showsDialCode ? displayNameWithDialCode : displayName
    }

    static let all: [Country] = [
        Country(code: "IQ", name: "Iraq", dialCode: "+964"),
        Country(code: "US", name: "United States", dialCode: "+1"),
        Country(code: "GB", name: "United Kingdom", dialCode: "+44"),
        Country(code: "TR", name: "Turkey", dialCode: "+90"),
        Country(code: "DE", name: "Germany", dialCode: "+49"),
        Country(code: "FR", name: "France", dialCode: "+33"),
        Country(code: "AE", name: "United Arab Emirates", dialCode: "+971"),
        Country(code: "SA", name: "Saudi Arabia", dialCode: "+966"),
        Country(code: "JO", name: "Jordan", dialCode: "+962"),
        Country(code: "KW", name: "Kuwait", dialCode: "+965"),
        Country(code: "QA", name: "Qatar", dialCode: "+974"),
        Country(code: "BH", name: "Bahrain", dialCode: "+973"),
        Country(code: "EG", name: "Egypt", dialCode: "+20"),
        Country(code: "IR", name: "Iran", dialCode: "+98"),
        Country(code: "CA", name: "Canada", dialCode: "+1"),
        Country(code: "AU", name: "Australia", dialCode: "+61"),
        Country(code: "NL", name: "Netherlands", dialCode: "+31"),
        Country(code: "SE", name: "Sweden", dialCode: "+46"),
        Country(code: "NO", name: "Norway", dialCode: "+47"),
        Country(code: "CH", name: "Switzerland", dialCode: "+41"),
        Country(code: "IN", name: "India", dialCode: "+91"),
        Country(code: "PK", name: "Pakistan", dialCode: "+92"),
        Country(code: "CN", name: "China", dialCode: "+86"),
        Country(code: "JP", name: "Japan", dialCode: "+81"),
        Country(code: "KR", name: "South Korea", dialCode: "+82"),
        Country(code: "BR", name: "Brazil", dialCode: "+55"),
        Country(code: "MX", name: "Mexico", dialCode: "+52"),
        Country(code: "ES", name: "Spain", dialCode: "+34"),
        Country(code: "IT", name: "Italy", dialCode: "+39"),
        Country(code: "PL", name: "Poland", dialCode: "+48"),
    ].sorted { $0.name < $1.name }

    static func dialCode(for code: String) -> String {
        all.first { $0.code == code }?.dialCode ?? "+\(code)"
    }

    static func name(for code: String) -> String {
        all.first { $0.code == code }?.name ?? code
    }
}
