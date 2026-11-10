import Foundation
import Testing
@testable import qasa

/// The restore-key secrets in memory.
final class MemoryRestoreKeySecrets: RestoreKeySecrets {
    var restoreKey: Data?
    var restoreKeyWritten = false
    var pendingRestoreKey: Data?
    var hasLicense: Bool

    init(hasLicense: Bool = true, restoreKey: Data? = nil, written: Bool = false) {
        self.hasLicense = hasLicense
        self.restoreKey = restoreKey
        self.restoreKeyWritten = written
    }
}

/// Its own stub (not `StubURLProtocol`), so these tests never share canned responses
/// with another suite running in parallel.
nonisolated final class RestoreKeyStubURLProtocol: URLProtocol, @unchecked Sendable {
    struct Captured: Sendable {
        let method: String?
        let url: URL?
        let body: Data?
        let timeout: TimeInterval
    }

    nonisolated(unsafe) static var status = 200
    nonisolated(unsafe) static var responseBody = Data()
    nonisolated(unsafe) static var captured: [Captured] = []

    static func reset(status: Int, body: String = "") {
        self.status = status
        responseBody = Data(body.utf8)
        captured = []
    }

    static func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [RestoreKeyStubURLProtocol.self]
        return URLSession(configuration: config)
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        var body = request.httpBody
        if body == nil, let stream = request.httpBodyStream {
            stream.open()
            var data = Data()
            var buffer = [UInt8](repeating: 0, count: 4096)
            while stream.hasBytesAvailable {
                let read = stream.read(&buffer, maxLength: buffer.count)
                guard read > 0 else { break }
                data.append(buffer, count: read)
            }
            stream.close()
            body = data
        }
        Self.captured.append(Captured(
            method: request.httpMethod,
            url: request.url,
            body: body,
            timeout: request.timeoutInterval
        ))
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: Self.status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Self.responseBody)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

// MARK: - Rotation state machine

@MainActor
struct RestoreKeyRotationTests {
    private let old = Data(repeating: 1, count: 32)
    private let fresh = Data(repeating: 2, count: 32)

    @Test func noKeyRotatesAndUploadsTheNewHash() async {
        let secrets = MemoryRestoreKeySecrets()
        var uploaded: [Data] = []
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) {
            uploaded.append($0)
            return true
        }
        #expect(key == fresh)
        #expect(uploaded == [backupRestoreKeyHash(key: fresh)])
        #expect(uploaded.first?.count == 32)
        #expect(secrets.restoreKey == fresh)
        #expect(!secrets.restoreKeyWritten)
    }

    @Test func aWrittenKeyRotates() async {
        let secrets = MemoryRestoreKeySecrets(restoreKey: old, written: true)
        var uploads = 0
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) { _ in
            uploads += 1
            return true
        }
        #expect(uploads == 1)
        #expect(key == fresh)
        #expect(secrets.restoreKey == fresh)
        #expect(!secrets.restoreKeyWritten)
    }

    @Test func aKeyNotYetWrittenIsReused() async {
        let secrets = MemoryRestoreKeySecrets(restoreKey: old, written: false)
        var uploads = 0
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) { _ in
            uploads += 1
            return true
        }
        #expect(uploads == 0)
        #expect(key == old)
        #expect(secrets.restoreKey == old)
    }

    @Test func aFailedUploadKeepsTheOldKey() async {
        let secrets = MemoryRestoreKeySecrets(restoreKey: old, written: true)
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) { _ in false }
        #expect(key == old)
        #expect(secrets.restoreKey == old)
        #expect(secrets.restoreKeyWritten)
    }

    @Test func aFailedFirstUploadLeavesNoKey() async {
        let secrets = MemoryRestoreKeySecrets()
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) { _ in false }
        #expect(key == nil)
        #expect(secrets.restoreKey == nil)
    }

    @Test func noLicenseNeverUploads() async {
        let secrets = MemoryRestoreKeySecrets(hasLicense: false, restoreKey: old, written: true)
        var uploads = 0
        let key = await RestoreKeyRotation.keyForBackup(secrets: secrets, newKey: { fresh }) { _ in
            uploads += 1
            return true
        }
        #expect(uploads == 0)
        #expect(key == old)
    }

    @Test func writtenFlagFollowsTheKeyInTheFile() {
        let secrets = MemoryRestoreKeySecrets(restoreKey: old)
        RestoreKeyRotation.backupWritten(secrets: secrets, key: nil)
        #expect(!secrets.restoreKeyWritten)
        RestoreKeyRotation.backupWritten(secrets: secrets, key: fresh)
        #expect(!secrets.restoreKeyWritten)
        RestoreKeyRotation.backupWritten(secrets: secrets, key: old)
        #expect(secrets.restoreKeyWritten)
    }

    @Test func hashIsSha256() {
        let key = backupNewRestoreKey()
        #expect(key.count == 32)
        #expect(backupNewRestoreKey() != key)
        #expect(backupRestoreKeyHash(key: key).count == 32)
        #expect(backupRestoreKeyHash(key: key) == backupRestoreKeyHash(key: key))
    }

    @Test func resetClearsTheRestoreKeySecrets() {
        // `resetVaultSecrets` deletes every `Key` case (and its legacy copy).
        let keys = Set(VaultKeychainStore.Key.allCases.map(\.rawValue))
        #expect(keys.isSuperset(of: ["restoreKey", "restoreKeyWritten", "pendingRestoreKey", "licenseToken"]))
    }
}

// MARK: - Network

@Suite(.serialized)
@MainActor
struct RestoreKeyNetworkTests {
    private let url = URL(string: "https://api.test.qasa/v1/devices/me/restore-key")!
    private let registerURL = URL(string: "https://api.test.qasa/v1/devices/register")!

    @Test func uploadPutsTheBase64Hash() async throws {
        RestoreKeyStubURLProtocol.reset(status: 204)
        let api = QasaAPIClient(session: RestoreKeyStubURLProtocol.session())
        let hash = Data(repeating: 7, count: 32)

        let ok = await RestoreKeyRotation.uploadHash(hash, api: api, url: url)

        #expect(ok)
        let sent = try #require(RestoreKeyStubURLProtocol.captured.first)
        #expect(sent.method == "PUT")
        #expect(sent.url?.path == "/v1/devices/me/restore-key")
        #expect(sent.timeout == RestoreKeyRotation.uploadTimeout)
        let sentBody = try #require(sent.body)
        let json = try #require(try JSONSerialization.jsonObject(with: sentBody) as? [String: String])
        #expect(json == ["key_hash": hash.base64EncodedString()])
    }

    @Test(arguments: [401, 403, 500])
    func uploadFailureIsFalse(status: Int) async {
        RestoreKeyStubURLProtocol.reset(status: status, body: #"{"error":"nope","message":"no"}"#)
        let api = QasaAPIClient(session: RestoreKeyStubURLProtocol.session())
        #expect(await RestoreKeyRotation.uploadHash(Data(count: 32), api: api, url: url) == false)
    }

    @Test func limitErrorCarriesTheRestoreKeyStatus() async throws {
        RestoreKeyStubURLProtocol.reset(status: 403, body: """
        {"error":"device_limit_exceeded","message":"Limit reached","devices_used":1,"device_limit":1,
         "devices":[{"id":"d1","device_name":"Old iPhone","platform":"ios"}],
         "restore_key":"replaceable",
         "replaceable_device":{"id":"d1","device_name":"Old iPhone","platform":"ios","created_at":"2026-01-01T00:00:00Z"}}
        """)
        let api = QasaAPIClient(session: RestoreKeyStubURLProtocol.session())
        do {
            _ = try await api.rawRequest(registerURL, method: "POST", authorized: false)
            Issue.record("expected a limit error")
        } catch QasaAPIError.deviceLimitExceeded(let body) {
            #expect(body.restoreKeyStatus == .replaceable)
            #expect(body.replaceable_device == RestoreKeyDevice(id: "d1", device_name: "Old iPhone", platform: "ios"))
            #expect(body.devices?.count == 1)
        }
    }

    @Test(arguments: [
        (#""restore_key":"unknown","#, RestoreKeyStatus?.some(.unknown)),
        (#""restore_key":"not_enough","#, RestoreKeyStatus?.some(.notEnough)),
        (#""restore_key":null,"#, RestoreKeyStatus?.none),
        ("", RestoreKeyStatus?.none),
    ])
    func limitErrorStatuses(field: String, expected: RestoreKeyStatus?) throws {
        let json = #"{"error":"device_limit_exceeded","message":"m","#
            + field + #""device_limit":1}"#
        let body = try QasaCodec.decode(DeviceLimitErrorBody.self, from: Data(json.utf8))
        #expect(body.restoreKeyStatus == expected)
        #expect(body.replaceable_device == nil)
    }

    @Test func registeredResponseNamesTheReplacedDevice() throws {
        let json = """
        {"device_id":"d2","license_token":"t","devices_used":1,"device_limit":1,"tier":"free",
         "replaced_device":{"id":"d1","device_name":"Old iPhone"}}
        """
        let response = try QasaCodec.decode(RegisterDeviceResponse.self, from: Data(json.utf8))
        #expect(response.replaced_device?.device_name == "Old iPhone")
        #expect(response.replaced_device?.id == "d1")

        let plain = try QasaCodec.decode(RegisterDeviceResponse.self, from: Data("""
        {"device_id":"d2","license_token":"t","devices_used":1,"device_limit":1,"tier":"free"}
        """.utf8))
        #expect(plain.replaced_device == nil)
    }
}

// MARK: - Register body

@MainActor
struct RegisterRequestTests {
    private func json(_ request: RegisterDeviceRequest) throws -> [String: Any] {
        try #require(try JSONSerialization.jsonObject(with: QasaCodec.encodeBody(request)) as? [String: Any])
    }

    private func request(key: Data?, replace: Bool) -> RegisterDeviceRequest {
        DeviceRegistrationService.registerRequest(
            accountId: "acct",
            fingerprint: "fp",
            deviceName: "New iPhone",
            location: "Iraq",
            restoreKey: key,
            replace: replace
        )
    }

    @Test func withoutAKeyTheBodyIsUnchanged() throws {
        let body = try json(request(key: nil, replace: true))
        #expect(Set(body.keys) == ["account_id", "device_fingerprint", "device_name", "platform", "location"])
        #expect(body["platform"] as? String == "ios")
    }

    @Test func aPendingKeyIsSentAsBase64() throws {
        let key = Data(repeating: 9, count: 32)
        let body = try json(request(key: key, replace: false))
        #expect(body["restore_key"] as? String == key.base64EncodedString())
        #expect(body["replace_device"] == nil)
    }

    @Test func replacingSendsTheFlag() throws {
        let key = Data(repeating: 9, count: 32)
        let body = try json(request(key: key, replace: true))
        #expect(body["restore_key"] as? String == key.base64EncodedString())
        #expect(body["replace_device"] as? Bool == true)
    }
}
