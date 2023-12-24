package li.songe.gkd.service

import android.view.accessibility.AccessibilityEvent

data class AbEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val className: String,
)

fun AccessibilityEvent.toAbEvent(): AbEvent? {
    return AbEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = packageName?.toString() ?: return null,
        className = className?.toString() ?: return null
    )
}