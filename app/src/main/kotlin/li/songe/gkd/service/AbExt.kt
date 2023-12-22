package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.ToastUtils
import li.songe.gkd.util.storeFlow
import li.songe.selector.Selector
import li.songe.selector.Transform

val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow
        // 在主线程调用会阻塞界面导致卡顿
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

fun AccessibilityNodeInfo.querySelector(
    selector: Selector,
    quickFind: Boolean = false,
): AccessibilityNodeInfo? {
    if (selector.isMatchRoot) {
        if (parent == null) {
            val trackNodes = mutableListOf<AccessibilityNodeInfo>()
            return selector.match(this, abTransform, trackNodes)
        }
        return null
    }
    if (quickFind && selector.canQf) {
        val qfIdValue = selector.qfIdValue
        val qfVidValue = selector.qfVidValue
        val qfTextValue = selector.qfTextValue
        val nodes = (if (qfIdValue != null) {
            findAccessibilityNodeInfosByViewId(qfIdValue)
        } else if (qfVidValue != null) {
            findAccessibilityNodeInfosByViewId("$packageName:id/$qfVidValue")
        } else if (qfTextValue != null) {
            findAccessibilityNodeInfosByText(qfTextValue)
        } else {
            emptyList()
        })
        if (nodes.isNotEmpty()) {
            val trackNodes = mutableListOf<AccessibilityNodeInfo>()
            nodes.forEach { childNode ->
                val targetNode = selector.match(childNode, abTransform, trackNodes)
                if (targetNode != null) return targetNode
            }
        }
        return null
    }
    // 在一些开屏广告的界面会造成1-2s的阻塞
    return abTransform.querySelector(this, selector)
}

// 不可以在 多线程/不同协程作用域 里同时使用
private val tempRect = Rect()
private fun AccessibilityNodeInfo.getTempRect(): Rect {
    getBoundsInScreen(tempRect)
    return tempRect
}


// https://github.com/gkd-kit/gkd/issues/115
private const val MAX_CHILD_SIZE = 256
private const val MAX_DESCENDANTS_SIZE = 4096

private val getChildren: (AccessibilityNodeInfo) -> Sequence<AccessibilityNodeInfo> = { node ->
    sequence {
        repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
            val child = node.getChild(i) ?: return@sequence
            yield(child)
        }
    }
}

private val getAttr: (AccessibilityNodeInfo, String) -> Any? = { node, name ->
    when (name) {
        "id" -> node.viewIdResourceName
        "vid" -> node.viewIdResourceName?.let { id ->
            id.subSequence(
                (node.packageName?.length ?: 0) + ":id/".length,
                id.length
            )
        }

        "name" -> node.className
        "text" -> node.text
        "text.length" -> node.text?.length
        "desc" -> node.contentDescription
        "desc.length" -> node.contentDescription?.length

        "clickable" -> node.isClickable
        "focusable" -> node.isFocusable
        "checkable" -> node.isCheckable
        "checked" -> node.isChecked
        "editable" -> node.isEditable
        "longClickable" -> node.isLongClickable
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
}

val abTransform = Transform(
    getAttr = getAttr,
    getName = { node -> node.className },
    getChildren = getChildren,
    getChild = { node, index -> if (index in 0..<node.childCount) node.getChild(index) else null },
    getParent = { node -> node.parent },
    getDescendants = { node ->
        sequence {
            val stack = getChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            val tempNodes = mutableListOf<AccessibilityNodeInfo>()
            do {
                val top = stack.removeLast()
                yield(top)
                for (childNode in getChildren(top)) {
                    tempNodes.add(childNode)
                }
                if (tempNodes.isNotEmpty()) {
                    for (i in tempNodes.size - 1 downTo 0) {
                        stack.add(tempNodes[i])
                    }
                    tempNodes.clear()
                }
            } while (stack.isNotEmpty())
        }.take(MAX_DESCENDANTS_SIZE)
    })

private var lastToastTime = -1L
fun toastClickTip() {
    if (storeFlow.value.toastWhenClick) {
        val t = System.currentTimeMillis()
        if (t - lastToastTime > 3000) {
            ToastUtils.showShort(storeFlow.value.clickToast)
            lastToastTime = t
        }
    }
}

