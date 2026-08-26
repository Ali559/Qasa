import Foundation
import UIKit

@Observable
final class DeviceRegistrationService {
    static let shared = DeviceRegistrationService()

    private(set) var devicesUsed: Int = 0
    private(set) var deviceLimit: Int = 1
    private(set) var currentDeviceId: String?
    private(set) var lastLimitError: DeviceLimitErrorBody?
    private(set) var isRegistering = false

    private init() {}

    func registerIfNeeded() async {
        guard VaultSession.shared.isUnlocked,
              let accountId = VaultKeychainStore.accountId,
              let fingerprint = VaultKeychainStore.deviceFingerprint else {
            return
        }

        isRegistering = true
        defer { isRegistering = false }

        let body = RegisterDeviceRequest(
            account_id: accountId,
            device_fingerprint: fingerprint,
            device_name: UIDevice.current.name,
            platform: "ios"
        )

        do {
            let response: RegisterDeviceResponse = try await QasaAPIClient.shared.request(
                QasaConfig.registerURL,
                method: "POST",
                body: body,
                authorized: false
            )
            LicenseManager.shared.applyToken(response.license_token)
            devicesUsed = response.devices_used
            deviceLimit = response.device_limit
            currentDeviceId = response.device_id
            lastLimitError = nil
        } catch let QasaAPIError.deviceLimitExceeded(body) {
            lastLimitError = body
            devicesUsed = body.devices_used ?? body.device_limit ?? 1
            deviceLimit = body.device_limit ?? 1
        } catch {
            // Offline unlock remains valid for local vault use.
        }
    }

    func fetchDevices() async throws -> [RemoteDevice] {
        let response: DeviceListResponse = try await QasaAPIClient.shared.request(
            QasaConfig.devicesURL,
            authorized: true
        )
        return response.devices
    }

    func unlinkDevice(id: String) async throws {
        let url = QasaConfig.devicesURL.appending(path: "/\(id)")
        try await QasaAPIClient.shared.requestEmpty(url, method: "DELETE")
    }
}
