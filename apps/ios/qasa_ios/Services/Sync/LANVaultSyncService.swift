import Foundation
import Network
import CryptoKit

/// Premium same-LAN vault sync over TLS-ish TCP with a shared pairing code.
/// Ciphertext only — master password never leaves the device unencrypted.
@Observable
final class LANVaultSyncService {
    static let shared = LANVaultSyncService()

    private(set) var statusMessage: String = "Idle"
    private(set) var isHosting = false
    private(set) var isBrowsing = false

    private var listener: NWListener?
    private var browser: NWBrowser?
    private var connection: NWConnection?

    private let serviceType = "_qasa-sync._tcp"

    private init() {}

    func startHost(pairCode: String, vaultBlob: Data) {
        stop()
        isHosting = true
        statusMessage = "Waiting for peer on this Wi‑Fi…"
        do {
            let params = NWParameters.tcp
            params.includePeerToPeer = true
            listener = try NWListener(using: params)
            listener?.service = NWListener.Service(name: "Qasa-\(pairCode)", type: serviceType)
            listener?.newConnectionHandler = { [weak self] conn in
                self?.handleInbound(conn, pairCode: pairCode, vaultBlob: vaultBlob)
            }
            listener?.stateUpdateHandler = { [weak self] state in
                if case .ready = state {
                    self?.statusMessage = "Hosting — code \(pairCode)"
                }
            }
            listener?.start(queue: .global(qos: .userInitiated))
        } catch {
            statusMessage = error.localizedDescription
            isHosting = false
        }
    }

    func startBrowse(pairCode: String, onReceive: @escaping (Data) -> Void) {
        stop()
        isBrowsing = true
        statusMessage = "Looking for host…"
        let desc = NWBrowser.Descriptor.bonjour(type: serviceType, domain: nil)
        browser = NWBrowser(for: desc, using: .tcp)
        browser?.browseResultsChangedHandler = { [weak self] results, _ in
            guard let self else { return }
            for result in results {
                if case let .service(name: name, type: _, domain: _, interface: _) = result.endpoint,
                   name.contains(pairCode) {
                    self.connect(to: result.endpoint, pairCode: pairCode, onReceive: onReceive)
                    return
                }
            }
        }
        browser?.start(queue: .global(qos: .userInitiated))
    }

    func stop() {
        listener?.cancel()
        browser?.cancel()
        connection?.cancel()
        listener = nil
        browser = nil
        connection = nil
        isHosting = false
        isBrowsing = false
    }

    private func connect(to endpoint: NWEndpoint, pairCode: String, onReceive: @escaping (Data) -> Void) {
        let conn = NWConnection(to: endpoint, using: .tcp)
        connection = conn
        conn.stateUpdateHandler = { [weak self] state in
            if case .ready = state {
                self?.statusMessage = "Connected — authenticating…"
                let challenge = Self.authToken(pairCode: pairCode)
                conn.send(content: challenge, completion: .contentProcessed { _ in
                    self?.receiveBlob(conn, onReceive: onReceive)
                })
            }
        }
        conn.start(queue: .global(qos: .userInitiated))
    }

    private func handleInbound(_ conn: NWConnection, pairCode: String, vaultBlob: Data) {
        connection = conn
        conn.stateUpdateHandler = { [weak self] state in
            guard case .ready = state else { return }
            self?.statusMessage = "Peer connected — verifying…"
            conn.receive(minimumIncompleteLength: 32, maximumLength: 64) { data, _, _, _ in
                guard let data, data == Self.authToken(pairCode: pairCode) else {
                    self?.statusMessage = "Auth failed"
                    conn.cancel()
                    return
                }
                var payload = Data()
                var len = UInt32(vaultBlob.count).bigEndian
                payload.append(Data(bytes: &len, count: 4))
                payload.append(vaultBlob)
                conn.send(content: payload, completion: .contentProcessed { _ in
                    self?.statusMessage = "Vault sent securely."
                    conn.cancel()
                })
            }
        }
        conn.start(queue: .global(qos: .userInitiated))
    }

    private func receiveBlob(_ conn: NWConnection, onReceive: @escaping (Data) -> Void) {
        conn.receive(minimumIncompleteLength: 4, maximumLength: 4) { header, _, _, _ in
            guard let header, header.count == 4 else { return }
            let len = header.withUnsafeBytes { $0.load(as: UInt32.self).bigEndian }
            conn.receive(minimumIncompleteLength: Int(len), maximumLength: Int(len)) { body, _, _, _ in
                guard let body else { return }
                DispatchQueue.main.async {
                    self.statusMessage = "Vault received."
                    onReceive(body)
                }
                conn.cancel()
            }
        }
    }

    private static func authToken(pairCode: String) -> Data {
        let key = SymmetricKey(data: Data(pairCode.utf8))
        let mac = HMAC<SHA256>.authenticationCode(for: Data("qasa-lan-sync-v1".utf8), using: key)
        return Data(mac)
    }
}
