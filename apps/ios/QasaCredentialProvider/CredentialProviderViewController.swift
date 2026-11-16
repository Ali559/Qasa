import AuthenticationServices
import SwiftUI
import UIKit

/// The AutoFill extension (docs/autofill-plan.md §3.4). Every request needs the user:
/// nothing is ever returned without the unlock sheet (Face ID or the master password).
///   - Passwords: the QuickType bar and the AutoFill sheet.
///   - One-time codes (iOS 18+; skipped on iOS 17): the current 2FA code of a login.
///   - Text to insert (iOS 18): "AutoFill → Qasa" in any text field: one value of any item,
///     or a strong password (before iOS 26.2 the only way to suggest one).
///   - Generated passwords (iOS 26.2): a new-password field asks for a strong password, with
///     no UI; nothing of the vault is needed (design rule 6c).
///   - Saving a login (iOS 26.2): after an unlock, sealed into `PendingSaveInbox` for the app.
final class CredentialProviderViewController: ASCredentialProviderViewController {
    private var hostingController: UIViewController?

    // MARK: passwords

    override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        showRoot(mode: .password, serviceIdentifiers: serviceIdentifiers, preferredRecordId: nil)
    }

    override func provideCredentialWithoutUserInteraction(for credentialIdentity: ASPasswordCredentialIdentity) {
        requireInteraction()
    }

    override func provideCredentialWithoutUserInteraction(for credentialRequest: any ASCredentialRequest) {
        requireInteraction()
    }

    override func prepareInterfaceToProvideCredential(for credentialIdentity: ASPasswordCredentialIdentity) {
        showRoot(mode: .password, serviceIdentifiers: [credentialIdentity.serviceIdentifier], preferredRecordId: credentialIdentity.recordIdentifier)
    }

    /// The request-based entry point iOS uses for identities registered with
    /// `replaceCredentialIdentityEntries`: a password or a one-time code.
    override func prepareInterfaceToProvideCredential(for credentialRequest: any ASCredentialRequest) {
        let identity = credentialRequest.credentialIdentity
        var mode: CredentialProviderRootView.Mode = .password
        if #available(iOS 18.0, *), credentialRequest.type == .oneTimeCode { mode = .oneTimeCode }
        showRoot(mode: mode, serviceIdentifiers: [identity.serviceIdentifier], preferredRecordId: identity.recordIdentifier)
    }

    // MARK: one-time codes

    @available(iOS 18.0, *)
    override func prepareOneTimeCodeCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        showRoot(mode: .oneTimeCode, serviceIdentifiers: serviceIdentifiers, preferredRecordId: nil)
    }

    // MARK: text insertion

    @available(iOS 18.0, *)
    override func prepareInterfaceForUserChoosingTextToInsert() {
        let root = TextInsertionView(
            onCancel: { [weak self] in self?.cancel() },
            onInsert: { [weak self] text in
                self?.extensionContext.completeRequest(withTextToInsert: text)
            }
        )
        host(UIHostingController(rootView: root))
    }

    // MARK: generated passwords and saving (iOS 26.2, design rule 6c)

    /// A new-password field: a strong password from the shared generator (and an alphanumeric
    /// alternative), within the new field's, the confirm field's and Apple's quirk
    /// `passwordrules`; empty rules count as none (R2). The system shows them; Info.plist opts
    /// out of our own generate UI.
    @available(iOS 26.2, *)
    override func performWithoutUserInteraction(generatePasswordsRequest: ASGeneratePasswordsRequest) {
        let rules = StrongPassword.rules(
            password: generatePasswordsRequest.passwordFieldPasswordRules,
            confirm: generatePasswordsRequest.confirmPasswordFieldPasswordRules,
            quirks: generatePasswordsRequest.passwordRulesFromQuirks
        )
        let results = StrongPassword.suggestions(rules: rules).map { suggestion in
            ASGeneratedPassword(kind: suggestion.kind == .strong ? .strong : .alphanumeric, value: suggestion.value)
        }
        extensionContext.completeGeneratePasswordRequest(results: results, completionHandler: nil)
    }

    /// Only reachable if the generate UI were on (it is off in Info.plist): same answer.
    @available(iOS 26.2, *)
    override func prepareInterface(for generatePasswordsRequest: ASGeneratePasswordsRequest) {
        performWithoutUserInteraction(generatePasswordsRequest: generatePasswordsRequest)
    }

    /// Saving needs the vault key, which needs the user: always ask for the save sheet. A
    /// generated password filled into a form not yet sent is kept as a pending account (R4:
    /// the app offers it on its next unlock if the submit's own save never comes) and the
    /// request completes; Apple says not to ask the user anything for this event.
    @available(iOS 26.2, *)
    override func performWithoutUserInteractionIfPossible(savePasswordRequest: ASSavePasswordRequest) {
        if savePasswordRequest.event == .generatedPasswordFilled {
            GeneratedPasswordStore.recordFilled(
                savePasswordRequest.credential.password,
                host: AutofillContextMatcher.normalizeHost(savePasswordRequest.serviceIdentifier.identifier)
            )
            extensionContext.completeSavePasswordRequest(completionHandler: nil)
        } else {
            requireInteraction()
        }
    }

    @available(iOS 26.2, *)
    override func prepareInterface(for savePasswordRequest: ASSavePasswordRequest) {
        let service = savePasswordRequest.serviceIdentifier
        let site = savePasswordRequest.title ?? service.displayName ?? Self.host(of: service.identifier)
        let credential = savePasswordRequest.credential
        let root = SavePasswordView(
            site: site,
            username: credential.user,
            onCancel: { [weak self] in self?.cancel() },
            onSave: { [weak self] session in
                let login = PendingSaveInbox.Login(
                    title: site,
                    website: service.identifier,
                    username: credential.user,
                    password: credential.password,
                    savedAt: Date()
                )
                do {
                    try PendingSaveInbox.append(login, key: session.encKey)
                    // Saved (the app imports it): no "save the password you created" later.
                    GeneratedPasswordStore.forget(password: credential.password)
                    self?.extensionContext.completeSavePasswordRequest(completionHandler: nil)
                } catch {
                    self?.extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.failed.rawValue))
                }
            }
        )
        host(UIHostingController(rootView: root))
    }

    /// `https://www.example.com/login` → `example.com`; anything else as given.
    private static func host(of identifier: String) -> String {
        let host = URL(string: identifier)?.host ?? identifier
        return host.hasPrefix("www.") ? String(host.dropFirst(4)) : host
    }

    // MARK: -

    private func requireInteraction() {
        extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.userInteractionRequired.rawValue))
    }

    private func cancel() {
        extensionContext.cancelRequest(withError: NSError(domain: ASExtensionErrorDomain, code: ASExtensionError.userCanceled.rawValue))
    }

    private func showRoot(
        mode: CredentialProviderRootView.Mode,
        serviceIdentifiers: [ASCredentialServiceIdentifier],
        preferredRecordId: String?
    ) {
        let root = CredentialProviderRootView(
            mode: mode,
            serviceIdentifiers: serviceIdentifiers.map(\.identifier),
            preferredRecordId: preferredRecordId.flatMap(UUID.init(uuidString:)),
            onCancel: { [weak self] in self?.cancel() },
            onProvideLogin: { [weak self] user, password in
                self?.extensionContext.completeRequest(
                    withSelectedCredential: ASPasswordCredential(user: user, password: password),
                    completionHandler: nil
                )
            },
            onProvideCode: { [weak self] code in
                // Code mode is only ever entered on iOS 18+, where these APIs exist.
                if #available(iOS 18.0, *) {
                    self?.extensionContext.completeOneTimeCodeRequest(using: ASOneTimeCodeCredential(code: code))
                }
            }
        )
        host(UIHostingController(rootView: root))
    }

    private func host(_ controller: UIViewController) {
        if let old = hostingController {
            old.willMove(toParent: nil)
            old.view.removeFromSuperview()
            old.removeFromParent()
        }
        addChild(controller)
        // Brand accent for SwiftUI controls and bar buttons.
        controller.view.tintColor = UIColor(QasaSharedPalette.accent)
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(controller.view)
        controller.didMove(toParent: self)
        hostingController = controller
    }
}
