import Foundation

@Observable
final class VPNService {
    static let shared = VPNService()

    private(set) var connected = false
    private(set) var nodeName: String?
    private(set) var city: String?
    private(set) var assignedIP: String?
    private(set) var isBusy = false
    private(set) var lastError: String?

    private init() {}

    func refreshStatus() async {
        do {
            let status: VPNStatusResponse = try await QasaAPIClient.shared.request(
                QasaConfig.vpnStatusURL
            )
            connected = status.connected
            nodeName = status.node
            city = status.city
            assignedIP = status.assigned_ip
            lastError = nil
        } catch {
            lastError = L10n.message(for: error)
        }
    }

    func connect() async {
        isBusy = true
        defer { isBusy = false }
        do {
            let _: VPNConnectResponse = try await QasaAPIClient.shared.request(
                QasaConfig.vpnConnectURL,
                method: "POST",
                body: VPNConnectRequest(client_public_key: nil)
            )
            await refreshStatus()
        } catch {
            lastError = L10n.message(for: error)
            connected = false
        }
    }

    func disconnect() async {
        isBusy = true
        defer { isBusy = false }
        do {
            let _: VPNDisconnectResponse = try await QasaAPIClient.shared.request(
                QasaConfig.vpnDisconnectURL,
                method: "POST",
                body: Optional<String>.none
            )
            connected = false
            nodeName = nil
            city = nil
            assignedIP = nil
            lastError = nil
        } catch {
            lastError = L10n.message(for: error)
        }
    }
}
