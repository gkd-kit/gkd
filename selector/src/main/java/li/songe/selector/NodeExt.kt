package li.songe.selector

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

inline fun AccessibilityNodeInfo.forEach(action: (childNode: AccessibilityNodeInfo) -> Unit) {
    var index = 0
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        if (child != null) {
            action(child)
        }
        index += 1
    }
}

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, childNode: AccessibilityNodeInfo) -> Unit) {
    var index = 0
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        if (child != null) {
            action(index, child)
        }
        index += 1
    }
}

inline fun AccessibilityNodeInfo.forEachAncestorIndexed(action: (depth: Int, ancestorNode: AccessibilityNodeInfo) -> Unit) {
    var p: AccessibilityNodeInfo? = this
    var depth = 0
    while (true) {
        val p2 = p?.parent
        if (p2 != null) {
            p = p2
            depth++
            action(depth, p2)
        } else {
            break
        }
    }
}

inline fun AccessibilityNodeInfo.forEachElderBrotherIndexed(action: (offset: Int, brotherNode: AccessibilityNodeInfo) -> Unit) {
    val parentNode = parent ?: return
    val index = getIndex(parentNode) ?: return
    if (index == 0) {
        return
    }
    repeat(index) {
        val brother: AccessibilityNodeInfo? = parentNode.getChild(index - it - 1)
        if (brother != null) {
            action(it + 1, brother)
        }
    }
}

inline fun AccessibilityNodeInfo.forEachYoungerBrotherIndexed(action: (offset: Int, brotherNode: AccessibilityNodeInfo) -> Unit) {
    val parentNode = parent ?: return
    val index = getIndex(parentNode) ?: return
    if (index == parentNode.childCount - 1) {
        return
    }
    repeat(parentNode.childCount - index - 1) {
        val brother: AccessibilityNodeInfo? = parentNode.getChild(index + it + 1)
        if (brother != null) {
            action(it + 1, brother)
        }
    }
}

fun AccessibilityNodeInfo.traverse() = sequence {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.push(this@traverse)
    while (stack.isNotEmpty()) {
        val top = stack.pop()
        yield(top)
        top.forEach { childNode ->
            stack.push(childNode)
        }
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
    return depth
}

fun AccessibilityNodeInfo.getAncestor(dep: Int): AccessibilityNodeInfo? {
    forEachAncestorIndexed { depth, ancestorNode ->
        if (depth == dep) {
            return ancestorNode
        }
    }
    return null
}

fun AccessibilityNodeInfo.getBrother(dep: Int, elder: Boolean = true): AccessibilityNodeInfo? {
    if (elder) {
        forEachElderBrotherIndexed { offset, brotherNode ->
            if (offset == dep) {
                return brotherNode
            }
        }
    } else {
        forEachYoungerBrotherIndexed { offset, brotherNode ->
            if (offset == dep) {
                return brotherNode
            }
        }
    }
    return null
}
