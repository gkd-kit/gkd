package li.songe.gkd.selector

import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_core.Selector

fun AccessibilityNodeInfo.getIndex(): Int? {
    parent?.forEachIndexed { index, accessibilityNodeInfo ->
        if (accessibilityNodeInfo == this) {
            return index
        }
    }
    return null
}

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, childNode: AccessibilityNodeInfo) -> Unit) {
    var index = 0
    val childCount = this.childCount
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        if (child != null) {
            action(index, child)
        }
        index += 1
    }
}

fun AccessibilityNodeInfo.querySelector(selector: Selector): AccessibilityNodeInfo? {
    val ab = AbNode(this)
    val result = (ab.querySelector(selector) as AbNode?) ?: return null
    return result.value
}

fun AccessibilityNodeInfo.querySelectorAll(selector: Selector) =
    (AbNode(this).querySelectorAll(selector) as Sequence<AbNode>)