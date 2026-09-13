import AuthenticationServices
import SwiftUI
import UIKit

final class CredentialProviderViewController: ASCredentialProviderViewController {
    private var hostingController: UIHostingController<CredentialProviderRootView>?

    override func prepareCredentialList(for serviceIdentifiers: [ASCredentialServiceIdentifier]) {
        showRoot(serviceIdentifiers: serviceIdentifiers, preferIdentity: nil)
    }

    override func provideCredentialWithoutUserInteraction(for credentialIdentity: ASPasswordCredentialIdentity) {
        // Always require interaction for unlock + confirmation.
        self.extensionContext.cancelRequest(
            withError: NSError(
                domain: ASExtensionErrorDomain,
                code: ASExtensionError.userInteractionRequired.rawValue
            )
        )
    }

    override func prepareInterfaceToProvideCredential(for credentialIdentity: ASPasswordCredentialIdentity) {
        showRoot(serviceIdentifiers: [credentialIdentity.serviceIdentifier], preferIdentity: credentialIdentity)
    }

    private func showRoot(
        serviceIdentifiers: [ASCredentialServiceIdentifier],
        preferIdentity: ASPasswordCredentialIdentity?
    ) {
        let ids = serviceIdentifiers.map(\.identifier)
        let root = CredentialProviderRootView(
            serviceIdentifiers: ids,
            preferredRecordId: preferIdentity?.recordIdentifier.flatMap(UUID.init(uuidString:)),
            onCancel: { [weak self] in
                self?.extensionContext.cancelRequest(
                    withError: NSError(
                        domain: ASExtensionErrorDomain,
                        code: ASExtensionError.userCanceled.rawValue
                    )
                )
            },
            onProvideLogin: { [weak self] user, password in
                self?.extensionContext.completeRequest(
                    withSelectedCredential: ASPasswordCredential(user: user, password: password),
                    completionHandler: nil
                )
            }
        )
        let host = UIHostingController(rootView: root)
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)
        hostingController = host
    }
}
