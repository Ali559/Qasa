package alibarznji.qasa.vpn

import alibarznji.qasa.network.VPNWireGuardConfig
import alibarznji.qasa.screens.vpn.formatBytes
import com.wireguard.config.InetNetwork
import com.wireguard.crypto.KeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardConfigFactoryTest {
    private val server = KeyPair()

    private fun cfg(ip: String = "10.8.0.7") = VPNWireGuardConfig(
        session_id = "s1",
        endpoint = "203.0.113.5:51820",
        server_public_key = server.publicKey.toBase64(),
        client_ip = ip,
        dns = listOf("1.1.1.1", "8.8.8.8"),
        allowed_ips = listOf("0.0.0.0/0", "::/0"),
        persistent_keepalive = 25,
    )

    @Test
    fun buildsInterfaceAndPeerFromServerConfig() {
        val device = KeyPair()
        val config = WireGuardConfigFactory.build(cfg(), device)

        val iface = config.`interface`
        assertEquals(device.publicKey, iface.keyPair.publicKey)
        assertEquals(listOf("10.8.0.7/32"), iface.addresses.map { it.toString() })
        assertEquals(setOf("1.1.1.1", "8.8.8.8"), iface.dnsServers.map { it.hostAddress }.toSet())
        assertTrue(iface.excludedApplications.isEmpty())

        val peer = config.peers.single()
        assertEquals(server.publicKey, peer.publicKey)
        assertEquals("203.0.113.5:51820", peer.endpoint.get().toString())
        assertEquals(setOf(InetNetwork.parse("0.0.0.0/0"), InetNetwork.parse("::/0")), peer.allowedIps)
        assertEquals(25, peer.persistentKeepalive.get())
    }

    @Test
    fun excludesTheGivenPackage() {
        val config = WireGuardConfigFactory.build(cfg(), KeyPair(), excludedPackage = "alibarznji.qasa")
        assertEquals(setOf("alibarznji.qasa"), config.`interface`.excludedApplications)
    }

    @Test
    fun keepsAnExistingPrefix() {
        assertEquals("10.8.0.7/32", WireGuardConfigFactory.withPrefix("10.8.0.7"))
        assertEquals("10.8.0.7/24", WireGuardConfigFactory.withPrefix("10.8.0.7/24"))
    }

    @Test
    fun publicWifiClassification() {
        assertTrue("pre-Android 12: type unknown", WifiTrust.isPublic(null))
        assertTrue("open", WifiTrust.isPublic(0))
        assertTrue("WEP", WifiTrust.isPublic(1))
        assertTrue("OWE", WifiTrust.isPublic(6))
        assertTrue("unknown", WifiTrust.isPublic(-1))
        assertFalse("WPA2 PSK", WifiTrust.isPublic(2))
        assertFalse("EAP", WifiTrust.isPublic(3))
        assertFalse("WPA3 SAE", WifiTrust.isPublic(4))
    }

    @Test
    fun formatsBinaryUnits() {
        java.util.Locale.setDefault(java.util.Locale.US)
        assertEquals("100.0 GB", formatBytes(100L * 1024 * 1024 * 1024))
        assertEquals("512 MB", formatBytes(512L * 1024 * 1024))
    }
}
