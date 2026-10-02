package li.gkd.app.logging

import android.content.Intent
import android.os.Bundle
import android.util.Log
import li.gkd.app.META
import li.gkd.app.data.AndroidLogMetadata

actual fun logMetadata(): LogMetadata = AndroidLogMetadata.create()
actual fun isLogDebuggable(): Boolean = META.debuggable

actual fun writePlatformLog(tag: String, message: String) {
    Log.d(tag, message)
}

actual fun formatLogValue(value: Any?): String = when (value) {
    is Bundle -> {
        val sb = StringBuilder()
        sb.append("Bundle{")
        val keys = value.keySet()
        keys.forEachIndexed { index, key ->
            @Suppress("DEPRECATION")
            val item = value.get(key)
            sb.append("$key=${formatLogValue(item)}")
            if (index < keys.size - 1) {
                sb.append(",")
            }
        }
        sb.append("}")
        sb.toString()
    }

    is Intent -> {
        val sb = StringBuilder()
        sb.append("Intent{")
        value.action?.let { sb.append("action=$it,") }
        value.data?.let { sb.append("data=$it,") }
        value.type?.let { sb.append("type=$it,") }
        value.component?.let { sb.append("component=$it,") }
        value.categories?.let { sb.append("categories=$it,") }
        value.extras?.let { sb.append("extras=${formatLogValue(it)}") }
        sb.append("}")
        sb.toString()
    }

    is Throwable -> Log.getStackTraceString(value)

    else -> value.toString()
}
