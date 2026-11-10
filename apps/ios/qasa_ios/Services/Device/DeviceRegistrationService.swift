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
    /// Replacing the device a restored backup came from (`replace_device: true`).
    private(set) var isReplacing = false
    /// The confirmation shown right after a setup restore hit the device limit and the
    /// backup's restore key may replace the device that made it. Cleared by "Not now".
    var replacePrompt: RestoreKeyDevice?

    /// A backup was just adopted: the next limit error offers the replacement at once.
    @ObservationIgnored private var promptAfterRestore = false
    @ObservationIgnored private let restoreKeys: RestoreKeySecrets = KeychainRestoreKeySecrets()

    private init() {}

    /// Outcome of a replacement the user confirmed.
    enum ReplaceResult: Equatable {
        /// Registered in the old device's place; the name of the device that was unlinked.
        case replaced(oldDeviceName: String?)
        case failed
    }

    private enum RegisterResult {
        case registered(RegisterDeviceResponse)
        case limit
        case failed
        case skipped
    }

    func registerIfNeeded() async {
        _ = await register(replace: false)
    }

    /// Called after `BackupService.restore` adopted a backup, before the unlock that
    /// registers this device.
    func noteRestoredBackup() {
        promptAfterRestore = true
    }

    /// The restored backup's key may take the place of the device that made it.
    var canReplaceOldDevice: Bool {
        lastLimitError?.restoreKeyStatus == .replaceable
            && lastLimitError?.replaceable_device != nil
            && restoreKeys.pendingRestoreKey != nil
    }

    /// "Unlink and continue": registers again with the restore key and
    /// `replace_device: true`. On success the caller continues as after any successful
    /// registration (`AppBootstrap.onDeviceReplaced`).
    func replaceOldDevice() async -> ReplaceResult {
        guard restoreKeys.pendingRestoreKey != nil else { return .failed }
        replacePrompt = nil
        isReplacing = true
        defer { isReplacing = false }
        guard case .registered(let response) = await register(replace: true) else { return .failed }
        return .replaced(oldDeviceName: response.replaced_device?.device_name)
    }

    /// The register body; `restore_key` and `replace_device` only when there is a key.
    static func registerRequest(
        accountId: String,
        fingerprint: String,
        deviceName: String,
        location: String?,
        restoreKey: Data?,
        replace: Bool
    ) -> RegisterDeviceRequest {
        RegisterDeviceRequest(
            account_id: accountId,
            device_fingerprint: fingerprint,
            device_name: deviceName,
            platform: "ios",
            location: location,
            restore_key: restoreKey?.base64EncodedString(),
            replace_device: restoreKey != nil && replace ? true : nil
        )
    }

    private func register(replace: Bool) async -> RegisterResult {
        guard VaultSession.shared.isUnlocked,
              let accountId = VaultKeychainStore.accountId,
              let fingerprint = VaultKeychainStore.deviceFingerprint else {
            return .skipped
        }

        isRegistering = true
        defer { isRegistering = false }

        let pendingKey = restoreKeys.pendingRestoreKey
        let body = Self.registerRequest(
            accountId: accountId,
            fingerprint: fingerprint,
            deviceName: UIDevice.current.name,
            location: Self.approximateLocation(),
            restoreKey: pendingKey,
            replace: replace
        )

        do {
            let response: RegisterDeviceResponse = try await QasaAPIClient.shared.request(
                QasaConfig.registerURL,
                method: "POST",
                body: body,
                authorized: false
            )
            let previousDeviceId = LicenseManager.shared.parsed?.deviceId
            LicenseManager.shared.applyToken(response.license_token)
            if previousDeviceId != response.device_id {
                // A new device record has no restore-key hash on the server: drop the old
                // key so the next backup rotates instead of carrying an unknown key.
                restoreKeys.restoreKey = nil
                restoreKeys.restoreKeyWritten = false
            }
            devicesUsed = response.devices_used
            deviceLimit = response.device_limit
            currentDeviceId = response.device_id
            lastLimitError = nil
            if pendingKey != nil { restoreKeys.pendingRestoreKey = nil }
            promptAfterRestore = false
            replacePrompt = nil
            return .registered(response)
        } catch let QasaAPIError.deviceLimitExceeded(body) {
            lastLimitError = body
            devicesUsed = body.devices_used ?? body.device_limit ?? 1
            deviceLimit = body.device_limit ?? 1
            if promptAfterRestore, !replace, body.restoreKeyStatus == .replaceable {
                replacePrompt = body.replaceable_device
            }
            promptAfterRestore = false
            return .limit
        } catch {
            // Offline unlock remains valid for local vault use.
            return .failed
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
