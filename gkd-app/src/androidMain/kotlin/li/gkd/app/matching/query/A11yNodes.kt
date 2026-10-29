package li.gkd.app.matching.query

import android.view.accessibility.AccessibilityNodeInfo
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.monotonicTimeMillis
import li.gkd.selector.createDefaultSelectorTypeModel

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length,
        )
    }
    return null
}

// https://github.com/gkd-kit/gkd/issues/115
// https://github.com/gkd-kit/gkd/issues/650
// 限制节点遍历的数量避免内存溢出
const val MAX_CHILD_SIZE = 512
const val MAX_DESCENDANTS_SIZE = 4096

private const val A11Y_NODE_TIME_KEY = "generatedTime"

fun AccessibilityNodeInfo.setGeneratedTime(): AccessibilityNodeInfo {
    extras.putLong(A11Y_NODE_TIME_KEY, monotonicTimeMillis())
    return this
}

fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        // https://github.com/gkd-kit/gkd/issues/759
        return true
    }
    return (monotonicTimeMillis() - generatedTime) > expiryMillis
}

val selectorTypeModel
    get() = createDefaultSelectorTypeModel()

val AccessibilityNodeInfo.compatChecked: Boolean?
    get() =
        if (AndroidTarget.BAKLAVA) {
            when (checked) {
                AccessibilityNodeInfo.CHECKED_STATE_TRUE -> true
                AccessibilityNodeInfo.CHECKED_STATE_FALSE -> false
                AccessibilityNodeInfo.CHECKED_STATE_PARTIAL -> null
                else -> null
            }
        } else {
            @Suppress("DEPRECATION") isChecked
        }
