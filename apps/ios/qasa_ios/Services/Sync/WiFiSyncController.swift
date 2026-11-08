import Foundation
import Network
import Observation
import SwiftData
import UIKit

/// Wi‑Fi vault sync (plan §6): Bonjour discovery here, the session itself in Rust
/// (`core/qasa-ffi`). Mirrors the desktop (`apps/desktop/src-tauri/src/sync.rs`).
///
/// - Host: Rust binds a TCP port and makes the 6-digit code; this advertises
///   `_qasa-sync._tcp` on that port under the device name (never the code). The code works
///   for one session, 3 attempts and 2 minutes, whichever ends first.
/// - Join: browses for hosts, resolves the one picked to an IPv4 address and port (without
///   connecting, so the host doesn't count a wasted attempt), then runs the session with
///   the typed code.
@Observable
final class WiFiSyncController {
    static let codeLifetime: TimeInterval = 120

    struct Peer: Identifiable, Hashable {
        let name: String
        let type: String
        let domain: String
        let platform: String
        var id: String { "\(name).\(type).\(domain)" }
    }

    struct Failure: Equatable {
        /// A `sync.*` string key.
        let key: String
        let attemptsLeft: Int?

        func message(locale: Locale) -> String {
            let text = L10n.tr(key, locale: locale)
            guard let attemptsLeft else { return text }
            return String(format: text, attemptsLeft)
        }
    }

    struct Result: Equatable {
        let peerName: String
        let adopted: Bool
        let received: Int
        let sent: Int
    }

    enum Stage: Equatable {
        case idle
        /// Hosting: showing `code` until `expiresAt`.
        case waiting(code: String, expiresAt: Date)
        /// A device connected and the code matched; the session is running.
        case connecting
        case done(Result)
        case failed(Failure)
    }

    private(set) var stage: Stage = .idle
    /// The code stays visible after a wrong attempt while hosting continues.
    private(set) var hostCode: String?
    private(set) var hostExpiresAt: Date?
    private(set) var peers: [Peer] = []
    private(set) var isBrowsing = false
    private(set) var isJoining = false

    private var host: SyncHost?
    private var advert: NetService?
    private var browser: NWBrowser?
    private var resolver: AddressResolver?
    private var expiryTimer: Timer?
    private var expired = false
    private var generation = 0

    // MARK: host

    func startHost(container: ModelContainer) {
        stop()
        generation += 1
        let gen = generation
        let host: SyncHost
        do {
            host = try SyncHost()
        } catch {
            stage = .failed(Self.failure(error))
            return
        }
        self.host = host
        expired = false
        let code = host.code()
        let expiresAt = Date().addingTimeInterval(Self.codeLifetime)
        hostCode = code
        hostExpiresAt = expiresAt
        stage = .waiting(code: code, expiresAt: expiresAt)

        let service = NetService(domain: "local.", type: "_qasa-sync._tcp.", name: Self.instanceName(), port: Int32(host.port()))
        service.setTXTRecord(NetService.data(fromTXTRecord: ["p": Data("ios".utf8), "v": Data("1".utf8)]))
        service.publish()
        advert = service

        expiryTimer = Timer.scheduledTimer(withTimeInterval: Self.codeLifetime, repeats: false) { [weak self] _ in
            MainActor.assumeIsolated {
                guard let self, self.generation == gen else { return }
                self.expired = true
                self.host?.stop()
            }
        }

        let store = makeStore(container: container, generation: gen)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            var final: Swift.Result<FfiOutcome, Error>
            var used = 0
            while true {
                do {
                    final = .success(try host.serve(store: store))
                    break
                } catch let error as FfiSyncError {
                    used += 1
                    // Keep waiting after a wrong code or a dropped connection while
                    // attempts are left; a refused vault decision won't change on retry.
                    let retry: Bool
                    switch error {
                    case .WrongCode(let left): retry = left > 0
                    case .Io: retry = used < 3
                    default: retry = false
                    }
                    guard retry else {
                        final = .failure(error)
                        break
                    }
                    let failure = Self.failure(error)
                    DispatchQueue.main.async {
                        guard let self, self.generation == gen else { return }
                        self.stage = .failed(failure)
                    }
                } catch {
                    final = .failure(error)
                    break
                }
            }
            let result = final
            DispatchQueue.main.async {
                guard let self, self.generation == gen else { return }
                self.finishHost(result)
            }
        }
    }

    private func finishHost(_ result: Swift.Result<FfiOutcome, Error>) {
        let wasExpired = expired
        teardownHost()
        switch result {
        case .success(let outcome):
            stage = .done(Self.result(outcome))
        case .failure(FfiSyncError.Stopped) where wasExpired:
            stage = .failed(Failure(key: "sync.expired", attemptsLeft: nil))
        case .failure(FfiSyncError.Stopped):
            stage = .idle
        case .failure(let error):
            stage = .failed(Self.failure(error))
        }
    }

    // MARK: join

    func startBrowsing() {
        guard browser == nil else { return }
        peers = []
        isBrowsing = true
        let browser = NWBrowser(for: .bonjourWithTXTRecord(type: "_qasa-sync._tcp", domain: nil), using: .tcp)
        browser.browseResultsChangedHandler = { [weak self] results, _ in
            let found: [Peer] = results.compactMap { result in
                guard case let .service(name, type, domain, _) = result.endpoint else { return nil }
                var platform = ""
                if case let .bonjour(txt) = result.metadata, let p = txt["p"] { platform = p }
                return Peer(name: name, type: type, domain: domain, platform: platform)
            }
            DispatchQueue.main.async {
                guard let self else { return }
                var seen = Set<String>()
                self.peers = found
                    .filter { seen.insert($0.id).inserted }
                    .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            }
        }
        browser.start(queue: .main)
        self.browser = browser
    }

    func stopBrowsing() {
        browser?.cancel()
        browser = nil
        isBrowsing = false
    }

    /// Resolves `peer` and runs the session. `stage` ends as `.done` or `.failed`.
    func join(peer: Peer, code: String, container: ModelContainer) {
        let digits = code.filter(\.isNumber)
        guard digits.count == 6, !isJoining else { return }
        generation += 1
        let gen = generation
        isJoining = true
        stage = .connecting
        let store = makeStore(container: container, generation: gen)
        let resolver = AddressResolver(name: peer.name, type: peer.type, domain: peer.domain)
        self.resolver = resolver
        resolver.resolve { [weak self] address in
            guard let self, self.generation == gen else { return }
            self.resolver = nil
            guard let address else {
                self.isJoining = false
                self.stage = .failed(Failure(key: "sync.io", attemptsLeft: nil))
                return
            }
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                let result: Swift.Result<FfiOutcome, Error>
                do {
                    result = .success(try syncJoin(host: address.host, port: address.port, code: digits, store: store))
                } catch {
                    result = .failure(error)
                }
                DispatchQueue.main.async {
                    guard let self, self.generation == gen else { return }
                    self.isJoining = false
                    switch result {
                    case .success(let outcome): self.stage = .done(Self.result(outcome))
                    case .failure(let error): self.stage = .failed(Self.failure(error))
                    }
                }
            }
        }
    }

    // MARK: lifecycle

    /// Stops hosting and browsing; a running join finishes in the background but is ignored.
    func stop() {
        generation += 1
        teardownHost()
        stopBrowsing()
        resolver = nil
        isJoining = false
        stage = .idle
    }

    func reset() {
        if case .waiting = stage { return }
        if host == nil, !isJoining { stage = .idle }
    }

    private func teardownHost() {
        host?.stop()
        host = nil
        advert?.stop()
        advert = nil
        expiryTimer?.invalidate()
        expiryTimer = nil
        hostCode = nil
        hostExpiresAt = nil
    }

    private func makeStore(container: ModelContainer, generation gen: Int) -> VaultSyncStore {
        VaultSyncStore(
            container: container,
            deviceName: UIDevice.current.name,
            onIntro: { [weak self] in
                DispatchQueue.main.async {
                    guard let self, self.generation == gen else { return }
                    self.stage = .connecting
                }
            }
        )
    }

    // MARK: mapping

    /// Same keys as the desktop's `failureKey` (`apps/desktop/src/sync/api.ts`).
    static func failure(_ error: Error) -> Failure {
        guard let error = error as? FfiSyncError else { return Failure(key: "sync.failed", attemptsLeft: nil) }
        switch error {
        case .WrongCode(let left):
            return left > 0
                ? Failure(key: "sync.wrongCode", attemptsLeft: Int(left))
                : Failure(key: "sync.wrongCodeFinal", attemptsLeft: nil)
        case .DifferentVault: return Failure(key: "sync.differentVault", attemptsLeft: nil)
        case .NoVault: return Failure(key: "sync.noVault", attemptsLeft: nil)
        case .Version: return Failure(key: "sync.version", attemptsLeft: nil)
        case .Expired: return Failure(key: "sync.expired", attemptsLeft: nil)
        case .Io: return Failure(key: "sync.io", attemptsLeft: nil)
        case .Stopped, .Store, .Unexpected: return Failure(key: "sync.failed", attemptsLeft: nil)
        }
    }

    private static func result(_ o: FfiOutcome) -> Result {
        Result(peerName: o.peerName, adopted: o.adopted, received: Int(o.received), sent: Int(o.sent))
    }

    private static func instanceName() -> String {
        let name = String(UIDevice.current.name.filter { $0.isLetter || $0.isNumber || " -_'’".contains($0) }.prefix(40))
        return name.trimmingCharacters(in: .whitespaces).isEmpty ? "Qasa" : name
    }
}

/// Resolves a Bonjour service to an IPv4 address and port with `NetService`, which (unlike
/// opening an `NWConnection` to learn the address) never connects to the host.
private final class AddressResolver: NSObject, NetServiceDelegate {
    struct Address {
        let host: String
        let port: UInt16
    }

    private let service: NetService
    private var completion: ((Address?) -> Void)?

    init(name: String, type: String, domain: String) {
        service = NetService(domain: domain.isEmpty ? "local." : domain, type: type, name: name)
        super.init()
        service.delegate = self
    }

    func resolve(_ completion: @escaping (Address?) -> Void) {
        self.completion = completion
        service.resolve(withTimeout: 8)
    }

    func netServiceDidResolveAddress(_ sender: NetService) {
        let port = UInt16(clamping: sender.port)
        let ipv4 = (sender.addresses ?? []).compactMap(Self.ipv4)
        guard port > 0, let host = ipv4.first else { return }
        finish(Address(host: host, port: port))
    }

    func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        finish(nil)
    }

    func netServiceDidStop(_ sender: NetService) {
        finish(nil)
    }

    private func finish(_ address: Address?) {
        guard let completion else { return }
        self.completion = nil
        service.stop()
        completion(address)
    }

    private nonisolated static func ipv4(_ data: Data) -> String? {
        data.withUnsafeBytes { raw -> String? in
            guard raw.count >= MemoryLayout<sockaddr_in>.size else { return nil }
            let family = raw.load(fromByteOffset: MemoryLayout<UInt8>.size, as: UInt8.self)
            guard family == UInt8(AF_INET) else { return nil }
            var addr = raw.load(as: sockaddr_in.self).sin_addr
            var buffer = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
            guard inet_ntop(AF_INET, &addr, &buffer, socklen_t(INET_ADDRSTRLEN)) != nil else { return nil }
            return String(cString: buffer)
        }
    }
}
