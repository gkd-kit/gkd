package li.gkd.selector

import li.gkd.selector.relation.RelationExpression

/** A relation traversal result and its zero-based offset in the unfiltered traversal order. */
public data class TraversalCandidate<T : Any>(
    val node: T,
    val offset: Int,
)

internal fun <T : Any> NodeAdapter<T>.getFastQueryDescendantsExcludingSelf(
    node: T,
    fastQueryList: List<FastQuery>,
): Sequence<T> = sequence {
    val sourceKey = getNodeKey(node)
    for (candidate in getFastQueryDescendants(node, fastQueryList)) {
        if (getNodeKey(candidate) != sourceKey) yield(candidate)
    }
}

/**
 * Adapts a platform node tree to selector matching. Implement by inheritance on Kotlin and JS.
 *
 * Every matching operation observes one stable node-tree snapshot. From the start to the end of a
 * call to `Selector.match`, `Selector.matchWithTrace`, or a query helper on this adapter, all
 * adapter methods must return deterministic names, attributes, invocations, relationships, and
 * traversal order for the same arguments. State changes become visible to the next operation.
 * Relation traversal overrides must retain each candidate's original zero-based offset even when
 * an earlier allowed position has no node.
 */
public abstract class NodeAdapter<T : Any> {
    public abstract fun getAttr(target: Any, name: String): Any?

    public open fun getInvoke(target: Any, name: String, args: List<Any>): Any? = null

    public abstract fun getName(node: T): String?

    public abstract fun getChildCount(node: T): Int

    public abstract fun getChild(node: T, index: Int): T?

    public abstract fun getParent(node: T): T?

    /**
     * Returns the equality/hash key for one logical node.
     *
     * Equal keys must identify the same logical node, distinct logical nodes must have unequal
     * keys, and key equality and hash codes must remain stable during one matching operation.
     */
    public abstract fun getNodeKey(node: T): Any

    /** Returns this node's tree root, or `null` when its parent chain contains a cycle. */
    public open fun getRoot(node: T): T? {
        val visitedKeys = mutableSetOf<Any>()
        var current = node
        while (visitedKeys.add(getNodeKey(current))) {
            current = getParent(current) ?: return current
        }
        return null
    }

    public open fun getChildren(node: T): Sequence<T> = sequence {
        repeat(getChildCount(node)) { index ->
            getChild(node, index)?.let { yield(it) }
        }
    }

    public open fun getDescendants(node: T): Sequence<T> = sequence {
        val visitedKeys = mutableSetOf(getNodeKey(node))
        val stack = getChildren(node).toMutableList()
        stack.reverse()
        while (stack.isNotEmpty()) {
            val top = stack.removeAt(stack.lastIndex)
            if (!visitedKeys.add(getNodeKey(top))) continue
            yield(top)
            val children = getChildren(top).toMutableList()
            for (index in children.lastIndex downTo 0) {
                stack.add(children[index])
            }
        }
    }

    public open fun traverseChildren(
        node: T,
        relationExpression: RelationExpression,
    ): Sequence<TraversalCandidate<T>> = sequence {
        val childCount = getChildCount(node)
        for (offset in 0 until childCount) {
            if (relationExpression.maxOffset?.let { offset > it } == true) return@sequence
            if (relationExpression.checkOffset(offset)) {
                getChild(node, offset)?.let { child ->
                    yield(TraversalCandidate(child, offset))
                }
            }
        }
    }

    public open fun traverseAncestors(
        node: T,
        relationExpression: RelationExpression,
    ): Sequence<TraversalCandidate<T>> = sequence {
        val visitedKeys = mutableSetOf(getNodeKey(node))
        var parent = getParent(node) ?: return@sequence
        var offset = 0
        while (true) {
            if (!visitedKeys.add(getNodeKey(parent))) return@sequence
            if (relationExpression.checkOffset(offset)) {
                yield(TraversalCandidate(parent, offset))
            }
            offset++
            if (relationExpression.maxOffset?.let { offset > it } == true) return@sequence
            parent = getParent(parent) ?: return@sequence
        }
    }

    public open fun traversePreviousSiblings(
        node: T,
        relationExpression: RelationExpression,
    ): Sequence<TraversalCandidate<T>> = sequence {
        val parent = getParent(node) ?: return@sequence
        val nodeKey = getNodeKey(node)
        val childCount = getChildCount(parent)
        val nodeIndex = (0 until childCount).firstOrNull { index ->
            getChild(parent, index)?.let { getNodeKey(it) == nodeKey } == true
        } ?: -1
        if (nodeIndex < 0) return@sequence
        for (offset in 0 until nodeIndex) {
            if (relationExpression.maxOffset?.let { offset > it } == true) break
            if (relationExpression.checkOffset(offset)) {
                getChild(parent, nodeIndex - offset - 1)?.let { sibling ->
                    yield(TraversalCandidate(sibling, offset))
                }
            }
        }
    }

    public open fun traverseFollowingSiblings(
        node: T,
        relationExpression: RelationExpression,
    ): Sequence<TraversalCandidate<T>> = sequence {
        val parent = getParent(node) ?: return@sequence
        val nodeKey = getNodeKey(node)
        val childCount = getChildCount(parent)
        val nodeIndex = (0 until childCount).firstOrNull { index ->
            getChild(parent, index)?.let { getNodeKey(it) == nodeKey } == true
        } ?: -1
        if (nodeIndex < 0) return@sequence
        for (index in nodeIndex + 1 until childCount) {
            val offset = index - nodeIndex - 1
            if (relationExpression.maxOffset?.let { offset > it } == true) break
            if (relationExpression.checkOffset(offset)) {
                getChild(parent, index)?.let { sibling ->
                    yield(TraversalCandidate(sibling, offset))
                }
            }
        }
    }

    public open fun traverseDescendants(
        node: T,
        relationExpression: RelationExpression,
    ): Sequence<TraversalCandidate<T>> = sequence {
        var offset = 0
        for (descendant in getDescendants(node)) {
            if (relationExpression.checkOffset(offset)) {
                yield(TraversalCandidate(descendant, offset))
            }
            offset++
            if (relationExpression.maxOffset?.let { offset > it } == true) return@sequence
        }
    }

    /**
     * Returns descendants matching at least one [fastQueryList] entry.
     *
     * Overrides must not omit a matching descendant and must return each logical node at most once
     * according to [getNodeKey]. The source [node] is not its own descendant and must not be
     * returned. Implementations may use an implementation-specific order, so enabling fast queries
     * may change query result order and the first matching node compared with [getDescendants].
     * Callers requiring depth-first order must disable fast queries.
     *
     * Implementations should defer work until iteration and yield candidates incrementally so
     * first-result query helpers can stop before later fast queries run. Selector matching validates
     * every returned candidate again.
     */
    public open fun getFastQueryDescendants(
        node: T,
        fastQueryList: List<FastQuery>,
    ): Sequence<T> = getDescendants(node).filter { candidate ->
        fastQueryList.any { query ->
            query.acceptValue(getAttr(candidate, query.attributeName))
        }
    }

    private fun queryDescendants(
        node: T,
        selector: Selector,
        options: MatchOptions,
    ): Sequence<T> = if (options.fastQuery && selector.fastQueryList.isNotEmpty()) {
        getFastQueryDescendantsExcludingSelf(node, selector.fastQueryList)
    } else {
        getDescendants(node)
    }

    private fun querySelectorSequence(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): Sequence<T> = sequence {
        queryDescendants(node, selector, options).forEach { childNode ->
            selector.match(childNode, this@NodeAdapter, options)?.let { target ->
                yield(target)
            }
        }
    }

    public fun querySelector(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): T? = querySelectorSequence(node, selector, options).firstOrNull()

    public fun querySelectorAll(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): List<T> = querySelectorSequence(node, selector, options)
        .distinctBy(::getNodeKey)
        .toList()

    private fun querySelectorWithTraceSequence(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): Sequence<SelectorMatch<T>> = sequence {
        queryDescendants(node, selector, options).forEach { childNode ->
            selector.matchWithTrace(childNode, this@NodeAdapter, options)?.let { result ->
                yield(result)
            }
        }
    }

    public fun querySelectorWithTrace(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): SelectorMatch<T>? = querySelectorWithTraceSequence(node, selector, options).firstOrNull()

    public fun querySelectorAllWithTrace(
        node: T,
        selector: Selector,
        options: MatchOptions = MatchOptions.default,
    ): List<SelectorMatch<T>> = querySelectorWithTraceSequence(node, selector, options)
        .distinctBy { result -> getNodeKey(result.target) }
        .toList()
}
