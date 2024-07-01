package li.songe.gkd.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import li.songe.gkd.BuildConfig
import li.songe.selector.MismatchExpressionTypeException
import li.songe.selector.MismatchOperatorTypeException
import li.songe.selector.MismatchParamTypeException
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.UnknownIdentifierException
import li.songe.selector.UnknownIdentifierMethodException
import li.songe.selector.UnknownMemberException
import li.songe.selector.UnknownMemberMethodException
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke
import li.songe.selector.initDefaultTypeInfo

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

fun AccessibilityNodeInfo.getChildOrNull(i: Int?): AccessibilityNodeInfo? {
    i ?: return null
    return if (i in 0 until childCount) {
        getChild(i)
    } else {
        null
    }
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

private val typeInfo by lazy {
    initDefaultTypeInfo().apply {
        nodeType.props = nodeType.props.filter { !it.name.startsWith('_') }.toTypedArray()
        contextType.props = contextType.props.filter { !it.name.startsWith('_') }.toTypedArray()
    }.contextType
}

fun Selector.checkSelector(): String? {
    val error = checkType(typeInfo) ?: return null
    if (BuildConfig.DEBUG) {
        LogUtils.d(
            "Selector check error",
            source,
            error.message
        )
    }
    return when (error) {
        is MismatchExpressionTypeException -> "不匹配表达式类型:${error.exception.stringify()}"
        is MismatchOperatorTypeException -> "不匹配操作符类型:${error.exception.stringify()}"
        is MismatchParamTypeException -> "不匹配参数类型:${error.call.stringify()}"
        is UnknownIdentifierException -> "未知属性:${error.value.value}"
        is UnknownIdentifierMethodException -> "未知方法:${error.value.value}"
        is UnknownMemberException -> "未知属性:${error.value.property}"
        is UnknownMemberMethodException -> "未知方法:${error.value.property}"
    }
}

private fun createGetNodeAttr(cache: NodeCache): ((AccessibilityNodeInfo, String) -> Any?) {
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

    /**
     * 在无缓存时, 此方法小概率造成无限节点片段,底层原因未知
     *
     * https://github.com/gkd-kit/gkd/issues/28
     */
    fun AccessibilityNodeInfo.getDepthX(): Int {
        var p: AccessibilityNodeInfo = this
        var depth = 0
        while (true) {
            val p2 = cache.parent[p] ?: p.parent.apply {
                cache.parent[p] = this
            }
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
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
            "depth" -> node.getDepthX()
            "childCount" -> node.childCount

            "parent" -> cache.parent[node] ?: node.parent.apply {
                cache.parent[node] = this
            }

            else -> null
        }
    }
}

data class CacheTransform(
    val transform: Transform<AccessibilityNodeInfo>,
    val cache: NodeCache,
)

data class NodeCache(
    val child: MutableMap<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo> = HashMap(),
    val index: MutableMap<AccessibilityNodeInfo, Int> = HashMap(),
    val parent: MutableMap<AccessibilityNodeInfo, AccessibilityNodeInfo?> = HashMap(),
) {
    fun clear() {
        emptyMap<String, String>()
        child.clear()
        parent.clear()
        index.clear()
    }
}

fun createCacheTransform(): CacheTransform {
    val cache = NodeCache()
    fun AccessibilityNodeInfo.getParentX(): AccessibilityNodeInfo? {
        return parent?.also { parent ->
            cache.parent[this] = parent
        }
    }

    fun AccessibilityNodeInfo.getChildX(index: Int): AccessibilityNodeInfo? {
        return cache.child[this to index] ?: getChild(index)?.also { child ->
            cache.index[child] = index
            cache.parent[child] = this
            cache.child[this to index] = child
        }
    }

    fun AccessibilityNodeInfo.getIndexX(): Int {
        cache.index[this]?.let { return it }
        getParentX()?.forEachIndexed { index, child ->
            if (child != null) {
                cache.index[child] = index
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
    val getNodeAttr = createGetNodeAttr(cache)
    val getParent = { node: AccessibilityNodeInfo ->
        cache.parent[node] ?: node.parent.apply {
            cache.parent[node] = this
        }
    }
    val transform = Transform(
        getAttr = { node, name ->
            when (node) {
                is AccessibilityNodeInfo -> {
                    when (name) {
                        "index" -> {
                            node.getIndexX()
                        }

                        else -> {
                            getNodeAttr(node, name)
                        }
                    }
                }

                is CharSequence -> getCharSequenceAttr(node, name)

                else -> {
                    null
                }
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is AccessibilityNodeInfo -> when (name) {
                    "getChild" -> {
                        args.getIntOrNull()?.let { index ->
                            if (index in 0 until target.childCount) {
                                target.getChildX(index)
                            } else {
                                null
                            }
                        }
                    }

                    else -> null
                }

                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)

                else -> null
            }

        },
        getName = { node -> node.className },
        getChildren = getChildrenCache,
        getParent = getParent,
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
                val parentVal = getParent(node) ?: return@sequence
                // 如果 node 由 quickFind 得到, 则第一次调用此方法可能得到 cache.index 是空
                val index = cache.index[node]
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
            val parentVal = getParent(node)
            if (parentVal != null) {
                val index = cache.index[node]
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

    return CacheTransform(transform, cache)
}

private fun List<Any?>.getIntOrNull(i: Int = 0): Int? {
    return getOrNull(i) as? Int ?: return null
}

fun createTransform(): Transform<AccessibilityNodeInfo> {
    val cache = NodeCache()
    val getNodeAttr = createGetNodeAttr(cache)
    return Transform(
        getAttr = { target, name ->
            when (target) {
                is AccessibilityNodeInfo -> getNodeAttr(target, name)
                is CharSequence -> getCharSequenceAttr(target, name)
                else -> {
                    null
                }
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is AccessibilityNodeInfo -> when (name) {
                    "getChild" -> {
                        target.getChildOrNull(args.getIntOrNull())
                    }

                    else -> null
                }

                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                else -> null
            }
        },
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
