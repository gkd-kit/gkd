package li.gkd.app.data

import android.view.accessibility.AccessibilityEvent
import li.gkd.db.A11yEventLog

fun AccessibilityEvent.toA11yEventLog(id: Int) = A11yEventLog(
    id = id,
    ctime = System.currentTimeMillis(),
    type = eventType,
    appId = packageName.toString(),
    name = className.toString(),
    desc = contentDescription?.toString(),
    text = text.map { it.toString() },
)

val A11yEventLog.isStateChanged: Boolean
    get() = type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

val A11yEventLog.fixedName: String
    get() {
        if (isStateChanged && name.startsWith(appId)) {
            return name.substring(appId.length)
        }
        if (name.contains("View") || name.contains("Layout") || viewSuffixes.any(name::startsWith)) {
            return name.substring(name.lastIndexOf('.') + 1)
        }
        return name
    }

private val viewSuffixes = listOf(
    "android.widget.",
    "android.view.",
    "android.support.",
)
