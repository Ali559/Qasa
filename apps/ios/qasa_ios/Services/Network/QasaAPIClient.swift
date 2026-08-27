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
        switch self {
        case .invalidURL: return "Invalid API URL."
        case .httpStatus(let code, _): return "Server error (\(code))."
        case .decoding: return "Unexpected server response."
        case .unauthorized: return "Session expired. Unlock your vault again."
        case .deviceLimitExceeded(let body): return body.message
        case .forbidden: return "This feature requires a Premium subscription."
        case .network(let error): return error.localizedDescription
        }
    }
}

nonisolated struct DeviceLimitErrorBody: Decodable, Sendable {
    let error: String
    let message: String
    let devices_used: Int?
    let device_limit: Int?
    let devices: [RemoteDevice]?
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
        authorized: Bool = true
    ) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = method
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
