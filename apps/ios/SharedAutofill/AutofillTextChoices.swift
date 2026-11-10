import Foundation

/// What iOS 18 text insertion ("AutoFill → Qasa" in any text field) can insert from one
/// item: one row per value, best first (docs/autofill-plan.md §3.4). Apple lets a provider
/// insert one piece of text at a time, so a card or address is offered field by field.
struct AutofillTextChoice: Identifiable, Equatable, Sendable {
    let id: String
    /// Catalog key and English fallback for the row's label.
    let labelKey: String
    let labelFallback: String
    let value: String
    /// Shown as bullets or `•••• 1234` in the list (the value is still what gets inserted).
    let isSecret: Bool

    var label: String { AutofillL10n.tr(labelKey, labelFallback) }

    /// The row's preview: never a whole card number or code on screen.
    var preview: String {
        guard isSecret else { return value }
        let digits = value.filter(\.isNumber)
        if digits.count >= 8 { return "•••• \(digits.suffix(4))" }
        return String(repeating: "•", count: min(max(value.count, 3), 8))
    }
}

enum AutofillTextChoices {
    static func choices(for secrets: AutofillSecrets) -> [AutofillTextChoice] {
        var out: [AutofillTextChoice] = []
        func add(_ id: String, _ key: String, _ fallback: String, _ raw: String, secret: Bool = false) {
            let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            // A value already offered (an email that is also the username) is listed once.
            guard !value.isEmpty, !out.contains(where: { $0.value == value }) else { return }
            out.append(AutofillTextChoice(id: id, labelKey: key, labelFallback: fallback, value: value, isSecret: secret))
        }
        switch secrets {
        case .login(let l):
            add("username", "field.username", "Username", l.username.isEmpty ? l.email : l.username)
            if !l.username.isEmpty { add("email", "field.email", "Email", l.email) }
            add("password", "field.password", "Password", l.password, secret: true)
        case .card(let c):
            add("number", "autofill.cardNumber", "Card number", c.number.filter(\.isNumber), secret: true)
            add("expiry", "autofill.expiryDate", "Expiry date", c.expiryDate)
            add("cvv", "autofill.cvv", "CVV", c.securityCode, secret: true)
            add("holder", "autofill.nameOnCard", "Name on card", c.accountHolder)
        case .bank(let b):
            add("account", "field.accountNumber", "Account Number", b.accountNumber, secret: true)
            add("routing", "field.routingNumber", "Routing Number", b.routingNumber, secret: true)
            add("holder", "field.accountHolder", "Account Holder", b.accountHolder)
            add("bank", "field.bankName", "Bank Name", b.bankName)
        case .name(let n):
            let full = [n.firstName, n.middleName, n.lastName]
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .joined(separator: " ")
            add("full", "autofill.fullName", "Full name", full)
            add("first", "field.firstName", "First Name", n.firstName)
            add("middle", "field.middleName", "Middle Name", n.middleName)
            add("last", "field.lastName", "Last Name", n.lastName)
        case .email(let e):
            add("email", "field.email", "Email", e.email)
        case .phone(let p):
            let national = p.number.filter(\.isNumber)
            let international = p.number.hasPrefix("+")
                ? p.number
                : p.dialCode + String(national.drop(while: { $0 == "0" }))
            add("international", "field.phoneNumber", "Phone Number", national.isEmpty ? "" : international)
            add("national", "autofill.phoneNational", "Phone number without country code", national)
        case .address(let a):
            add("street", "field.address", "Address", a.address)
            let line2 = [a.building, a.floor, a.apartment]
                .map { $0.trimmingCharacters(in: .whitespaces) }
                .filter { !$0.isEmpty }
                .joined(separator: ", ")
            add("line2", "autofill.addressLine2", "Building, floor, apartment", line2)
            add("city", "field.city", "City", a.city)
            add("zip", "field.zipCode", "Zip Code", a.zipCode)
            add("country", "autofill.country", "Country", a.countryName.isEmpty ? a.countryCode : a.countryName)
            add("recipient", "field.recipient", "Recipient", a.recipient)
        case .company(let c):
            add("company", "field.companyName", "Company Name", c.companyName)
            add("job", "field.jobTitle", "Job Title", c.jobTitle)
        }
        return out
    }
}
