import Foundation

enum QasaAPIError: LocalizedError, Sendable {
    case invalidURL
    case httpStatus(Int, Data?)
    case decoding(Error)
    case unauthorized
    case deviceLimitExceeded(DeviceLimitErrorBody)
    case forbidden
    case network(Error)

    var errorDescription: String? {
        let locale = L10n.preferredLocale
        switch self {
        case .invalidURL:
            return L10n.tr("error.invalidURL", locale: locale)
        case .httpStatus(let code, _):
            return String(format: L10n.tr("error.server", locale: locale), code)
        case .decoding:
            return L10n.tr("error.decoding", locale: locale)
        case .unauthorized:
            return L10n.tr("error.unauthorized", locale: locale)
        case .deviceLimitExceeded(let body):
            return body.message
        case .forbidden:
            return L10n.tr("error.forbidden", locale: locale)
        case .network:
            return L10n.tr("error.network", locale: locale)
        }
    }
}

nonisolated struct DeviceLimitErrorBody: Decodable, Sendable {
    let error: String
    let message: String
    let devices_used: Int?
    let device_limit: Int?
    let devices: [RemoteDevice]?
    /// Only with a full account: `nil` when register sent no restore key, else
    /// `unknown`, `not_enough` or `replaceable`.
    var restore_key: String? = nil
    /// The device the sent restore key may replace (`restore_key == "replaceable"`).
    var replaceable_device: RestoreKeyDevice? = nil

    var restoreKeyStatus: RestoreKeyStatus? {
        restore_key.flatMap(RestoreKeyStatus.init(rawValue:))
    }
}

/// What the server says about the restore key a limit-blocked registration sent.
nonisolated enum RestoreKeyStatus: String, Sendable {
    case unknown
    case notEnough = "not_enough"
    case replaceable
}

/// `replaceable_device` on a 403, `replaced_device` on a 201.
nonisolated struct RestoreKeyDevice: Decodable, Hashable, Sendable {
    let id: String
    let device_name: String
    let platform: String?
}

actor QasaAPIClient {
    static let shared = QasaAPIClient()

    private let session: URLSession

    init(session: URLSession = .shared) {
        self.session = session
    }

    func request<T: Decodable>(
        _ url: URL,
        method: String = "GET",
        body: (any Encodable)? = nil,
        authorized: Bool = true
    ) async throws -> T {
        let data = try await rawRequest(url, method: method, body: body, authorized: authorized)
        do {
            return try QasaCodec.decode(T.self, from: data)
        } catch {
            throw QasaAPIError.decoding(error)
        }
    }

    func rawRequest(
        _ url: URL,
        method: String = "GET",
        body: (any Encodable)? = nil,
        authorized: Bool = true,
        timeout: TimeInterval? = nil
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
        if let timeout { request.timeoutInterval = timeout }
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try QasaCodec.encodeBody(body)
        }
        if authorized, let token = VaultKeychainStore.licenseToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw QasaAPIError.network(error)
        }

        guard let http = response as? HTTPURLResponse else {
            throw QasaAPIError.httpStatus(-1, data)
        }

        switch http.statusCode {
        case 200...299:
            return data
        case 401:
            throw QasaAPIError.unauthorized
        case 403:
            if let limit = try? QasaCodec.decode(DeviceLimitErrorBody.self, from: data),
               limit.error == "device_limit_exceeded" {
                throw QasaAPIError.deviceLimitExceeded(limit)
            }
            throw QasaAPIError.forbidden
        default:
            throw QasaAPIError.httpStatus(http.statusCode, data)
        }
    }

    func requestEmpty(
        _ url: URL,
        method: String = "DELETE",
        authorized: Bool = true
    ) async throws {
        _ = try await rawRequest(url, method: method, body: Optional<String>.none, authorized: authorized)
    }
}

nonisolated enum QasaCodec {
    private static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .useDefaultKeys
        return encoder
    }()

    private static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .useDefaultKeys
        return decoder
    }()

    static func encodeBody(_ body: any Encodable) throws -> Data {
        try encoder.encode(AnyEncodable(body))
    }

    static func decode<T: Decodable>(_ type: T.Type, from data: Data) throws -> T {
        try decoder.decode(type, from: data)
    }
}

nonisolated private struct AnyEncodable: Encodable {
    let value: any Encodable

    init(_ value: any Encodable) { self.value = value }

    func encode(to encoder: Encoder) throws {
        try value.encode(to: encoder)
    }
}
