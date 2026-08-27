import FirebaseCore
import FirebaseMessaging
import Foundation
import UIKit
import UserNotifications

extension Notification.Name {
    /// Posted when the user taps a remote/local push — open Notifications in the shell.
    nonisolated static let qasaOpenNotifications = Notification.Name("qasa.openNotifications")
}

/// Requests permission, registers with APNs → FCM, uploads the **FCM** token via device prefs.
/// Android will upload its FCM token the same way (`PATCH /v1/devices/prefs`).
@MainActor
final class PushNotificationService: NSObject, UNUserNotificationCenterDelegate, MessagingDelegate {
    static let shared = PushNotificationService()

    /// FCM registration token (not raw APNs hex). Same field Android will use later.
    private(set) var fcmToken: String?
    private let tokenKey = "qasa.fcm_token"

    private override init() {
        super.init()
        fcmToken = UserDefaults.standard.string(forKey: tokenKey)
    }

    func configure() {
        UNUserNotificationCenter.current().delegate = self
        guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
            // Drop GoogleService-Info.plist from Firebase Console into qasa_ios/ to enable.
            return
        }
        if FirebaseApp.app() == nil {
            FirebaseApp.configure()
        }
        Messaging.messaging().delegate = self
    }

    /// Call after vault unlock when a license token exists.
    func requestAuthorizationAndRegister() async {
        configure()
        do {
            let granted = try await UNUserNotificationCenter.current().requestAuthorization(
                options: [.alert, .badge, .sound]
            )
            guard granted else {
                await uploadPrefs(token: fcmToken, enabled: false)
                return
            }
            UIApplication.shared.registerForRemoteNotifications()
            if let token = fcmToken {
                await uploadPrefs(token: token, enabled: true)
            } else {
                await refreshAndUploadFcmToken(enabled: true)
            }
        } catch {
            // Permission prompt failed; keep going without push.
        }
    }

    /// APNs device token — hand to Firebase so it can mint an FCM registration token.
    func didRegister(deviceToken: Data) {
        guard FirebaseApp.app() != nil else { return }
        Messaging.messaging().apnsToken = deviceToken
        Task { await refreshAndUploadFcmToken(enabled: true) }
    }

    func didFailToRegister(error: Error) {
        Task { await uploadPrefs(token: fcmToken, enabled: true) }
        _ = error
    }

    private func refreshAndUploadFcmToken(enabled: Bool) async {
        guard FirebaseApp.app() != nil else { return }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            Messaging.messaging().register { _ in
                continuation.resume()
            }
        }
        // Token arrives via messaging(_:didReceiveRegistrationToken:); upload cached copy if present.
        if let token = fcmToken {
            await uploadPrefs(token: token, enabled: enabled)
        }
    }

    private func storeAndUpload(token: String, enabled: Bool) {
        fcmToken = token
        UserDefaults.standard.set(token, forKey: tokenKey)
        Task { await uploadPrefs(token: token, enabled: enabled) }
    }

    private func uploadPrefs(token: String?, enabled: Bool) async {
        guard VaultKeychainStore.licenseToken != nil else { return }
        struct Ok: Decodable { let ok: Bool? }
        let body = DevicePrefsRequest(
            locale: AppPreferences.shared.language.rawValue,
            push_token: token,
            notifications_enabled: enabled
        )
        _ = try? await QasaAPIClient.shared.request(
            QasaConfig.devicePrefsURL,
            method: "PATCH",
            body: body
        ) as Ok
    }

    // MARK: - MessagingDelegate

    nonisolated func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken, !fcmToken.isEmpty else { return }
        Task { @MainActor in
            storeAndUpload(token: fcmToken, enabled: true)
        }
    }

    // Foreground presentation
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    // Tap → Notifications screen
    nonisolated func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        NotificationCenter.default.post(
            name: .qasaOpenNotifications,
            object: nil,
            userInfo: userInfo
        )
        completionHandler()
    }
}

final class QasaAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Task { @MainActor in
            PushNotificationService.shared.configure()
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task { @MainActor in
            PushNotificationService.shared.didRegister(deviceToken: deviceToken)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        Task { @MainActor in
            PushNotificationService.shared.didFailToRegister(error: error)
        }
    }
}
