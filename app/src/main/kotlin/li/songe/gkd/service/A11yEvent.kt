package li.songe.gkd.service

import android.view.accessibility.AccessibilityEvent

data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val className: String,
)

fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = packageName?.toString() ?: return null,
        className = className?.toString() ?: return null
    )
}