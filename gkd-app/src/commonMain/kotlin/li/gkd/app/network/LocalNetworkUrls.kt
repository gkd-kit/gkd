package li.gkd.app.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI

object LocalNetworkUrls {
    fun isNetworkUrl(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true)

    fun isLocalNetworkUrl(url: String): Boolean {
        val host = runCatching { URI(url).host }
            .getOrNull()
            ?.removePrefix("[")
            ?.removeSuffix("]")
            ?.substringBefore('%')
            ?.trimEnd('.')
            ?.lowercase()
            ?: return false
        if (
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".lan") ||
            host.endsWith(".home.arpa") ||
            ('.' !in host && ':' !in host)
        ) {
            return true
        }
        val ipv4Parts = host.split('.')
        if (ipv4Parts.size == 4) {
            val octets = ipv4Parts.map { part ->
                part.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
            }
            return octets[0] == 0 ||
                    octets[0] == 10 ||
                    octets[0] == 127 ||
                    (octets[0] == 169 && octets[1] == 254) ||
                    (octets[0] == 172 && octets[1] in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168)
        }
        if (':' !in host) return false
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        return address.isAnyLocalAddress ||
                address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)
    }

}
