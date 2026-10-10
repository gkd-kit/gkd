package li.gkd.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ServiceManager
import android.view.WindowManager
import li.gkd.app.app
import li.gkd.app.permission.AndroidPermissions
import li.gkd.app.resources.*
import li.gkd.app.ui.text.getSync

object SystemServiceDump {
    private val systemServiceCache = hashMapOf<String, IBinder>()

    private fun dumpSystemService(name: String, vararg args: String): String {
        if (app.checkSelfPermission(AndroidPermissions.DUMP) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException(Res.string.permission_dump_required.getSync())
        }
        val binder = systemServiceCache.getOrPut(name) {
            checkNotNull(ServiceManager.getService(name)) { "System service $name is unavailable" }
        }
        return binder.dump(*args)
    }

    fun isUiAutomationRunning(): Boolean =
        containsUiAutomation(dumpSystemService(Context.ACCESSIBILITY_SERVICE))

    fun isFocusedWindowSecure(appId: String): Boolean? =
        parseFocusedWindowSecure(dumpSystemService(Context.WINDOW_SERVICE, "visible-apps"), appId)
}

private val uiAutomationDumpRegex = Regex("""\bUi Automation\[""")
private val legacyUserStateDumpRegex = Regex("""User state\[attributes:\{([\s\S]*?)services:\{""")
private val legacyCurrentUserRegex = Regex("""\bcurrentUser\s*=\s*true\b""")
private val legacyUiAutomationDumpRegex = Regex("""\bService\[""")

fun containsUiAutomation(dump: String): Boolean {
    if (uiAutomationDumpRegex.containsMatchIn(dump)) return true
    return legacyUserStateDumpRegex.findAll(dump).any { result ->
        val attributes = result.groupValues[1]
        legacyCurrentUserRegex.containsMatchIn(attributes) &&
                legacyUiAutomationDumpRegex.containsMatchIn(attributes)
    }
}

private val focusedWindowIdRegex = Regex("""Window\{([^\s}]+)""")
private val windowHeaderRegex = Regex("""(?m)^\s*Window(?:\s+#\d+)?\s+Window\{""")
private val windowFlagSeparatorRegex = Regex("""[\s|]+""")
private val secureWindowFlagNames = setOf("SECURE", "FLAG_SECURE")

fun parseFocusedWindowSecure(windowDump: String, appId: String): Boolean? {
    if (appId.isBlank()) return null
    val focusLine = windowDump.lineSequence()
        .firstOrNull { line -> "mCurrentFocus=Window{" in line }
        ?: return null
    if (!Regex("""(?:^|\s)${Regex.escape(appId)}/""").containsMatchIn(focusLine)) {
        return null
    }
    val windowId = focusedWindowIdRegex.find(focusLine)?.groupValues?.get(1)
        ?: return null
    val focusedWindowHeaderRegex = Regex(
        """(?m)^\s*Window(?:\s+#\d+)?\s+Window\{${Regex.escape(windowId)}[\s}][^\r\n]*"""
    )
    val header = focusedWindowHeaderRegex.find(windowDump) ?: return null
    val nextHeader = windowHeaderRegex.find(windowDump, header.range.last + 1)
    val blockEnd = nextHeader?.range?.first ?: windowDump.length
    val focusedWindowBlock = windowDump.substring(header.range.first, blockEnd)
    val rawFlags = focusedWindowBlock.substringAfter(" fl=", missingDelimiterValue = "")
    if (rawFlags.isEmpty()) return null
    val flagTokens = rawFlags
        .substringBefore('}')
        .trim()
        .split(windowFlagSeparatorRegex)
        .takeWhile { token -> '=' !in token }
    if (flagTokens.isEmpty()) return null
    if (flagTokens.any { token -> token in secureWindowFlagNames }) {
        return true
    }
    val numericFlags = flagTokens.first()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
        .toLongOrNull(16)
    return if (numericFlags != null) {
        numericFlags and WindowManager.LayoutParams.FLAG_SECURE.toLong() != 0L
    } else {
        false
    }
}
