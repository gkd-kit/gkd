package li.gkd.app.network

import io.ktor.client.request.prepareGet
import kotlinx.coroutines.CancellationException
import li.gkd.app.util.LogUtils

/** Any HTTPS response confirms probe reachability, not that an update host is reachable. */
suspend fun canReachNetwork(): Boolean = try {
    NetworkClients.probeClient.prepareGet("https://baidu.com/").execute { true }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LogUtils.d("Network probe failed", e)
    false
}
