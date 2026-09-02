package li.gkd.selector.relation

import li.gkd.selector.MatchContext
import li.gkd.selector.SelectorRelationKind
import li.gkd.selector.NodeAdapter
import li.gkd.selector.TraversalCandidate

internal sealed class RelationOperator(
    val key: String,
    val kind: SelectorRelationKind,
) {
    fun formatOffset(offset: Int): String {
        val n = offset + 1
        return if (n == 1) {
            key
        } else {
            key + n
        }
    }

    abstract fun <T : Any> traversal(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        relationExpression: RelationExpression
    ): Sequence<TraversalCandidate<T>>

    companion object {
        val parseOrder: List<RelationOperator> by lazy {
            listOf(
                BeforeSibling,
                AfterSibling,
                Ancestor,
                Child,
                Descendant,
                Previous,
            ).sortedByDescending { it.key.length }
        }
    }

    /**
     * A + B, 1,2,3,A,B,7,8
     */
    data object BeforeSibling : RelationOperator(
        "+",
        SelectorRelationKind.BeforeSibling,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = adapter.traversePreviousSiblings(context.current, relationExpression)

    }

    /**
     * A - B, 1,2,3,B,A,7,8
     */
    data object AfterSibling : RelationOperator(
        "-",
        SelectorRelationKind.AfterSibling,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = adapter.traverseFollowingSiblings(context.current, relationExpression)
    }

    /**
     * A > B, A is the ancestor of B
     */
    data object Ancestor : RelationOperator(
        ">",
        SelectorRelationKind.Ancestor,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = adapter.traverseAncestors(context.current, relationExpression)

    }

    /**
     * A < B, A is the child of B
     */
    data object Child : RelationOperator(
        "<",
        SelectorRelationKind.Child,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = adapter.traverseChildren(context.current, relationExpression)
    }

    /**
     * A << B, A is the descendant of B
     */
    data object Descendant : RelationOperator(
        "<<",
        SelectorRelationKind.Descendant,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = adapter.traverseDescendants(context.current, relationExpression)
    }

    /**
     * A -> B + C, A is the context previous node of B, A==C
     * A ->2 B + C + D, A==D
     */
    data object Previous : RelationOperator(
        "->",
        SelectorRelationKind.Previous,
    ) {
        override fun <T : Any> traversal(
            context: MatchContext<T>, adapter: NodeAdapter<T>, relationExpression: RelationExpression
        ) = sequence {
            var prev = context.getPrev(relationExpression.minOffset)
            var offset = relationExpression.minOffset
            while (prev != null) {
                if (relationExpression.checkOffset(offset)) {
                    yield(TraversalCandidate(prev.current, offset))
                }
                prev = prev.prev
                offset++
                if (relationExpression.maxOffset?.let { offset > it } == true) {
                    break
                }
            }
        }
    }

}
