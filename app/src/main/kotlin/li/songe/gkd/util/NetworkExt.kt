package li.songe.gkd.util

import java.net.NetworkInterface

fun getIpAddressInLocalNetwork(): List<String> {
    val networkInterfaces = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
    } catch (e: Exception) {
        // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
        toast("获取host失败:" + e.message)
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
