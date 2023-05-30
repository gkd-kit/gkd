package li.songe.gkd.selector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector_core.Selector

fun AccessibilityNodeInfo.getIndex(): Int {
    parent?.forEachIndexed { index, accessibilityNodeInfo ->
        if (accessibilityNodeInfo == this) {
            return index
        }
    }
    return 0
}

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, childNode: AccessibilityNodeInfo?) -> Unit) {
    var index = 0
    val childCount = this.childCount
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        action(index, child)
        index += 1
    }
}

fun AccessibilityNodeInfo.querySelector(selector: Selector): AccessibilityNodeInfo? {
    val ab = AbNode(this)
    val result = (ab.querySelector(selector) as AbNode?) ?: return null
    return result.value
}

fun AccessibilityNodeInfo.querySelectorAll(selector: Selector): Sequence<AbNode> {
    val ab = AbNode(this)
    return ab.querySelectorAll(selector) as Sequence<AbNode>
}

fun AccessibilityNodeInfo.click(service: AccessibilityService) = when {
    this.isClickable -> {
        this.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        "self"
    }

    else -> {
        val react = Rect()
        this.getBoundsInScreen(react)
        val x = react.left + 50f / 100f * (react.right - react.left)
        val y = react.top + 50f / 100f * (react.bottom - react.top)
        if (x >= 0 && y >= 0) {
            val gestureDescription = GestureDescription.Builder()
            val path = Path()
            path.moveTo(x, y)
            gestureDescription.addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            service.dispatchGesture(gestureDescription.build(), null, null)
            "(50%, 50%)"
        } else {
            "($x, $y) no click"
        }
    }
}

fun AccessibilityNodeInfo.getDepth(): Int {
    var p: AccessibilityNodeInfo? = this
    var depth = 0
    while (true) {
        val p2 = p?.parent
        if (p2 != null) {
            p = p2
            depth++
        } else {
            break
        }
    }
    return depth
}