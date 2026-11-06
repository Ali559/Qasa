package alibarznji.qasa.vpn

import alibarznji.qasa.network.VPNWireGuardConfig
import com.wireguard.config.Config
import com.wireguard.config.InetAddresses
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import com.wireguard.crypto.KeyPair

/** Turns the config from `POST /v1/vpn/connect` into a wireguard-android [Config]. */
object WireGuardConfigFactory {
    /**
     * [excludedPackage] keeps one app outside the tunnel. Only debug builds use it, so the app
     * can still reach a local dev API while connected to a seeded node.
     *
     * The node pool is IPv4 only, but `::/0` stays in the allowed IPs on purpose: IPv6 is
     * routed into the tunnel and dropped there instead of leaking around it.
     */
    fun build(cfg: VPNWireGuardConfig, keyPair: KeyPair, excludedPackage: String? = null): Config {
        val iface = Interface.Builder()
            .setKeyPair(keyPair)
            .addAddress(InetNetwork.parse(withPrefix(cfg.client_ip)))
            .apply {
                cfg.dns.forEach { addDnsServer(InetAddresses.parse(it)) }
                excludedPackage?.let { excludeApplication(it) }
            }
            .build()
        val peer = Peer.Builder()
            .parsePublicKey(cfg.server_public_key)
            .parseEndpoint(cfg.endpoint)
            .parseAllowedIPs(cfg.allowed_ips.joinToString(","))
            .setPersistentKeepalive(cfg.persistent_keepalive)
            .build()
        return Config.Builder().setInterface(iface).addPeer(peer).build()
    }

    /** The server sends a bare address; tolerate one that already has a prefix. */
    internal fun withPrefix(ip: String): String = if ('/' in ip) ip else "$ip/32"
}
