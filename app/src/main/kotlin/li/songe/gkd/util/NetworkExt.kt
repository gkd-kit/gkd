package li.songe.gkd.util

import java.net.NetworkInterface

fun getIpAddressInLocalNetwork(): List<String> {
    val networkInterfaces = try {
        // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
        NetworkInterface.getNetworkInterfaces().iterator().asSequence()
    } catch (e: Exception) {
        toast("获取host失败:" + e.message)
        emptySequence()
    }
    val localAddresses = networkInterfaces.flatMap {
        it.inetAddresses.asSequence().filter { inetAddress ->
            inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                ?: false) && inetAddress.hostAddress != "127.0.0.1"
        }.map { inetAddress -> inetAddress.hostAddress }
    }
    return localAddresses.toList()
}