import AuthenticationServices
import SwiftUI
import UIKit

/// The AutoFill extension (docs/autofill-plan.md §3.4). Every request needs the user:
/// nothing is ever returned without the unlock sheet (Face ID or the master password).
///   - Passwords: the QuickType bar and the AutoFill sheet.
///   - One-time codes (iOS 18): the current 2FA code of a login.
///   - Text to insert (iOS 18): "AutoFill → Qasa" in any text field: one value of any item.
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
        let mode: CredentialProviderRootView.Mode = credentialRequest.type == .oneTimeCode ? .oneTimeCode : .password
        showRoot(mode: mode, serviceIdentifiers: [identity.serviceIdentifier], preferredRecordId: identity.recordIdentifier)
    }

    // MARK: one-time codes

    override func prepareOneTimeCodeCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        showRoot(mode: .oneTimeCode, serviceIdentifiers: serviceIdentifiers, preferredRecordId: nil)
    }

    // MARK: text insertion

    override func prepareInterfaceForUserChoosingTextToInsert() {
        let root = TextInsertionView(
            onCancel: { [weak self] in self?.cancel() },
            onInsert: { [weak self] text in
                self?.extensionContext.completeRequest(withTextToInsert: text)
            }
        )
        host(UIHostingController(rootView: root))
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
                self?.extensionContext.completeOneTimeCodeRequest(using: ASOneTimeCodeCredential(code: code))
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
        controller.view.frame = view.bounds
        controller.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(controller.view)
        controller.didMove(toParent: self)
        hostingController = controller
    }
}
