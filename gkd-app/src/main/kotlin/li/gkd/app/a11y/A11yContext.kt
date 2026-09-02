package li.gkd.app.a11y

import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import li.gkd.app.META
import li.gkd.app.data.ResolvedRule
import li.gkd.app.priv.toHidden
import li.gkd.app.util.InterruptRuleMatchException
import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector
import li.gkd.selector.NodeAdapter
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.relation.RelationExpression


private operator fun <K, V> LruCache<K, V>.set(child: K, value: V): V {
    return put(child, value)
}

private fun List<Any>.getInt(i: Int = 0) = get(i) as Int

private const val MAX_CACHE_SIZE = MAX_DESCENDANTS_SIZE

private val AccessibilityNodeInfo?.notExpiredNode: AccessibilityNodeInfo?
    get() {
        if (this != null) {
            val expiryMillis = if (text == null) 2000L else 1000L
            if (isExpired(expiryMillis)) {
                return null
            }
        }
        return this
    }

class A11yContext(
    private val a11yEngine: A11yRuleEngine,
    private val interruptable: Boolean = true,
) {
    private var childCache =
        LruCache<Pair<AccessibilityNodeInfo, Int>, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    private var indexCache = LruCache<AccessibilityNodeInfo, Int>(MAX_CACHE_SIZE)
    private var parentCache = LruCache<AccessibilityNodeInfo, AccessibilityNodeInfo>(MAX_CACHE_SIZE)
    val rootCache = atomic<AccessibilityNodeInfo?>(null)

    private fun clearChildCache(
        node: AccessibilityNodeInfo,
        visitedNodes: MutableSet<AccessibilityNodeInfo> = mutableSetOf(),
    ) {
        if (!visitedNodes.add(node)) return
        repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { i ->
            childCache.remove(node to i)?.let {
                clearChildCache(it, visitedNodes)
            }
        }
    }

    fun clearNodeCache(eventNode: AccessibilityNodeInfo? = null) {
        if (rootCache.value?.packageName != topActivityFlow.value.appId) {
            rootCache.value = null
        }
        if (eventNode != null) {
            clearChildCache(eventNode)
            parentCache[eventNode]?.let { p ->
                getPureIndex(eventNode)?.let { i ->
                    childCache[p to i] = eventNode
                }
            }
            if (rootCache.value == eventNode) {
                rootCache.value = eventNode
            } else {
                if (META.debuggable) {
                    Log.d(
                        "cache",
                        "clear node cache ${eventNode.packageName}/${eventNode.className}"
                    )
                }
                return
            }
        }
        if (META.debuggable) {
            val sizeList = listOf(childCache.size(), parentCache.size(), indexCache.size())
            if (sizeList.any { it > 0 }) {
                Log.d("cache", "clear cache -> $sizeList")
            }
        }
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

    private var lastAppChangeTime = appChangeTime
    fun clearOldAppNodeCache(): Boolean {
        if (appChangeTime != lastAppChangeTime) {
            lastAppChangeTime = appChangeTime
            clearNodeCache()
            return true
        }
        return false
    }

    var currentRule: ResolvedRule? = null

    @Volatile
    var interruptKey = 0
    private var interruptInnerKey = 0

    private fun guardInterrupt() {
        if (!interruptable) return
        if (interruptInnerKey == interruptKey) return
        interruptInnerKey = interruptKey
        val rule = currentRule ?: return
        if (!activityRuleFlow.value.activePriority) return
        if (!activityRuleFlow.value.currentRules.any { it === rule }) return
        if (rule.isPriority()) return
        if (META.debuggable) {
            Log.d("guardInterrupt", "中断 rule=${rule.statusText()}")
        }
        throw InterruptRuleMatchException()
    }

    private fun getA11Root(): AccessibilityNodeInfo? {
        guardInterrupt()
        return a11yEngine.safeActiveWindow
    }

    private fun getA11Child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.getChild(index)?.setGeneratedTime()
    }

    private fun getA11Parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        guardInterrupt()
        return node.parent?.setGeneratedTime()
    }

    private fun getA11ByText(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return node.findAccessibilityNodeInfosByText(value).apply {
            forEach { it.setGeneratedTime() }
        }
    }

    private fun getA11ById(
        node: AccessibilityNodeInfo,
        value: String
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return node.findAccessibilityNodeInfosByViewId(value).apply {
            forEach { it.setGeneratedTime() }
        }
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
        if (rootCache.value.notExpiredNode == null) {
            rootCache.value = getA11Root()
        }
        if (node == rootCache.value) return null
        return rootCache.value
    }

    private fun getCacheParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (getCacheRoot() == node) {
            return null
        }
        parentCache[node].notExpiredNode?.let { return it }
        return getA11Parent(node).apply {
            if (this != null) {
                parentCache[node] = this
            } else {
                rootCache.value = node
            }
        }
    }

    private fun getCacheChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        return childCache[node to index].notExpiredNode ?: getA11Child(node, index)?.also { child ->
            indexCache[child] = index
            parentCache[child] = node
            childCache[node to index] = child
        }
    }

    private fun getPureIndex(node: AccessibilityNodeInfo): Int? {
        return indexCache[node]
    }

    private fun getCacheIndex(node: AccessibilityNodeInfo): Int {
        indexCache[node]?.let { return it }
        getCacheChildren(getCacheParent(node)).forEachIndexed { index, child ->
            if (child == node) {
                indexCache[node] = index
                return index
            }
        }
        return 0
    }

    /** Broken accessibility parent chains may contain cycles; see issue #28. */
    private fun getCacheDepth(node: AccessibilityNodeInfo): Int? {
        val visitedNodes = mutableSetOf<AccessibilityNodeInfo>()
        var p: AccessibilityNodeInfo = node
        var depth = 0
        while (visitedNodes.add(p)) {
            p = getCacheParent(p) ?: return depth
            depth++
        }
        return null
    }

    private fun getCacheChildren(node: AccessibilityNodeInfo?): Sequence<AccessibilityNodeInfo> {
        if (node == null) return emptySequence()
        return sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { index ->
                val child = getCacheChild(node, index) ?: return@sequence
                yield(child)
            }
        }
    }

    private var tempVid: CharSequence? = null
    private var tempVidNode: AccessibilityNodeInfo? = null
    private fun getTempVid(n: AccessibilityNodeInfo): CharSequence? {
        if (n !== tempVidNode) {
            tempVid = n.getVid()
            tempVidNode = n
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
        "checked" -> node.compatChecked

        "editable" -> node.isEditable
        "longClickable" -> node.isLongClickable
        "visibleToUser" -> node.isVisibleToUser

        "left" -> node.toHidden.boundsInScreen.left
        "top" -> node.toHidden.boundsInScreen.top
        "right" -> node.toHidden.boundsInScreen.right
        "bottom" -> node.toHidden.boundsInScreen.bottom

        "width" -> node.toHidden.boundsInScreen.width()
        "height" -> node.toHidden.boundsInScreen.height()

        "index" -> getCacheIndex(node)
        "depth" -> getCacheDepth(node)
        "childCount" -> node.childCount

        "parent" -> getCacheParent(node)

        else -> null
    }

    private inner class A11yNodeAdapter : NodeAdapter<AccessibilityNodeInfo>() {
        override fun getAttr(target: Any, name: String): Any? = when (target) {
            is AccessibilityNodeInfo -> getCacheAttr(target, name)
            else -> null
        }

        override fun getInvoke(target: Any, name: String, args: List<Any>): Any? = when (target) {
            is AccessibilityNodeInfo -> when (name) {
                "getChild" -> getCacheChild(target, args.getInt())
                else -> null
            }
            else -> null
        }

        override fun getName(node: AccessibilityNodeInfo): String? = node.className?.toString()

        override fun getChildCount(node: AccessibilityNodeInfo): Int =
            node.childCount.coerceAtMost(MAX_CHILD_SIZE)

        override fun getChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? =
            getCacheChild(node, index)

        override fun getParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
            getCacheParent(node)

        override fun getNodeKey(node: AccessibilityNodeInfo): Any = node

        override fun getRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo? = getCacheRoot()

        override fun getDescendants(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> =
            sequence {
                val visitedNodes = mutableSetOf(node)
                val stack = getCacheChildren(node).toMutableList()
                if (stack.isEmpty()) return@sequence
                stack.reverse()
                val tempNodes = mutableListOf<AccessibilityNodeInfo>()
                do {
                    val top = stack.removeAt(stack.lastIndex)
                    if (!visitedNodes.add(top)) continue
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

        override fun traverseChildren(
            node: AccessibilityNodeInfo,
            relationExpression: RelationExpression,
        ): Sequence<TraversalCandidate<AccessibilityNodeInfo>> = sequence {
            repeat(node.childCount.coerceAtMost(MAX_CHILD_SIZE)) { offset ->
                relationExpression.maxOffset?.let { maxOffset ->
                    if (offset > maxOffset) return@sequence
                }
                if (relationExpression.checkOffset(offset)) {
                    getCacheChild(node, offset)?.let { child ->
                        yield(TraversalCandidate(child, offset))
                    }
                }
            }
        }

        override fun traversePreviousSiblings(
            node: AccessibilityNodeInfo,
            relationExpression: RelationExpression,
        ): Sequence<TraversalCandidate<AccessibilityNodeInfo>> = sequence {
            val parentVal = getCacheParent(node) ?: return@sequence
            // 如果 node 由 fastQuery 得到, 则第一次调用此方法可能得到 cache.index 是空
            val index = getPureIndex(node)
            if (index != null) {
                var i = index - 1
                var offset = 0
                while (0 <= i && i < parentVal.childCount) {
                    relationExpression.maxOffset?.let { maxOffset ->
                        if (offset > maxOffset) return@sequence
                    }
                    if (relationExpression.checkOffset(offset)) {
                        getCacheChild(parentVal, i)?.let { child ->
                            yield(TraversalCandidate(child, offset))
                        }
                    }
                    i--
                    offset++
                }
            } else {
                val list = getCacheChildren(parentVal).takeWhile { it != node }.toMutableList()
                list.reverse()
                for ((offset, sibling) in list.withIndex()) {
                    if (relationExpression.maxOffset?.let { offset > it } == true) break
                    if (relationExpression.checkOffset(offset)) {
                        yield(TraversalCandidate(sibling, offset))
                    }
                }
            }
        }

        override fun traverseFollowingSiblings(
            node: AccessibilityNodeInfo,
            relationExpression: RelationExpression,
        ): Sequence<TraversalCandidate<AccessibilityNodeInfo>> {
            val parentVal = getCacheParent(node)
            return if (parentVal != null) {
                val index = getPureIndex(node)
                if (index != null) {
                    sequence {
                        var i = index + 1
                        var offset = 0
                        while (0 <= i && i < parentVal.childCount) {
                            relationExpression.maxOffset?.let { maxOffset ->
                                if (offset > maxOffset) return@sequence
                            }
                            if (relationExpression.checkOffset(offset)) {
                                getCacheChild(parentVal, i)?.let { child ->
                                    yield(TraversalCandidate(child, offset))
                                }
                            }
                            i++
                            offset++
                        }
                    }
                } else {
                    sequence {
                        getCacheChildren(parentVal)
                            .dropWhile { it != node }
                            .drop(1)
                            .forEachIndexed { offset, sibling ->
                                if (relationExpression.maxOffset?.let { offset > it } == true) {
                                    return@sequence
                                }
                                if (relationExpression.checkOffset(offset)) {
                                    yield(TraversalCandidate(sibling, offset))
                                }
                            }
                    }
                }
            } else {
                emptySequence()
            }
        }

        override fun traverseDescendants(
            node: AccessibilityNodeInfo,
            relationExpression: RelationExpression,
        ): Sequence<TraversalCandidate<AccessibilityNodeInfo>> = sequence {
            val visitedNodes = mutableSetOf(node)
            val stack = getCacheChildren(node).toMutableList()
            if (stack.isEmpty()) return@sequence
            stack.reverse()
            val tempNodes = mutableListOf<AccessibilityNodeInfo>()
            var offset = 0
            do {
                val top = stack.removeAt(stack.lastIndex)
                if (!visitedNodes.add(top)) continue
                if (relationExpression.checkOffset(offset)) {
                    yield(TraversalCandidate(top, offset))
                }
                offset++
                if (offset > MAX_DESCENDANTS_SIZE) {
                    return@sequence
                }
                relationExpression.maxOffset?.let { maxOffset ->
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

        override fun getFastQueryDescendants(
            node: AccessibilityNodeInfo,
            fastQueryList: List<FastQuery>,
        ): Sequence<AccessibilityNodeInfo> = sequence {
            val yieldedKeys = mutableSetOf(getNodeKey(node))
            for (fastQuery in fastQueryList) {
                for (childNode in getFastQueryNodes(node, fastQuery)) {
                    if (yieldedKeys.add(getNodeKey(childNode))) {
                        yield(childNode)
                    }
                }
            }
        }
    }

    private val adapter = A11yNodeAdapter()

    fun querySelfOrSelector(
        node: AccessibilityNodeInfo,
        selector: Selector,
        options: MatchOptions,
    ): AccessibilityNodeInfo? {
        if (selector.isMatchRoot) {
            return selector.match(
                getCacheRoot() ?: return null,
                adapter,
                options
            )
        }
        selector.match(node, adapter, options)?.let {
            return it
        }
        return adapter.querySelector(node, selector, options)
    }

    fun queryRule(
        rule: ResolvedRule,
        node: AccessibilityNodeInfo,
    ): AccessibilityNodeInfo? {
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
                    resultNode = querySelfOrSelector(
                        queryNode,
                        selector,
                        rule.matchOptions,
                    )
                    if (resultNode != null) break
                }
                if (resultNode == null) return null
            }
            for (selector in rule.matches) {
                resultNode = querySelfOrSelector(
                    queryNode,
                    selector,
                    rule.matchOptions,
                ) ?: return null
            }
            for (selector in rule.excludeMatches) {
                querySelfOrSelector(
                    queryNode,
                    selector,
                    rule.matchOptions,
                )?.let { return null }
            }
            if (rule.excludeAllMatches.isNotEmpty()) {
                val allExclude = rule.excludeAllMatches.all {
                    querySelfOrSelector(
                        queryNode,
                        it,
                        rule.matchOptions,
                    ) == null
                }
                if (!allExclude) {
                    return null
                }
            }
            return resultNode
        } finally {
            currentRule = null
        }
    }
}
