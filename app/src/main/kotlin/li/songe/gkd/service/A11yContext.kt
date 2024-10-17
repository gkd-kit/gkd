package li.songe.gkd.service

import android.graphics.Rect
import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import li.songe.gkd.META
import li.songe.gkd.data.ResolvedRule
import li.songe.gkd.util.InterruptRuleMatchException
import li.songe.selector.Context
import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.Selector
import li.songe.selector.Transform
import li.songe.selector.getBooleanInvoke
import li.songe.selector.getCharSequenceAttr
import li.songe.selector.getCharSequenceInvoke
import li.songe.selector.getIntInvoke


private operator fun <K, V> LruCache<K, V>.set(child: K, value: V): V {
    return put(child, value)
}

private fun List<Any>.getInt(i: Int = 0) = get(i) as Int

private const val MAX_CACHE_SIZE = MAX_DESCENDANTS_SIZE

class A11yContext(
    private val disableInterrupt: Boolean = false
) {
    private var childCache =
        LruCache<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    private var indexCache = LruCache<AccessibilityNodeInfo, Int>(MAX_CACHE_SIZE)
    private var parentCache = LruCache<AccessibilityNodeInfo, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    var rootCache: AccessibilityNodeInfo? = null

    private fun clearNodeCache() {
        if (META.debuggable) {
            val sizeList = listOf(childCache.size(), parentCache.size(), indexCache.size())
            if (sizeList.any { it > 0 }) {
                Log.d("cache", "clear cache -> $sizeList")
            }
        }
        rootCache = null
        try {
            childCache.evictAll()
            parentCache.evictAll()
            indexCache.evictAll()
        } catch (_: Exception) {
            // https://github.com/gkd-kit/gkd/issues/664
            // 在某些机型上 未知原因 缓存不一致 导致删除失败
            childCache = LruCache(MAX_CACHE_SIZE)
            indexCache = LruCache(MAX_CACHE_SIZE)
            parentCache = LruCache(MAX_CACHE_SIZE)
        }
    }

    private var lastClearTime = 0L
    private var lastAppChangeTime = appChangeTime
    private fun clearNodeCacheIfTimeout() {
        if (appChangeTime != lastAppChangeTime) {
            lastAppChangeTime = appChangeTime
            lastClearTime = System.currentTimeMillis()
            clearNodeCache()
            return
        }
        val t = System.currentTimeMillis()
        if (t - lastClearTime > 30_000L) {
            lastClearTime = t
            clearNodeCache()
        }
    }

    var currentRule: ResolvedRule? = null

    @Volatile
    var interruptKey = 0
    private var interruptInnerKey = 0

    private fun guardInterrupt() {
        if (disableInterrupt) return
        if (interruptInnerKey == interruptKey) return
        interruptInnerKey = interruptKey
        val rule = currentRule ?: return
        if (!activityRuleFlow.value.currentRules.contains(rule)) return
        if (rule.isPriority()) return
        if (META.debuggable) {
            Log.d("guardInterrupt", "中断 rule=${rule.statusText()}")
        }
        throw InterruptRuleMatchException()
    }

    private fun getA11Root(): AccessibilityNodeInfo? {
        guardInterrupt()
        return A11yService.instance?.safeActiveWindow
    }

    private fun getA11Child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.getChild(index)
    }

    private fun getA11Parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.parent
    }

    private fun getA11ByText(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return node.findAccessibilityNodeInfosByText(value)
    }

    private fun getA11ById(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return node.findAccessibilityNodeInfosByViewId(value)
    }

    private fun getFastQueryNodes(
        node: AccessibilityNodeInfo,
        fastQuery: FastQuery
    ): List<AccessibilityNodeInfo> {
        return when (fastQuery) {
            is FastQuery.Id -> getA11ById(node, fastQuery.value)
            is FastQuery.Text -> getA11ByText(node, fastQuery.value)
            is FastQuery.Vid -> getA11ById(node, "${node.packageName}:id/${fastQuery.value}")
        }
    }

    private fun getCacheRoot(node: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
        if (rootCache == null) {
            rootCache = getA11Root()
        }
        if (node == rootCache) return null
        return rootCache
    }

    private fun getPureIndex(node: AccessibilityNodeInfo): Int? {
        return indexCache[node]
    }

    private fun getCacheIndex(node: AccessibilityNodeInfo): Int {
        indexCache[node]?.let { return it }
        getCacheParent(node)?.let(::getCacheChildren)?.forEachIndexed { index, child ->
            if (child == node) {
                indexCache[node] = index
                return index
            }
        }
        return 0
    }

    private fun getCacheParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (rootCache == node) {
            return null
        }
        parentCache[node]?.let { return it }
        return getA11Parent(node).apply {
            if (this != null) {
                parentCache[node] = this
            } else {
                rootCache = node
            }
        }
    }

    /**
     * 在无缓存时, 此方法小概率造成无限节点片段,底层原因未知
     *
     * https://github.com/gkd-kit/gkd/issues/28
     */
    private fun getCacheDepth(node: AccessibilityNodeInfo): Int {
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (true) {
            val p2 = getCacheParent(p)
            if (p2 != null) {
                p = p2
                depth++
            } else {
                break
            }
        }
        return depth
    }

    private fun getCacheChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        return childCache[node to index] ?: getA11Child(node, index)?.also { child ->
            indexCache[child] = index
            parentCache[child] = node
            childCache[node to index] = child
        }
    }

    private fun getCacheChildren(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> {
        return sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = getCacheChild(node, index) ?: return@sequence
                yield(child)
            }
        }
    }

    private var tempNode: AccessibilityNodeInfo? = null
    private val tempRect = Rect()
    private var tempVid: CharSequence? = null
    private fun getTempRect(n: AccessibilityNodeInfo): Rect {
        if (n !== tempNode) {
            n.getBoundsInScreen(tempRect)
            tempNode = n
        }
        return tempRect
    }

    private fun getTempVid(n: AccessibilityNodeInfo): CharSequence? {
        if (n !== tempNode) {
            tempVid = n.getVid()
            tempNode = n
        }
        return tempVid
    }

    private fun getCacheAttr(node: AccessibilityNodeInfo, name: String): Any? = when (name) {
        "id" -> node.viewIdResourceName
        "vid" -> getTempVid(node)

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

        "left" -> getTempRect(node).left
        "top" -> getTempRect(node).top
        "right" -> getTempRect(node).right
        "bottom" -> getTempRect(node).bottom

        "width" -> getTempRect(node).width()
        "height" -> getTempRect(node).height()

        "index" -> getCacheIndex(node)
        "depth" -> getCacheDepth(node)
        "childCount" -> node.childCount

        "parent" -> getCacheParent(node)

        else -> null
    }

    private val transform = Transform(
        getAttr = { target, name ->
            when (target) {
                is Context<*> -> when (name) {
                    "prev" -> target.prev
                    "current" -> target.current
                    else -> getCacheAttr(target.current as AccessibilityNodeInfo, name)
                }

                is AccessibilityNodeInfo -> getCacheAttr(target, name)
                is CharSequence -> getCharSequenceAttr(target, name)
                else -> null
            }
        },
        getInvoke = { target, name, args ->
            when (target) {
                is AccessibilityNodeInfo -> when (name) {
                    "getChild" -> {
                        getCacheChild(target, args.getInt())
                    }

                    else -> null
                }

                is Context<*> -> when (name) {
                    "getPrev" -> {
                        args.getInt().let { target.getPrev(it) }
                    }

                    "getChild" -> {
                        getCacheChild(target.current as AccessibilityNodeInfo, args.getInt())
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
        getChildren = ::getCacheChildren,
        getParent = ::getCacheParent,
        getRoot = ::getCacheRoot,
        getDescendants = { node ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    yield(top)
                    for (childNode in getCacheChildren(top)) {
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
        traverseChildren = { node, connectExpression ->
            sequence {
                repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (connectExpression.checkOffset(offset)) {
                        val child = getCacheChild(node, offset) ?: return@sequence
                        yield(child)
                    }
                }
            }
        },
        traverseBeforeBrothers = { node, connectExpression ->
            sequence {
                val parentVal = getCacheParent(node) ?: return@sequence
                // 如果 node 由 quickFind 得到, 则第一次调用此方法可能得到 cache.index 是空
                val index = getPureIndex(node)
                if (index != null) {
                    var i = index - 1
                    var offset = 0
                    while (0 <= i && i < parentVal.childCount) {
                        connectExpression.maxOffset?.let { maxOffset ->
                            if (offset > maxOffset) return@sequence
                        }
                        if (connectExpression.checkOffset(offset)) {
                            val child = getCacheChild(parentVal, i) ?: return@sequence
                            yield(child)
                        }
                        i--
                        offset++
                    }
                } else {
                    val list = getCacheChildren(parentVal).takeWhile { it != node }.toMutableList()
                    list.reverse()
                    yieldAll(list.filterIndexed { i, _ ->
                        connectExpression.checkOffset(
                            i
                        )
                    })
                }
            }
        },
        traverseAfterBrothers = { node, connectExpression ->
            val parentVal = getCacheParent(node)
            if (parentVal != null) {
                val index = getPureIndex(node)
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            connectExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (connectExpression.checkOffset(offset)) {
                                val child = getCacheChild(parentVal, i) ?: return@sequence
                                yield(child)
                            }
                            i++
                            offset++
                        }
                    }
                } else {
                    getCacheChildren(parentVal).dropWhile { it != node }
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
        traverseDescendants = { node, connectExpression ->
            sequence {
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                var offset = 0
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    if (connectExpression.checkOffset(offset)) {
                        yield(top)
                    }
                    offset++
                    if (offset > MAX_DESCENDANTS_SIZE) {
                        return@sequence
                    }
                    connectExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    for (childNode in getCacheChildren(top)) {
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
        traverseFastQueryDescendants = { node, fastQueryList ->
            sequence {
                for (fastQuery in fastQueryList) {
                    val nodes = getFastQueryNodes(node, fastQuery)
                    nodes.forEach { childNode ->
                        yield(childNode)
                    }
                }
            }
        }
    )

    fun querySelector(
        node: AccessibilityNodeInfo,
        selector: Selector,
        option: MatchOption,
    ): AccessibilityNodeInfo? {
        if (selector.isMatchRoot) {
            return selector.match(
                getCacheRoot() ?: return null,
                transform,
                option
            )
        }
        if (option.fastQuery && selector.fastQueryList.isNotEmpty()) {
            val nodes = transform.traverseFastQueryDescendants(node, selector.fastQueryList)
            nodes.forEach { childNode ->
                selector.match(childNode, transform, option)?.let { return it }
            }
            return null
        }
        if (option.quickFind && selector.quickFindValue != null) {
            val nodes = getFastQueryNodes(node, selector.quickFindValue!!)
            nodes.forEach { childNode ->
                selector.match(childNode, transform, option)?.let { return it }
            }
            return null
        }
        return transform.querySelector(node, selector, option)
    }

    fun queryRule(
        rule: ResolvedRule,
        node: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
        clearNodeCacheIfTimeout()
        currentRule = rule
        try {
            val queryNode = if (rule.matchRoot) {
                getCacheRoot()
            } else {
                node
            } ?: return null
            var resultNode: AccessibilityNodeInfo? = null
            if (rule.anyMatches.isNotEmpty()) {
                for (selector in rule.anyMatches) {
                    resultNode = a11yContext.querySelector(
                        queryNode,
                        selector,
                        rule.matchOption,
                    ) ?: break
                }
                if (resultNode == null) return null
            }
            for (selector in rule.matches) {
                resultNode = a11yContext.querySelector(
                    queryNode,
                    selector,
                    rule.matchOption,
                ) ?: return null
            }
            for (selector in rule.excludeMatches) {
                a11yContext.querySelector(
                    queryNode,
                    selector,
                    rule.matchOption,
                )?.let { return null }
            }
            return resultNode
        } finally {
            currentRule = null
        }
    }
}

val a11yContext = A11yContext()
