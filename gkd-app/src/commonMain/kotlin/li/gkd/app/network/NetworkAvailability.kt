package li.gkd.app.network

import java.net.InetAddress

object NetworkAvailability {
    /** Existing preflight heuristic, not a guarantee of Internet connectivity. */
    fun canResolveProbeHost(): Boolean = try {
        InetAddress.getByName("www.baidu.com") != null
    } catch (_: Exception) {
        false
    }
}
