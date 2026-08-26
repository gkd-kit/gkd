package li.songe.gkd.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkExtTest {
    @Test
    fun isLocalNetworkUrlRecognizesLocalHosts() {
        listOf(
            "http://localhost/subscription.json",
            "http://gkd.local/subscription.json",
            "http://router.lan/subscription.json",
            "http://nas/subscription.json",
            "http://10.0.0.1/subscription.json",
            "http://172.16.0.1/subscription.json",
            "http://172.31.255.255/subscription.json",
            "http://192.168.1.1/subscription.json",
            "http://169.254.1.1/subscription.json",
            "http://127.0.0.1/subscription.json",
            "http://[::1]/subscription.json",
            "http://[fc00::1]/subscription.json",
            "http://[fe80::1]/subscription.json",
        ).forEach { url ->
            assertTrue(url, isLocalNetworkUrl(url))
        }
    }

    @Test
    fun isLocalNetworkUrlIgnoresPublicAndInvalidHosts() {
        listOf(
            "https://example.com/subscription.json",
            "http://8.8.8.8/subscription.json",
            "http://172.15.255.255/subscription.json",
            "http://172.32.0.0/subscription.json",
            "not a URL",
        ).forEach { url ->
            assertFalse(url, isLocalNetworkUrl(url))
        }
    }
}
