package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.data.PrimitiveValue

val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow
        // 在主线程调用会阻塞界面导致卡顿
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
    } else {
        try {
            // 仍然报错 Cannot perform this action on a not sealed instance.
            // TODO 原因未知
            source
        } catch (e: Exception) {
            null
        }
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

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

fun AccessibilityNodeInfo.querySelector(
    selector: Selector,
    quickFind: Boolean = false,
    transform: Transform<AccessibilityNodeInfo>,
): AccessibilityNodeInfo? {
    if (selector.isMatchRoot) {
        if (parent == null) {
            return selector.match(this, transform)
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
            val trackNodes = ArrayList<AccessibilityNodeInfo>(selector.tracks.size)
            nodes.forEach { childNode ->
                val targetNode = selector.match(childNode, transform, trackNodes)
                if (targetNode != null) return targetNode
            }
        }
        return null
    }
    // 在一些开屏广告的界面会造成1-2s的阻塞
    return transform.querySelector(this, selector)
}


// https://github.com/gkd-kit/gkd/issues/115
// 限制节点遍历的数量避免内存溢出
private const val MAX_CHILD_SIZE = 512
private const val MAX_DESCENDANTS_SIZE = 4096

val getChildren: (AccessibilityNodeInfo) -> Sequence<AccessibilityNodeInfo> = { node ->
    sequence {
        repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
            val child = node.getChild(i) ?: return@sequence
            yield(child)
        }
    }
}

val allowPropertyNames by lazy {
    mapOf(
        "id" to PrimitiveValue.StringValue.TYPE_NAME,
        "vid" to PrimitiveValue.StringValue.TYPE_NAME,

        "name" to PrimitiveValue.StringValue.TYPE_NAME,
        "text" to PrimitiveValue.StringValue.TYPE_NAME,
        "text.length" to PrimitiveValue.IntValue.TYPE_NAME,
        "desc" to PrimitiveValue.StringValue.TYPE_NAME,
        "desc.length" to PrimitiveValue.IntValue.TYPE_NAME,

        "clickable" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "focusable" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "checkable" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "checked" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "editable" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "longClickable" to PrimitiveValue.BooleanValue.TYPE_NAME,
        "visibleToUser" to PrimitiveValue.BooleanValue.TYPE_NAME,

        "left" to PrimitiveValue.IntValue.TYPE_NAME,
        "top" to PrimitiveValue.IntValue.TYPE_NAME,
        "right" to PrimitiveValue.IntValue.TYPE_NAME,
        "bottom" to PrimitiveValue.IntValue.TYPE_NAME,
        "width" to PrimitiveValue.IntValue.TYPE_NAME,
        "height" to PrimitiveValue.IntValue.TYPE_NAME,

        "index" to PrimitiveValue.IntValue.TYPE_NAME,
        "depth" to PrimitiveValue.IntValue.TYPE_NAME,
        "childCount" to PrimitiveValue.IntValue.TYPE_NAME,
    )
}

fun Selector.checkSelector(): String? {
    binaryExpressions.forEach { e ->
        if (!allowPropertyNames.contains(e.name)) {
            return "未知属性:${e.name}"
        }
        if (e.value.type != "null" && allowPropertyNames[e.name] != e.value.type) {
            return "非法类型:${e.name}=${e.value.type}"
        }
    }
    return null
}

private fun createGetAttr(): ((AccessibilityNodeInfo, String) -> Any?) {
    var tempNode: AccessibilityNodeInfo? = null
    val tempRect = Rect()
    var tempVid: CharSequence? = null
    fun AccessibilityNodeInfo.getTempRect(): Rect {
        if (this !== tempNode) {
            getBoundsInScreen(tempRect)
            tempNode = this
        }
        return tempRect
    }

    fun AccessibilityNodeInfo.getTempVid(): CharSequence? {
        if (this !== tempNode) {
            tempVid = getVid()
            tempNode = this
        }
        return tempVid
    }
    return { node, name ->
        when (name) {
            "id" -> node.viewIdResourceName
            "vid" -> node.getTempVid()

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
}

data class CacheTransform(
    val transform: Transform<AccessibilityNodeInfo>,
    val indexCache: HashMap<AccessibilityNodeInfo, Int>,
)

fun createCacheTransform(): CacheTransform {
    val indexCache = HashMap<AccessibilityNodeInfo, Int>()
    fun AccessibilityNodeInfo.getChildX(index: Int): AccessibilityNodeInfo? {
        return getChild(index)?.also { child ->
            indexCache[child] = index
        }
    }

    fun AccessibilityNodeInfo.getIndexX(): Int {
        indexCache[this]?.let { return it }
        parent?.forEachIndexed { index, child ->
            if (child != null) {
                indexCache[child] = index
            }
            if (child == this) {
                return index
            }
        }
        return 0
    }

    val getChildrenCache: (AccessibilityNodeInfo) -> Sequence<AccessibilityNodeInfo> = { node ->
        sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = node.getChildX(index) ?: return@sequence
                yield(child)
            }
        }
    }
    val getAttr = createGetAttr()
    val transform = Transform(
        getAttr = { node, name ->
            when (name) {
                "index" -> {
                    node.getIndexX()
                }

                else -> {
                    getAttr(node, name)
                }
            }
        },
        getName = { node -> node.className },
        getChildren = getChildrenCache,
        getParent = { node -> node.parent },
        getDescendants = { node ->
            sequence {
                val stack = getChildrenCache(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                do {
                    val top = stack.removeLast()
                    yield(top)
                    for (childNode in getChildrenCache(top)) {
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
        },
        getChildrenX = { node, connectExpression ->
            sequence {
                repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (connectExpression.checkOffset(offset)) {
                        val child = node.getChildX(offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
        getBeforeBrothers = { node, connectExpression ->
            sequence {
                val parentVal = node.parent ?: return@sequence
                val index =
                    indexCache[node] // 如果 node 由 quickFind 得到, 则第一次调用此方法可能得到 indexCache 是空
                if (index != null) {
                    var i = index - 1
                    var offset = 0
                    while (0 <= i && i < parentVal.childCount) {
                        connectExpression.maxOffset?.let { maxOffset ->
                            if (offset > maxOffset) return@sequence
                        }
                        if (connectExpression.checkOffset(offset)) {
                            val child = parentVal.getChild(i) ?: return@sequence
                            yield(child)
                        }
                        i--
                        offset++
                    }
                } else {
                    val list =
                        getChildrenCache(parentVal).takeWhile { it != node }.toMutableList()
                    list.reverse()
                    yieldAll(list.filterIndexed { i, _ ->
                        connectExpression.checkOffset(
                            i
                        )
                    })
                }
            }
        },
        getAfterBrothers = { node, connectExpression ->
            val parentVal = node.parent
            if (parentVal != null) {
                val index = indexCache[node]
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            connectExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (connectExpression.checkOffset(offset)) {
                                val child = parentVal.getChild(i) ?: return@sequence
                                yield(child)
                            }
                            i++
                            offset++
                        }
                    }
                } else {
                    getChildrenCache(parentVal).dropWhile { it != node }
                        .drop(1)
                        .let {
                            if (connectExpression.maxOffset != null) {
                                it.take(connectExpression.maxOffset!! + 1)
                            } else {
                                it
                            }
                        }
                        .filterIndexed { i, _ ->
                            connectExpression.checkOffset(
                                i
                            )
                        }
                }
            } else {
                emptySequence()
            }
        },
        getDescendantsX = { node, connectExpression ->
            sequence {
                val stack = getChildrenCache(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                var offset = 0
                do {
                    val top = stack.removeLast()
                    if (connectExpression.checkOffset(offset)) {
                        yield(top)
                    }
                    offset++
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    for (childNode in getChildrenCache(top)) {
                        tempNodes.add(childNode)
                    }
                    if (tempNodes.isNotEmpty()) {
                        for (i in tempNodes.size - 1 downTo 0) {
                            stack.add(tempNodes[i])
                        }
                        tempNodes.clear()
                    }
                } while (stack.isNotEmpty())
            }
        },
    )

    return CacheTransform(transform, indexCache)
}

fun createTransform(): Transform<AccessibilityNodeInfo> {
    return Transform(
        getAttr = createGetAttr(),
        getName = { node -> node.className },
        getChildren = getChildren,
        getParent = { node -> node.parent },
        getDescendants = { node ->
            sequence {
                val stack = getChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                var offset = 0
                do {
                    val top = stack.removeLast()
                    yield(top)
                    offset++
                    if (offset > MAX_DESCENDANTS_SIZE) {
                        return@sequence
                    }
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
            }
        },
        getChildrenX = { node, connectExpression ->
            sequence {
                repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (connectExpression.checkOffset(offset)) {
                        val child = node.getChild(offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
    )
}
