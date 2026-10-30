package li.gkd.app.matching.query

import android.util.Log
import android.util.LruCache
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import li.gkd.app.META
import li.gkd.app.matching.diagnostics.MatchDiagnostics
import li.gkd.app.matching.diagnostics.MatchFastQueryCandidate
import li.gkd.app.matching.diagnostics.MatchFastQueryKind
import li.gkd.app.matching.diagnostics.MatchFastQueryResult
import li.gkd.app.matching.diagnostics.MatchNodeMetrics
import li.gkd.app.matching.diagnostics.MatchNodeObservation
import li.gkd.app.matching.diagnostics.MatchNodeRole
import li.gkd.app.matching.diagnostics.MatchRecordedValue
import li.gkd.app.matching.diagnostics.MatchRecordedValueType
import li.gkd.app.matching.diagnostics.MatchStage
import li.gkd.app.matching.diagnostics.NodeCacheKind
import li.gkd.app.matching.diagnostics.NodeReadOperation
import li.gkd.app.matching.diagnostics.RuleSelectorDiagnostics
import li.gkd.app.matching.diagnostics.matchRuleSelectorsExplained
import li.gkd.app.matching.diagnostics.recordManualQuery
import li.gkd.app.matching.diagnostics.recordRuleQuery
import li.gkd.app.matching.input.appChangeTime
import li.gkd.app.matching.input.currentTopActivity
import li.gkd.app.matching.input.foregroundRulesFlow
import li.gkd.app.priv.toHidden
import li.gkd.app.rule.ResolvedRule
import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOptions
import li.gkd.selector.NodeAdapter
import li.gkd.selector.Selector
import li.gkd.selector.TraversalCandidate
import li.gkd.selector.relation.RelationExpression

private operator fun <K, V> LruCache<K, V>.set(child: K, value: V): V {
    return put(child, value)
}

private fun List<Any>.getInt(i: Int = 0) = get(i) as Int

private const val MAX_CACHE_SIZE = MAX_DESCENDANTS_SIZE
// Bound diagnostic samples without changing the candidates used for matching.
private const val MAX_ANOMALOUS_CANDIDATES = 3
private const val MAX_CANDIDATE_FIELD_LENGTH = 256

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

class A11yQueryContext(
    private val getRoot: () -> AccessibilityNodeInfo?,
    private val interruptable: Boolean = true,
    private val metrics: MatchNodeMetrics? = null,
    private val diagnostics: MatchDiagnostics? = null,
    private val nodeReader: MatchNodeReader? = null,
    private val onNodeUsed: ((AccessibilityNodeInfo, MatchNodeRole) -> Unit)? = null,
) {
    private class RuleMatchInterrupted : CancellationException()

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
        if (rootCache.value?.packageName != currentTopActivity.appId) {
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
                        "clear node cache ${eventNode.packageName}/${eventNode.className}",
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

    @Volatile var interruptKey = 0
    var queryJob: kotlinx.coroutines.Job? = null
    var queryRunId = 0L
    var queryPage: Any? = null
    private var interruptInnerKey = 0

    private fun guardInterrupt() {
        queryJob?.let {
            if (!it.isActive) throw kotlinx.coroutines.CancellationException("query_cancelled")
        }
        if (!interruptable) return
        if (interruptInnerKey == interruptKey) return
        interruptInnerKey = interruptKey
        val rule = currentRule ?: return
        if (!foregroundRulesFlow.value.activePriority) return
        if (!foregroundRulesFlow.value.currentRules.any { it.sameExecution(rule) }) return
        if (rule.isPriority()) return
        if (META.debuggable) {
            Log.d("guardInterrupt", "中断 rule=${rule.statusText()}")
        }
        throw RuleMatchInterrupted()
    }

    private fun getA11Root(): AccessibilityNodeInfo? {
        guardInterrupt()
        // Benchmark callers already time their root supplier.
        return if (nodeReader == null) getRoot()
        else measure(NodeReadOperation.Root, block = getRoot)
    }

    private fun getA11Child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        guardInterrupt()
        return measure(NodeReadOperation.Child, { listOf(node, index) }) { node.getChild(index) }
            ?.setGeneratedTime()
    }

    private fun getA11Parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        guardInterrupt()
        return measure(NodeReadOperation.Parent, { listOf(node) }) { node.parent }
            ?.setGeneratedTime()
    }

    private fun getA11ByText(
        node: AccessibilityNodeInfo,
        value: String,
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return measure(NodeReadOperation.FindText, { listOf(node, value) }) {
                node.findAccessibilityNodeInfosByText(value)
            }
            .apply {
                forEach { it.setGeneratedTime() }
            }
    }

    private fun getA11ById(
        node: AccessibilityNodeInfo,
        value: String,
    ): List<AccessibilityNodeInfo> {
        guardInterrupt()
        return measure(NodeReadOperation.FindId, { listOf(node, value) }) {
                node.findAccessibilityNodeInfosByViewId(value)
            }
            .apply {
                forEach { it.setGeneratedTime() }
            }
    }

    private fun getFastQueryNodes(
        node: AccessibilityNodeInfo,
        fastQuery: FastQuery,
    ): List<AccessibilityNodeInfo> {
        val nodes =
            when (fastQuery) {
                is FastQuery.Id -> getA11ById(node, fastQuery.value)
                is FastQuery.Text -> getA11ByText(node, fastQuery.value)
                is FastQuery.Vid -> getA11ById(node, "${node.packageName}:id/${fastQuery.value}")
            }
        if (diagnostics?.enabled == true) {
            diagnostics.record(
                queryRunId,
                MatchStage.Selector,
                "fast_query_result",
                currentRule?.matchIdentity(),
                fastQuery =
                    MatchFastQueryResult(
                        kind =
                            when (fastQuery) {
                                is FastQuery.Text -> MatchFastQueryKind.Text
                                is FastQuery.Id -> MatchFastQueryKind.Id
                                is FastQuery.Vid -> MatchFastQueryKind.Vid
                            },
                        value = fastQuery.value,
                        count = nodes.size,
                        anomalousCandidates =
                            if (fastQuery is FastQuery.Text)
                                nodes
                                    .asSequence()
                                    .filter { it.text == null }
                                    .take(MAX_ANOMALOUS_CANDIDATES)
                                    .map {
                                        MatchFastQueryCandidate(
                                            className =
                                                it.className
                                                    ?.toString()
                                                    ?.take(MAX_CANDIDATE_FIELD_LENGTH),
                                            viewId =
                                                it.viewIdResourceName?.take(
                                                    MAX_CANDIDATE_FIELD_LENGTH,
                                                ),
                                            contentDescription =
                                                it.contentDescription
                                                    ?.toString()
                                                    ?.take(MAX_CANDIDATE_FIELD_LENGTH),
                                            windowId = it.windowId,
                                            visibleToUser = it.isVisibleToUser,
                                        )
                                    }
                                    .toList()
                            else emptyList(),
                    ),
            )
        }
        return nodes
    }

    private fun getCacheRoot(node: AccessibilityNodeInfo? = null): AccessibilityNodeInfo? {
        val cached = rootCache.value.notExpiredNode
        metrics?.cache(NodeCacheKind.Root, cached != null)
        if (cached == null) {
            rootCache.value = getA11Root()
        }
        if (node == rootCache.value) return null
        return rootCache.value
    }

    private fun getCacheParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (getCacheRoot() == node) {
            return null
        }
        val cached = parentCache[node].notExpiredNode
        metrics?.cache(NodeCacheKind.Parent, cached != null)
        cached?.let {
            return it
        }
        return getA11Parent(node).apply {
            if (this != null) {
                parentCache[node] = this
            } else {
                rootCache.value = node
            }
        }
    }

    private fun requireQueryRoot(): AccessibilityNodeInfo =
        (getCacheRoot() ?: throw MatchQueryIncomplete(MatchQueryIncompleteReason.RootUnavailable))
            .also {
                if (diagnostics?.enabled == true) onNodeUsed?.invoke(it, MatchNodeRole.Root)
            }

    private fun getCacheChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? {
        if (index !in 0 until node.childCount) {
            return null
        }
        val cached = childCache[node to index].notExpiredNode
        metrics?.cache(NodeCacheKind.Child, cached != null)
        return cached
            ?: (getA11Child(node, index)
                    ?: throw MatchQueryIncomplete(MatchQueryIncompleteReason.ChildUnavailable))
                .also { child ->
                    indexCache[child] = index
                    parentCache[child] = node
                    childCache[node to index] = child
                }
    }

    private fun getPureIndex(node: AccessibilityNodeInfo): Int? {
        return indexCache[node].also { metrics?.cache(NodeCacheKind.Index, it != null) }
    }

    private fun getCacheIndex(node: AccessibilityNodeInfo): Int {
        getPureIndex(node)?.let {
            return it
        }
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
            repeat(node.childCount) { index ->
                if (index == MAX_CHILD_SIZE)
                    throw MatchQueryIncomplete(MatchQueryIncompleteReason.ChildLimit)
                val child =
                    getCacheChild(node, index)
                        ?: throw MatchQueryIncomplete(MatchQueryIncompleteReason.ChildUnavailable)
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

    private fun getCacheAttr(node: AccessibilityNodeInfo, name: String): Any? {
        metrics?.attribute()
        return when (name) {
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
    }

    private inline fun <T> measure(
        operation: NodeReadOperation,
        args: () -> List<Any> = { emptyList() },
        noinline block: () -> T,
    ): T {
        val trace = diagnostics
        val runId = queryRunId
        val rule = if (trace?.enabled == true) currentRule?.matchIdentity() ?: "manual" else null
        val page = queryPage
        val collector = metrics
        val observation = MatchNodeObservation.current()
        val arguments =
            if (trace?.enabled == true)
                args().map { value ->
                    observation?.value?.invoke(value)
                        ?: when (value) {
                            is Int ->
                                MatchRecordedValue(MatchRecordedValueType.Int, value.toString())
                            is String -> MatchRecordedValue(MatchRecordedValueType.String, value)
                            else ->
                                MatchRecordedValue(
                                    MatchRecordedValueType.NodeIdentity,
                                    reference = System.identityHashCode(value),
                                )
                        }
                }
            else emptyList()
        val read =
            if (collector == null) block
            else {
                { collector.measure(operation, block) }
            }
        val result =
            if (nodeReader != null) {
                nodeReader.read(
                    name = operation.name,
                    runId = runId,
                    rule = rule,
                    page = page,
                    arguments = arguments,
                    block = read,
                )
            } else if (trace?.enabled == true)
                trace.observeOperation(
                    operation.name,
                    runId,
                    rule,
                    page,
                    parentOperationId = observation?.operation?.id,
                    arguments = arguments,
                    sessionId = observation?.sessionId,
                    block = read,
                )
            else read()
        guardInterrupt()
        return result
    }

    suspend fun refresh(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val reader = checkNotNull(nodeReader)
        return reader.query {
            // refresh mutates its receiver, so a cancelled read must own a separate node.
            @Suppress("DEPRECATION") val copy = AccessibilityNodeInfo.obtain(node)
            try {
                if (measure(NodeReadOperation.Refresh, { listOf(copy) }) { copy.refresh() })
                    copy.setGeneratedTime()
                else null
            } catch (error: CancellationException) {
                throw error
            } catch (error: InterruptedException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }
    }

    private inner class A11yNodeAdapter : NodeAdapter<AccessibilityNodeInfo>() {
        override fun getAttr(target: Any, name: String): Any? =
            when (target) {
                is AccessibilityNodeInfo -> getCacheAttr(target, name)
                else -> null
            }

        override fun getInvoke(target: Any, name: String, args: List<Any>): Any? =
            when (target) {
                is AccessibilityNodeInfo ->
                    when (name) {
                        "getChild" -> getCacheChild(target, args.getInt())
                        else -> null
                    }

                else -> null
            }

        override fun getName(node: AccessibilityNodeInfo): String? = node.className?.toString()

        override fun getChildCount(node: AccessibilityNodeInfo): Int = node.childCount

        override fun getChild(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? =
            getCacheChild(node, index)

        override fun getChildren(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> =
            getCacheChildren(node)

        override fun getParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
            getCacheParent(node)

        override fun getNodeKey(node: AccessibilityNodeInfo): Any = node

        override fun getRoot(node: AccessibilityNodeInfo): AccessibilityNodeInfo =
            requireQueryRoot()

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
            }
            .withQueryLimit(MAX_DESCENDANTS_SIZE, MatchQueryIncompleteReason.DescendantLimit)

        override fun traverseChildren(
            node: AccessibilityNodeInfo,
            relationExpression: RelationExpression,
        ): Sequence<TraversalCandidate<AccessibilityNodeInfo>> = sequence {
            repeat(node.childCount) { offset ->
                relationExpression.maxOffset?.let { maxOffset ->
                    if (offset > maxOffset) return@sequence
                }
                if (offset == MAX_CHILD_SIZE)
                    throw MatchQueryIncomplete(MatchQueryIncompleteReason.ChildLimit)
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
            var offset = 0
            for (descendant in getDescendants(node)) {
                if (relationExpression.checkOffset(offset)) {
                    yield(TraversalCandidate(descendant, offset))
                }
                offset++
                if (relationExpression.maxOffset?.let { offset > it } == true) return@sequence
            }
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
        diagnostics: MatchDiagnostics? = null,
    ): AccessibilityNodeInfo? {
        if (diagnostics != null) {
            val session = if (diagnostics.enabled) diagnostics.state.value else null
            if (session?.recordSystem == true && diagnostics.shouldRecordQuery(session.id)) {
                return recordManualQuery(
                    selector,
                    options,
                    node,
                    ::requireQueryRoot,
                    adapter,
                    diagnostics,
                    session,
                    queryRunId,
                )
            }
        }
        return querySingleSelector(
            node,
            selector,
            ::requireQueryRoot,
            adapter,
            options,
            rootOnlySelectors = true,
        )
    }

    fun queryRule(
        rule: ResolvedRule,
        node: AccessibilityNodeInfo,
        diagnostics: MatchDiagnostics,
    ): AccessibilityNodeInfo? {
        currentRule = rule
        try {
            if (diagnostics.enabled) onNodeUsed?.invoke(node, MatchNodeRole.Source)
            val session = if (diagnostics.enabled) diagnostics.state.value else null
            if (
                session?.recordQueries == true &&
                    (session.recordSystem || session.ruleFilter == rule.matchIdentity()) &&
                    diagnostics.shouldRecordQuery(session.id)
            ) {
                return recordRuleQuery(
                    rule,
                    node,
                    ::requireQueryRoot,
                    adapter,
                    diagnostics,
                    session,
                    queryRunId,
                    rootOnlySelectors = true,
                )
            }
            if (
                session != null &&
                    (session.ruleFilter == null || session.ruleFilter == rule.matchIdentity())
            ) {
                val observer =
                    RuleSelectorDiagnostics<AccessibilityNodeInfo>(
                        diagnostics,
                        session.id,
                        queryRunId,
                        rule.matchIdentity(),
                        captureConditions = session.detailed,
                    )
                return matchRuleSelectorsExplained(
                    rule,
                    node,
                    ::requireQueryRoot,
                    { target, selector ->
                        querySingleSelector(
                            target,
                            selector,
                            ::requireQueryRoot,
                            adapter,
                            rule.matchOptions,
                            rootOnlySelectors = true,
                            observer,
                        )
                    },
                    observer,
                )
            }
            return matchRuleSelectors(
                rule,
                node,
                ::requireQueryRoot,
                { target, selector -> querySelfOrSelector(target, selector, rule.matchOptions) },
            )
        } finally {
            currentRule = null
        }
    }
}
