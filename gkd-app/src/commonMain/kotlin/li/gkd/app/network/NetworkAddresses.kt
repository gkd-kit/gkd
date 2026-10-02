package li.gkd.app.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket

object NetworkAddresses {
    fun localIpv4Addresses(): List<String> = NetworkInterface.getNetworkInterfaces().asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filter { it.isSiteLocalAddress && it is Inet4Address }
        .mapNotNull { it.hostAddress }.toList()

    fun isPortAvailable(port: Int): Boolean = try {
        ServerSocket(port).use { true }
    } catch (_: Exception) {
        false
    }
}
