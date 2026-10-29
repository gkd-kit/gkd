package li.gkd.app.logging

import android.content.Intent
import android.os.Bundle
import android.util.Log
import li.gkd.app.data.AndroidLogMetadata

actual fun logMetadata(): LogMetadata = AndroidLogMetadata.create()

actual fun writePlatformLog(level: LogLevel, tag: String, message: String) {
    Log.println(
        when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        },
        tag,
        message,
    )
}

actual fun formatLogValue(value: Any?): String =
    when (value) {
        is Bundle ->
            "Bundle{keys=${value.keySet().take(16).map { logText(it, 80) }}, size=${value.size()}}"
        is Intent ->
            buildString {
                append("Intent{")
                value.action?.let { append("action=${logText(it)},") }
                value.data?.let { append("data=${logUrl(it.toString())},") }
                value.type?.let { append("type=${logText(it)},") }
                value.component?.let { append("component=$it,") }
                value.categories?.let { append("categories=${it.take(16)},") }
                value.extras?.let { append("extras=${formatLogValue(it)}") }
                append("}")
            }
        is Throwable -> value.stackTraceToString()
        else -> value.toString()
    }
