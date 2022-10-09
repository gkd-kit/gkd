package li.songe.node_selector

import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, childNode: AccessibilityNodeInfo) -> Any) {
    var index = 0
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        if (child != null) {
            val result = action(index, child)
            if (result == true) {
                return
            }
        }
        index += 1
    }
}

fun AccessibilityNodeInfo.getIndex(parentNodeInfo: AccessibilityNodeInfo? = null): Int? {
    (parentNodeInfo ?: parent)?.forEachIndexed { index, accessibilityNodeInfo ->
        if (accessibilityNodeInfo == this) {
            return index
        }
    }
    return null
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
    return 0
}

inline fun AccessibilityNodeInfo.traverseAncestor(action: (depth: Int, ancestorNode: AccessibilityNodeInfo) -> Any) {
    var p: AccessibilityNodeInfo? = this
    var depth = 0
    while (true) {
        val p2 = p?.parent
        if (p2 != null) {
            p = p2
            depth++
            val result = action(depth, p2)
            if (result == true) {
                break
            }
        } else {
            break
        }
    }
}

inline fun AccessibilityNodeInfo.traverseElderBrother(action: (offset: Int, brotherNode: AccessibilityNodeInfo) -> Any) {
    val parentNode = parent ?: return
    val index = getIndex(parentNode) ?: return
    if (index == 0) {
        return
    }
    repeat(index) {
        val brother: AccessibilityNodeInfo? = parentNode.getChild(index - it - 1)
        if (brother != null) {
            val result = action(it + 1, brother)
            if (result == true) {
                return
            }
        }
    }
}

inline fun AccessibilityNodeInfo.traverseYoungerBrother(action: (offset: Int, brotherNode: AccessibilityNodeInfo) -> Any) {
    val parentNode = parent ?: return
    val index = getIndex(parentNode) ?: return
    if (index == parentNode.childCount - 1) {
        return
    }
    repeat(parentNode.childCount - index - 1) {
        val brother: AccessibilityNodeInfo? = parentNode.getChild(index + it + 1)
        if (brother != null) {
            val result = action(it + 1, brother)
            if (result == true) {
                return
            }
        }
    }
}

fun AccessibilityNodeInfo.traverseAll(action: (node: AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
    val stack = Stack<AccessibilityNodeInfo>()
    stack.push(this)
    while (stack.isNotEmpty()) {
        val top = stack.pop()
        if (action(top)) {
            return top
        }
        top.forEachIndexed { _, childNode ->
            stack.push(childNode)
        }
    }
    return null
}
