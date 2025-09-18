package li.songe.gkd.util

import java.net.InetAddress

object NetworkUtils {
    fun isAvailable(): Boolean = try {
        InetAddress.getByName("www.baidu.com") != null
    } catch (_: Throwable) {
        false
    }
}