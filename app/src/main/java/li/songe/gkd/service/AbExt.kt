package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.Selector
import li.songe.selector.Transform

val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }


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

/**
 * 此方法小概率造成无限节点片段,底层原因未知
 *
 * https://github.com/gkd-kit/gkd/issues/28
 */
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

//fun AccessibilityNodeInfo.querySelectorAll(selector: Selector) =
//    abTransform.querySelectorAll(this, selector)

// 不可以在 多线程/不同协程作用域 里同时使用
private val tempRect = Rect()
private fun AccessibilityNodeInfo.getTempRect(): Rect {
    getBoundsInScreen(tempRect)
    return tempRect
}

val abTransform = Transform<AccessibilityNodeInfo>(getAttr = { node, name ->
    when (name) {
        "id" -> node.viewIdResourceName
        "name" -> node.className
        "text" -> node.text
        "text.length" -> node.text?.length
        "desc" -> node.contentDescription
        "desc.length" -> node.contentDescription?.length

        "clickable" -> node.isClickable
        "checkable" -> node.isCheckable
        "checked" -> node.isChecked
        "focusable" -> node.isFocusable
        "visibleToUser" -> node.isVisibleToUser

        "left" -> node.getTempRect().left
        "top" -> node.getTempRect().top
        "right" -> node.getTempRect().right
        "bottom" -> node.getTempRect().bottom

        "width" -> node.getTempRect().width()
        "height" -> node.getTempRect().height()

        "index" -> node.getIndex()
        "depth" -> node.getDepth()
        "childCount" -> node.childCount
        else -> null
    }
}, getName = { node -> node.className }, getChildren = { node ->
    sequence {
        repeat(node.childCount) { i ->
            yield(node.getChild(i))
        }
    }
}, getChild = { node, index -> node.getChild(index) }, getParent = { node -> node.parent })


