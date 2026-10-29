package li.gkd.app.network

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import li.gkd.app.logging.LogUtils
import li.songe.codeorigin.CallSite

/** Only transport failures are summarized; disk I/O and unrecognized failures keep their stack. */
fun networkFailureSummary(error: Throwable): String? =
    when (error) {
        is ResponseException -> "HTTP ${error.response.status.value}"
        is HttpRequestTimeoutException,
        is SocketTimeoutException -> "network timeout"
        is UnknownHostException -> "DNS resolution failed"
        is ConnectException,
        is NoRouteToHostException -> "connection unavailable"
        is SocketException -> "socket connection failed"
        is SSLException -> "TLS ${error.javaClass.simpleName}"
        else -> null
    }

fun logNetworkFailure(
    operation: String,
    error: Exception,
    @CallSite loc: String = "",
) {
    val summary = networkFailureSummary(error)
    if (summary != null) LogUtils.w("$operation: $summary", loc = loc)
    else LogUtils.e(operation, error, loc = loc)
}
