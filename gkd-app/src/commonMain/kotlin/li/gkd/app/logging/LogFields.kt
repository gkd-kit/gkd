package li.gkd.app.logging

import java.net.URI
import java.net.URISyntaxException

fun logText(value: String, limit: Int = 512): String {
    if (value.length <= limit) return value
    val end =
        if (limit > 0 && value[limit - 1].isHighSurrogate() && value[limit].isLowSurrogate())
            limit - 1
        else limit
    return value.take(end) + "…[truncated, length=${value.length}]"
}

/** Preserve the endpoint, not URL credentials, query values, fragments or opaque payloads. */
fun logUrl(value: String): String {
    val uri =
        try {
            URI(value)
        } catch (_: URISyntaxException) {
            return "[invalid URL]"
        }
    if (uri.isOpaque) return "${uri.scheme}:[omitted]"
    return logText(
        buildString {
            uri.scheme?.let {
                append(it)
                append(':')
            }
            if (uri.rawAuthority != null) {
                append("//")
                append(uri.host ?: "[host]")
                if (uri.port >= 0) {
                    append(':')
                    append(uri.port)
                }
            }
            append(uri.rawPath.orEmpty())
        },
    )
}
