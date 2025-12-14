package li.songe.gkd.util

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.hjq.device.compat.DeviceBrand
import com.hjq.device.compat.DeviceMarketName
import com.hjq.device.compat.DeviceOs
import li.songe.gkd.META
import li.songe.gkd.app
import li.songe.loc.Loc
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.days

object LogUtils {
    @Loc
    fun d(
        vararg args: Any?,
        @Loc loc: String = "",
        @Loc("{fileName}") fileName: String = "",
        tag: String = fileName.substringBeforeLast('.'),
    ) {
        val name = Thread.currentThread().name
        val actualLoc = loc.substring("li.songe.gkd.".length)
        val texts = args.map { stringify(it) }
        if (META.debuggable) {
            val msg = buildString {
                append("$name, $actualLoc")
                texts.forEachIndexed { i, text ->
                    if (texts.size == 1) {
                        append("\n")
                    } else {
                        append("\n[$i]: ")
                    }
                    append(text)
                }
            }
            Log.d(tag, msg)
        }
        val t = System.currentTimeMillis()
        logFileExecutor.run {
            logToFile(tag, name, actualLoc, texts, t)
        }
    }
}

private val logFileExecutor = Executors.newSingleThreadExecutor()
private const val MAX_LOG_KEEP_DAYS = 7

private fun logToFile(tag: String, name: String, loc: String, texts: List<String>, t: Long) {
    val file = logFolder.resolve("gkd-${t.format("yyyyMMdd")}.log")
    val sb = StringBuilder()
    if (!file.exists()) {
        val files = logFolder.listFiles()
        if (files != null && files.size >= MAX_LOG_KEEP_DAYS) {
            files.forEach {
                if (t - it.lastModified() > MAX_LOG_KEEP_DAYS.days.inWholeMilliseconds) {
                    it.delete()
                }
            }
        }
        val deviceInfos = listOf(
            android.os.Build.MANUFACTURER,
            android.os.Build.MODEL,
            DeviceBrand.getBrandName(),
            DeviceOs.getOsName() + DeviceOs.getOsVersionName() + DeviceOs.getOsBigVersionCode(),
            DeviceMarketName.getMarketName(app)
        )
        sb.append("=== Log ===\n")
        sb.append("Date: ${t.format("yyyy-MM-dd HH:mm:ss.SSS")}\n")
        sb.append("Android: ${android.os.Build.VERSION.RELEASE} (${android.os.Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${deviceInfos.joinToString("/")}\n")
        sb.append("App: ${META.versionName} (${META.versionCode})\n")
        sb.append("=== Log ===\n\n")
    }
    sb.append(t.format("HH:mm:ss.SSS"))
    sb.append(" $tag, $name, $loc")
    texts.forEachIndexed { i, text ->
        if (texts.size == 1) {
            sb.append("\n")
        } else {
            sb.append("\n[$i]: ")
        }
        sb.append(text)
    }
    sb.append("\n\n")
    file.appendText(sb.toString())
}

private fun stringify(arg: Any?): String = when (arg) {
    is Bundle -> {
        val sb = StringBuilder()
        sb.append("Bundle{")
        val keys = arg.keySet()
        keys.forEachIndexed { index, key ->
            @Suppress("DEPRECATION")
            val value = arg.get(key)
            sb.append("$key=${stringify(value)}")
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
        arg.action?.let { sb.append("action=$it,") }
        arg.data?.let { sb.append("data=$it,") }
        arg.type?.let { sb.append("type=$it,") }
        arg.component?.let { sb.append("component=$it,") }
        arg.categories?.let { sb.append("categories=$it,") }
        arg.extras?.let { sb.append("extras=${stringify(it)}") }
        sb.append("}")
        sb.toString()
    }

    is Throwable -> Log.getStackTraceString(arg)

    else -> arg.toString()
}
