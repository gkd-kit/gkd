package li.gkd.selector.relation

import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter
import li.gkd.selector.TraversalCandidate
internal class RelationSelector(
    val operator: RelationOperator = RelationOperator.Ancestor,
    val relationExpression: RelationExpression = PolynomialExpression(),
) {
    fun <T : Any> traversal(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
    ): Sequence<TraversalCandidate<T>> {
        return operator.traversal(context, adapter, relationExpression)
    }

    val isMatchAnyAncestor =
        operator == RelationOperator.Ancestor && relationExpression.matchesAllOffsets

    val isMatchAnyDescendant =
        operator == RelationOperator.Descendant && relationExpression.matchesAllOffsets
}
