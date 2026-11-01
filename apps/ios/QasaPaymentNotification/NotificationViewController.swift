import SwiftUI
import UIKit
import UserNotifications
import UserNotificationsUI

final class NotificationViewController: UIViewController, UNNotificationContentExtension {
    private var host: UIHostingController<EphemeralNotificationPanel>?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
    }

    func didReceive(_ notification: UNNotification) {
        let info = notification.request.content.userInfo
        let title = EphemeralPaymentNotificationSupport.title(from: info)
        let subtitle = EphemeralPaymentNotificationSupport.subtitle(from: info)
        let fields = EphemeralPaymentNotificationSupport.fields(from: info)
        let expiresAt = EphemeralPaymentNotificationSupport.expiresAt(from: info)
            ?? Date().addingTimeInterval(30)

        let panel = EphemeralNotificationPanel(
            title: title,
            subtitle: subtitle,
            fields: fields,
            expiresAt: expiresAt,
            onExpired: { [weak self] in
                self?.extensionContext?.dismissNotificationContentExtension()
            }
        )
        let hosting = UIHostingController(rootView: panel)
        hosting.view.backgroundColor = .clear

        host?.willMove(toParent: nil)
        host?.view.removeFromSuperview()
        host?.removeFromParent()

        addChild(hosting)
        hosting.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hosting.view)
        NSLayoutConstraint.activate([
            hosting.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hosting.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hosting.view.topAnchor.constraint(equalTo: view.topAnchor),
            hosting.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        hosting.didMove(toParent: self)
        host = hosting

        preferredContentSize = CGSize(width: view.bounds.width, height: panelHeight(fieldCount: fields.count))
    }

    private func panelHeight(fieldCount: Int) -> CGFloat {
        72 + CGFloat(max(fieldCount, 1)) * 52
    }
}
