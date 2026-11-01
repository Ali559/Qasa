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
            platform: "ios",
            location: Self.approximateLocation()
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

    /// Whether `accountId` can accept another linked device (live Premium + under limit).
    func checkLinkEligibility(accountId: UUID) async throws -> DeviceLinkCheckResponse {
        try await QasaAPIClient.shared.request(
            QasaConfig.devicesLinkCheckURL,
            method: "POST",
            body: DeviceLinkCheckRequest(account_id: accountId.uuidString.lowercased()),
            authorized: false
        )
    }

    /// Unlinks every device except the primary (first linked). Returns remaining devices.
    @discardableResult
    func unlinkOtherDevices() async throws -> [RemoteDevice] {
        struct Response: Decodable {
            let revoked: Int?
            let devices: [RemoteDevice]?
        }
        let response: Response = try await QasaAPIClient.shared.request(
            QasaConfig.devicesOthersURL,
            method: "DELETE"
        )
        return response.devices ?? []
    }

    /// Region-based hint for linked-device list (not precise GPS).
    static func approximateLocation() -> String {
        let locale = Locale.current
        if let region = locale.region?.identifier,
           let name = locale.localizedString(forRegionCode: region) {
            return name
        }
        if let region = locale.region?.identifier {
            return region
        }
        return TimeZone.current.identifier
    }
}
