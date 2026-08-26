package li.songe.gkd.priv

import android.content.Context
import android.view.IWindowManager
import android.view.WindowManager
import priv.kit.core.binder.PrivilegeBinderWrapper

class CompatWindowManager {
    val value: IWindowManager = IWindowManager.Stub.asInterface(
        requireNotNull(
            PrivilegeBinderWrapper.fromSystemService(Context.WINDOW_SERVICE),
        ),
    )

    companion object {
        private const val ROTATION_WITHOUT_CALLER = 1
        private const val ROTATION_WITH_CALLER = 2

        // https://diff.songe.li/i/IWindowManager.freezeRotation
        private val freezeRotationType by lazy {
            IWindowManager::class.detectHiddenMethod(
                "freezeRotation",
                ROTATION_WITHOUT_CALLER to listOf(Int::class),
                ROTATION_WITH_CALLER to listOf(Int::class, String::class),
            )
        }

        // https://diff.songe.li/i/IWindowManager.thawRotation
        private val thawRotationType by lazy {
            IWindowManager::class.detectHiddenMethod(
                "thawRotation",
                ROTATION_WITHOUT_CALLER to emptyList(),
                ROTATION_WITH_CALLER to listOf(String::class),
            )
        }
    }

    fun freezeRotation(rotation: Int, caller: String) = when (freezeRotationType) {
        ROTATION_WITHOUT_CALLER -> value.freezeRotation(rotation)
        ROTATION_WITH_CALLER -> value.freezeRotation(rotation, caller)
        else -> throw NoSuchMethodException("IWindowManager.freezeRotation")
    }

    fun thawRotation(caller: String) = when (thawRotationType) {
        ROTATION_WITHOUT_CALLER -> value.thawRotation()
        ROTATION_WITH_CALLER -> value.thawRotation(caller)
        else -> throw NoSuchMethodException("IWindowManager.thawRotation")
    }

    fun isFocusedWindowSecure(appId: String): Boolean? {
        return parseFocusedWindowSecure(value.asBinder().dump("visible-apps"), appId)
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
