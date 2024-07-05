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
import li.songe.selector.getBooleanInvoke
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

inline fun AccessibilityNodeInfo.forEachIndexed(action: (index: Int, childNode: AccessibilityNodeInfo?) -> Unit) {
    var index = 0
    val childCount = this.childCount
    while (index < childCount) {
        val child: AccessibilityNodeInfo? = getChild(index)
        action(index, child)
        index += 1
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
    quickFind: Boolean,
    transform: Transform<AccessibilityNodeInfo>,
    isRootNode: Boolean,
): AccessibilityNodeInfo? {
    if (selector.isMatchRoot) {
        val root = if (isRootNode) {
            return this
        } else {
            GkdAbService.service?.safeActiveWindow ?: return null
        }
        return selector.match(root, transform)
    }
    if (quickFind && selector.quickFindValue.canQf) {
        val idValue = selector.quickFindValue.id
        val vidValue = selector.quickFindValue.vid
        val textValue = selector.quickFindValue.text
        val nodes = (if (idValue != null) {
            findAccessibilityNodeInfosByViewId(idValue)
        } else if (vidValue != null) {
            findAccessibilityNodeInfosByViewId("$packageName:id/$vidValue")
        } else if (textValue != null) {
            findAccessibilityNodeInfosByText(textValue)
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

    return { node, name ->
        when (name) {
            "id" -> node.viewIdResourceName
            "vid" -> node.getTempVid()

            "name" -> node.className
            "text" -> node.text
            "desc" -> node.contentDescription

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

            "index" -> cache.getIndex(node)
            "depth" -> cache.getDepth(node)
            "childCount" -> node.childCount

            "parent" -> cache.getParent(node)

            else -> null
        }
    }
}

data class CacheTransform(
    val transform: Transform<AccessibilityNodeInfo>,
    val cache: NodeCache,
)

data class NodeCache(
    val childMap: MutableMap<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo> = HashMap(),
    val indexMap: MutableMap<AccessibilityNodeInfo, Int> = HashMap(),
    val parentMap: MutableMap<AccessibilityNodeInfo, AccessibilityNodeInfo?> = HashMap(),
) {
    fun clear() {
        childMap.clear()
        parentMap.clear()
        indexMap.clear()
    }

    fun getIndex(node: AccessibilityNodeInfo): Int {
        indexMap[node]?.let { return it }
        getParent(node)?.forEachIndexed { index, child ->
            if (child != null) {
                indexMap[child] = index
            }
            if (child == node) {
                return index
            }
        }
        return 0
    }

    fun getParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val parent = parentMap[node]
        if (parent != null) {
            return parent
        } else if (parentMap.contains(node)) {
            return null
        }
        return node.parent.apply {
            parentMap[node] = this
        }
    }

    /**
     * 在无缓存时, 此方法小概率造成无限节点片段,底层原因未知
     *
     * https://github.com/gkd-kit/gkd/issues/28
     */
    fun getDepth(node: AccessibilityNodeInfo): Int {
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (true) {
            val p2 = getParent(p)
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
    }

    fun getChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        return childMap[node to index] ?: node.getChild(index)?.also { child ->
            indexMap[child] = index
            parentMap[child] = node
            childMap[node to index] = child
        }
    }
}

fun createCacheTransform(): CacheTransform {
    val cache = NodeCache()

    val getChildrenCache: (AccessibilityNodeInfo) -> Sequence<AccessibilityNodeInfo> = { node ->
        sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = cache.getChild(node, index) ?: return@sequence
                yield(child)
            }
        }
    }
    val getNodeAttr = createGetNodeAttr(cache)
    val transform = Transform(
        getAttr = { node, name ->
            when (node) {
                is AccessibilityNodeInfo -> getNodeAttr(node, name)
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
                            cache.getChild(target, index)
                        }
                    }

                    else -> null
                }

                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is Boolean -> getBooleanInvoke(target, name, args)

                else -> null
            }

        },
        getName = { node -> node.className },
        getChildren = getChildrenCache,
        getParent = { cache.getParent(it) },
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
                        val child = cache.getChild(node, offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
        getBeforeBrothers = { node, connectExpression ->
            sequence {
                val parentVal = cache.getParent(node) ?: return@sequence
                // 如果 node 由 quickFind 得到, 则第一次调用此方法可能得到 cache.index 是空
                val index = cache.indexMap[node]
                if (index != null) {
                    var i = index - 1
                    var offset = 0
                    while (0 <= i && i < parentVal.childCount) {
                        connectExpression.maxOffset?.let { maxOffset ->
                            if (offset > maxOffset) return@sequence
                        }
                        if (connectExpression.checkOffset(offset)) {
                            val child = cache.getChild(parentVal, i) ?: return@sequence
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
            val parentVal = cache.getParent(node)
            if (parentVal != null) {
                val index = cache.indexMap[node]
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            connectExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (connectExpression.checkOffset(offset)) {
                                val child = cache.getChild(parentVal, i) ?: return@sequence
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
                    if (offset >= MAX_DESCENDANTS_SIZE) {
                        return@sequence
                    }
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

private fun List<Any>.getIntOrNull(i: Int = 0): Int? {
    return getOrNull(i) as? Int ?: return null
}

fun createNoCacheTransform(): CacheTransform {
    val cache = NodeCache()
    val getNodeAttr = createGetNodeAttr(cache)
    val transform = Transform(
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
                        args.getIntOrNull()?.let { index ->
                            cache.getChild(target, index)
                        }
                    }

                    else -> null
                }

                is CharSequence -> getCharSequenceInvoke(target, name, args)
                is Int -> getIntInvoke(target, name, args)
                is Boolean -> getBooleanInvoke(target, name, args)
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
    return CacheTransform(transform, cache)
}
