package li.gkd.app.matching.input

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.contracts.contract
import li.gkd.app.matching.query.setGeneratedTime
import li.gkd.app.util.monotonicTimeMillis

const val STATE_CHANGED = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
const val CONTENT_CHANGED = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

// 某些应用耗时 300ms
private val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() =
        if (className == null) {
            null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
        } else {
            source?.setGeneratedTime()
        }

fun AccessibilityEvent?.isUseful(): Boolean {
    contract {
        returns(true) implies (this@isUseful != null)
    }
    return (this != null &&
        packageName != null &&
        className != null &&
        (eventType == STATE_CHANGED || eventType == CONTENT_CHANGED))
}

class A11yEvent(
    val key: MatchEventKey,
    val sourceTimeMillis: Long,
    val time: Long,
    private var event: AccessibilityEvent?,
    val diagnosticEventId: Long? = null,
) : AutoCloseable {
    @Suppress("DEPRECATION")
    fun takeSource(): AccessibilityNodeInfo? {
        // Transfer ownership before Binder: cleanup must never wait for a stalled source read.
        val owned = synchronized(this) { event.also { event = null } } ?: return null
        return try {
            owned.safeSource
        } finally {
            owned.recycle()
        }
    }

    @Suppress("DEPRECATION")
    override fun close(): Unit =
        synchronized(this) {
            event?.recycle()
            event = null
        }
}

// AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失, 导致异步子线程获取到的数据不一致
@Suppress("DEPRECATION")
fun AccessibilityEvent.toA11yEvent(
    key: MatchEventKey,
    diagnosticEventId: Long? = null,
): A11yEvent? {
    if (packageName == null || className == null) return null
    return A11yEvent(
        key = key,
        sourceTimeMillis = eventTime,
        time = monotonicTimeMillis(),
        event = AccessibilityEvent.obtain(this),
        diagnosticEventId = diagnosticEventId,
    )
}
