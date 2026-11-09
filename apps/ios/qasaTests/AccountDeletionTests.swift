import Foundation
import SwiftData
import Testing
@testable import qasa

/// Answers every request with a canned response and records what was sent.
nonisolated final class StubURLProtocol: URLProtocol, @unchecked Sendable {
    struct Captured: Sendable {
        let method: String?
        let url: URL?
        let body: Data?
        let contentType: String?
    }

    nonisolated(unsafe) static var status = 200
    nonisolated(unsafe) static var responseBody = Data("{}".utf8)
    nonisolated(unsafe) static var captured: [Captured] = []

    static func reset(status: Int, body: String) {
        self.status = status
        responseBody = Data(body.utf8)
        captured = []
    }

    static func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [StubURLProtocol.self]
        return URLSession(configuration: config)
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.captured.append(Captured(
            method: request.httpMethod,
            url: request.url,
            body: request.httpBody ?? Self.readStream(request.httpBodyStream),
            contentType: request.value(forHTTPHeaderField: "Content-Type")
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

    /// URLSession hands a protocol the body as a stream, not `httpBody`.
    private static func readStream(_ stream: InputStream?) -> Data? {
        guard let stream else { return nil }
        stream.open()
        defer { stream.close() }
        var data = Data()
        var buffer = [UInt8](repeating: 0, count: 4096)
        while stream.hasBytesAvailable {
            let read = stream.read(&buffer, maxLength: buffer.count)
            guard read > 0 else { break }
            data.append(buffer, count: read)
        }
        return data
    }
}

@Suite(.serialized)
@MainActor
struct AccountDeletionTests {
    private let url = URL(string: "https://api.test.qasa/v1/account")!

    private func vaultWithRows() throws -> ModelContainer {
        let container = try ModelContainer(
            for: Schema(VaultSchema.models),
            configurations: [ModelConfiguration(isStoredInMemoryOnly: true)]
        )
        let context = container.mainContext
        let item = VaultItemRecord(kind: .login)
        context.insert(item)
        context.insert(VaultSearchToken(itemId: item.id, tokenHash: "h"))
        context.insert(VaultCollection(nameEncrypted: Data([1, 2, 3])))
        context.insert(AppNotification(title: "t", body: "b"))
        try context.save()
        return container
    }

    private func rowCount(_ context: ModelContext) throws -> Int {
        try context.fetchCount(FetchDescriptor<VaultItemRecord>())
            + context.fetchCount(FetchDescriptor<VaultSearchToken>())
            + context.fetchCount(FetchDescriptor<VaultCollection>())
            + context.fetchCount(FetchDescriptor<AppNotification>())
    }

    @Test func sendsDeleteWithConfirmAccountId() async throws {
        StubURLProtocol.reset(status: 200, body: #"{"deleted":true,"removed":{}}"#)
        let api = QasaAPIClient(session: StubURLProtocol.session())

        try await AccountDeletion.deleteAccount(accountId: "acct-42", api: api, url: url, eraseLocal: {})

        let sent = try #require(StubURLProtocol.captured.first)
        #expect(StubURLProtocol.captured.count == 1)
        #expect(sent.method == "DELETE")
        #expect(sent.url?.path == "/v1/account")
        #expect(sent.contentType == "application/json")
        let body = try #require(sent.body)
        let json = try #require(try JSONSerialization.jsonObject(with: body) as? [String: String])
        #expect(json == ["confirm_account_id": "acct-42"])
    }

    @Test func successForgetsIdentityAndErasesVault() async throws {
        StubURLProtocol.reset(status: 200, body: #"{"deleted":true}"#)
        let api = QasaAPIClient(session: StubURLProtocol.session())
        let meta = MemoryVaultMeta.withVault(account: "acct-42")
        let container = try vaultWithRows()
        let context = container.mainContext

        try await AccountDeletion.deleteAccount(accountId: "acct-42", api: api, url: url) {
            AccountDeletion.forgetIdentity(meta)
            try? AccountDeletion.eraseVaultRecords(context: context)
        }

        #expect(meta.accountId == nil)
        #expect(meta.deviceFingerprint == nil)
        #expect(meta.vaultExists == false)
        #expect(meta.kdfSalt == nil)
        #expect(meta.passwordVerifier == nil)
        #expect(try rowCount(context) == 0)
    }

    @Test(arguments: [500, 404, 400])
    func failureKeepsEverythingLocal(status: Int) async throws {
        StubURLProtocol.reset(status: status, body: #"{"error":"nope"}"#)
        let api = QasaAPIClient(session: StubURLProtocol.session())
        let meta = MemoryVaultMeta.withVault(account: "acct-42")
        let container = try vaultWithRows()
        let context = container.mainContext
        let before = try rowCount(context)
        var erased = false

        await #expect(throws: QasaAPIError.self) {
            try await AccountDeletion.deleteAccount(accountId: "acct-42", api: api, url: url) {
                erased = true
                AccountDeletion.forgetIdentity(meta)
                try? AccountDeletion.eraseVaultRecords(context: context)
            }
        }

        #expect(!erased)
        #expect(meta.accountId == "acct-42")
        #expect(meta.deviceFingerprint == "fp-host")
        #expect(meta.vaultExists)
        #expect(meta.kdfSalt != nil)
        #expect(try rowCount(context) == before)
        #expect(before == 4)
    }

    @Test func unauthorizedReauthorizesAndRetriesOnce() async throws {
        StubURLProtocol.reset(status: 401, body: "{}")
        let api = QasaAPIClient(session: StubURLProtocol.session())
        var reauthorized = 0
        var erased = false

        await #expect(throws: QasaAPIError.self) {
            try await AccountDeletion.deleteAccount(
                accountId: "acct-42",
                api: api,
                url: url,
                reauthorize: { reauthorized += 1 },
                eraseLocal: { erased = true }
            )
        }

        #expect(reauthorized == 1)
        #expect(StubURLProtocol.captured.count == 2)
        #expect(!erased)
    }
}
