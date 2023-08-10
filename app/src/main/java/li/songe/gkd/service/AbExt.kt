package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.Transform
import li.songe.selector.Selector

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
            null
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


fun AccessibilityNodeInfo.querySelector(selector: Selector) =
    abTransform.querySelector(this, selector)

fun AccessibilityNodeInfo.querySelectorAll(selector: Selector) =
    abTransform.querySelectorAll(this, selector)

// 不可以在 多线程/不同协程作用域 里同时使用
private val tempRect = Rect()
private fun AccessibilityNodeInfo.getTempRect(): Rect {
    getBoundsInScreen(tempRect)
    return tempRect
}

val abTransform = Transform<AccessibilityNodeInfo>(
    getAttr = { node, name ->
        when (name) {
            "id" -> node.viewIdResourceName
            "name" -> node.className
            "text" -> node.text
            "textLen" -> node.text?.length
            "desc" -> node.contentDescription
            "descLen" -> node.contentDescription?.length
            "childCount" -> node.childCount

            "isEnabled" -> node.isEnabled
            "isClickable" -> node.isClickable
            "isChecked" -> node.isChecked
            "isCheckable" -> node.isCheckable
            "isFocused" -> node.isFocused
            "isFocusable" -> node.isFocusable
            "isVisibleToUser" -> node.isVisibleToUser
            ""->node.isAccessibilityFocused

            "left" -> node.getTempRect().left
            "top" -> node.getTempRect().top
            "right" -> node.getTempRect().right
            "bottom" -> node.getTempRect().bottom

            "width" -> node.getTempRect().width()
            "height" -> node.getTempRect().height()

            "index" -> node.getIndex()
            "depth" -> node.getDepth()
            else -> null
        }
    },
    getName = { node -> node.className },
    getChildren = { node ->
        sequence {
            repeat(node.childCount) { i ->
                yield(node.getChild(i))
            }
        }
    },
    getChild = { node, index -> node.getChild(index) },
    getParent = { node -> node.parent }
)



