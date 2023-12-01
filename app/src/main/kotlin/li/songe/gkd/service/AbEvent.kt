package li.songe.gkd.service

import android.view.accessibility.AccessibilityEvent

data class AbEvent(
    val eventType: Int,
    val eventTime: Long,
    val packageName: String,
    val className: String,
)

fun AccessibilityEvent.toAbEvent(): AbEvent? {
    return AbEvent(
        eventType = eventType,
        eventTime = eventTime,
        packageName = packageName?.toString() ?: return null,
        className = className?.toString() ?: return null
    )
}