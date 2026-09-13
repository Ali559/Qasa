import Foundation

/// Routes card/bank copies through the OS notification tray (30s TTL).
enum PaymentClipboard {
    @MainActor
    static func copy(payload: VaultPayload, preferredFieldId: String? = nil) -> Bool {
        switch payload {
        case .card(let f):
            let secrets = AutofillSecrets.card(
                .init(
                    itemName: f.itemName,
                    accountHolder: f.accountHolder,
                    number: f.number,
                    securityCode: f.securityCode,
                    expiryDate: f.expiryDate
                )
            )
            EphemeralPaymentNotifier.shared.present(secrets: secrets, copyFieldId: preferredFieldId)
            return true
        case .bankAccount(let f):
            let secrets = AutofillSecrets.bank(
                .init(
                    itemName: f.itemName,
                    accountHolder: f.accountHolder,
                    accountNumber: f.accountNumber,
                    routingNumber: f.routingNumber,
                    bankName: f.bankName
                )
            )
            EphemeralPaymentNotifier.shared.present(secrets: secrets, copyFieldId: preferredFieldId)
            return true
        default:
            return false
        }
    }

    static func preferredFieldId(forLabel label: String) -> String? {
        let l = label.lowercased()
        if l.contains("security") || l.contains("cvv") || l.contains("cvc") { return "cvv" }
        if l.contains("expir") { return "expiry" }
        if l.contains("routing") { return "routing" }
        if l.contains("account number") { return "account" }
        if l.contains("number") { return "number" }
        if l.contains("account") && !l.contains("holder") { return "account" }
        if l.contains("holder") { return "holder" }
        if l.contains("bank") { return "bank" }
        return nil
    }
}
