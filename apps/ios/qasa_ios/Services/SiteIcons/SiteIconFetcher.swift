import Foundation

/// What asking a site for its icon gave (`docs/site-icons.md`, The request).
nonisolated enum SiteIconFetchResult: Equatable, Sendable {
    /// The normalised 64 × 64 PNG.
    case icon(Data)
    /// The site answered but offered no usable icon (retried after 7 days).
    case none
    /// Offline, a timeout, a TLS error… (retried after 1 day).
    case failure
}

/// Asks one site for its own icon, over HTTPS only, from an ephemeral session: no cookies,
/// credentials or shared cache, `User-Agent: Mozilla/5.0 (compatible)`, at most 3 redirects
/// (each re-checked against the skip rules), 8 s per request, at most 512 KB of HTML and
/// 256 KB per image. Never talks to anyone but the site (and the hosts its page points to).
nonisolated final class SiteIconFetcher: NSObject, URLSessionTaskDelegate, @unchecked Sendable {
    static let shared = SiteIconFetcher()

    static let userAgent = "Mozilla/5.0 (compatible)"
    static let timeout: TimeInterval = 8
    static let maxRedirects = 3
    static let maxHTMLBytes = 512 * 1024
    static let maxImageBytes = 256 * 1024

    private let lock = NSLock()
    private var redirectCounts: [Int: Int] = [:]
    private var session: URLSession!

    override init() {
        super.init()
        let config = URLSessionConfiguration.ephemeral
        config.httpCookieAcceptPolicy = .never
        config.httpShouldSetCookies = false
        config.httpCookieStorage = nil
        config.urlCredentialStorage = nil
        config.urlCache = nil
        config.requestCachePolicy = .reloadIgnoringLocalAndRemoteCacheData
        config.timeoutIntervalForRequest = Self.timeout
        config.timeoutIntervalForResource = Self.timeout
        config.waitsForConnectivity = false
        config.httpMaximumConnectionsPerHost = 2
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }

    private struct TooLarge: Error {}

    /// Asks `host` (already normalised and askable) for its icon.
    func fetch(host: String) async -> SiteIconFetchResult {
        guard let page = URL(string: "https://\(host)/"), SiteIconRules.isAskable(page) else { return .none }
        var candidates: [URL] = []
        do {
            let (data, response) = try await download(page, accept: "text/html", limit: Self.maxHTMLBytes, truncate: true)
            if (200..<300).contains(response.statusCode),
               let html = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) {
                candidates = SiteIconRules.pickIcons(html: html, pageURL: response.url ?? page)
            }
        } catch {
            return .failure
        }
        if Task.isCancelled { return .failure }
        if let favicon = URL(string: "https://\(host)/favicon.ico"), !candidates.contains(favicon) {
            candidates.append(favicon)
        }
        for url in candidates {
            if Task.isCancelled { return .failure }
            if let png = await icon(at: url) { return .icon(png) }
        }
        return .none
    }

    private func icon(at url: URL) async -> Data? {
        if url.scheme?.lowercased() == "data" {
            guard let bytes = SiteIconRules.dataURLBytes(url), bytes.count <= Self.maxImageBytes else { return nil }
            return SiteIconImage.normalizedPNG(from: bytes)
        }
        guard let (data, response) = try? await download(url, accept: "image/*", limit: Self.maxImageBytes, truncate: false),
              (200..<300).contains(response.statusCode) else { return nil }
        return SiteIconImage.normalizedPNG(from: data)
    }

    /// GET `url`, reading at most `limit` bytes: an HTML page is cut there (`truncate`), an
    /// image over the limit is refused.
    private func download(_ url: URL, accept: String, limit: Int, truncate: Bool) async throws -> (Data, HTTPURLResponse) {
        guard SiteIconRules.isAskable(url) else { throw URLError(.unsupportedURL) }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalAndRemoteCacheData, timeoutInterval: Self.timeout)
        request.setValue(accept, forHTTPHeaderField: "Accept")
        Self.applyAnonymousHeaders(&request)
        let (bytes, response) = try await session.bytes(for: request, delegate: self)
        let taskId = bytes.task.taskIdentifier
        defer {
            bytes.task.cancel()
            lock.withLock { _ = redirectCounts.removeValue(forKey: taskId) }
        }
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        if !truncate, http.expectedContentLength > Int64(limit) { throw TooLarge() }
        var data = Data()
        data.reserveCapacity(min(limit, max(0, Int(http.expectedContentLength))))
        for try await byte in bytes {
            if data.count >= limit {
                if truncate { break }
                throw TooLarge()
            }
            data.append(byte)
        }
        return (data, http)
    }

    /// Nothing that tells the site about the user or the app. CFNetwork always sends an
    /// `Accept-Language` header, so it goes out empty rather than with the device languages.
    private static func applyAnonymousHeaders(_ request: inout URLRequest) {
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("", forHTTPHeaderField: "Accept-Language")
        request.httpShouldHandleCookies = false
    }

    // MARK: - URLSessionTaskDelegate

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping @Sendable (URLRequest?) -> Void
    ) {
        let count = lock.withLock { () -> Int in
            let next = (redirectCounts[task.taskIdentifier] ?? 0) + 1
            redirectCounts[task.taskIdentifier] = next
            return next
        }
        // Refusing hands back the 3xx itself, which counts as "no icon here".
        guard count <= Self.maxRedirects, let url = request.url, SiteIconRules.isAskable(url) else {
            completionHandler(nil)
            return
        }
        var next = request
        Self.applyAnonymousHeaders(&next)
        completionHandler(next)
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        // Server trust is checked as usual; a site asking for a login or a client
        // certificate gets nothing.
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust {
            completionHandler(.performDefaultHandling, nil)
        } else {
            completionHandler(.cancelAuthenticationChallenge, nil)
        }
    }
}
