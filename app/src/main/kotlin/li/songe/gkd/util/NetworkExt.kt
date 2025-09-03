package li.songe.gkd.util

import java.net.NetworkInterface
import java.net.ServerSocket

fun getIpAddressInLocalNetwork(): List<String> {
    val networkInterfaces = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
    } catch (e: Exception) {
        // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
        toast("获取HOST失败:" + e.message)
        return emptyList()
    }
    val localAddresses = networkInterfaces.flatMap {
        it.inetAddresses.asSequence().filter { inetAddress ->
            inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                ?: false) && inetAddress.hostAddress != "127.0.0.1"
        }.map { inetAddress -> inetAddress.hostAddress }
    }
    return localAddresses.toList()
}


fun isPortAvailable(port: Int): Boolean {
    var serverSocket: ServerSocket? = null
    return try {
        serverSocket = ServerSocket(port)
        serverSocket.reuseAddress = true
        true
    } catch (_: Exception) {
        false
    } finally {
        serverSocket?.close()
    }
}