package li.gkd.selector.property

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter

internal class PropertySelector(
    val at: Boolean,
    val name: String,
    val filters: List<PropertyExpression>,
) {
    private val matchAnyName = name.isEmpty() || name == "*"

    val comparisonExpressions: List<ComparisonExpression> by lazy {
        filters.flatMap(PropertyExpression::collectBinaryExpressions)
    }

    private fun <T : Any> matchName(node: T, adapter: NodeAdapter<T>): Boolean {
        if (matchAnyName) return true
        val str = adapter.getName(node) ?: return false
        if (str.length == name.length) {
            return str.contentEquals(name)
        } else if (str.length > name.length) {
            return str[str.length - name.length - 1] == '.' && str.endsWith(name)
        }
        return false
    }

    fun <T : Any> match(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
    ): Boolean {
        return matchName(context.current, adapter) &&
                filters.all { it.evaluate(context, adapter) }
    }

    val fastQueryList: List<FastQuery>? by lazy {
        when (val expression = filters.firstOrNull()) {
            is ComparisonExpression -> expToFastQuery(expression)?.let(::listOf)
            is LogicalExpression -> {
                val expressions = expression.collectOrFastQueryExpressions() ?: return@lazy null
                expressions.map { expToFastQuery(it) ?: return@lazy null }
            }

            else -> null
        }
    }

    val usesPreviousContext: Boolean = filters.any(PropertyExpression::usesPreviousContext)

    val isMatchRoot: Boolean = filters.any { expression ->
        expression is ComparisonExpression &&
                expression.operator == CompareOperator.Equal &&
                ((expression.left is ValueExpression.Identifier &&
                        expression.left.name == "parent" &&
                        expression.right is ValueExpression.NullLiteral) ||
                        (expression.right is ValueExpression.Identifier &&
                                expression.right.name == "parent" &&
                                expression.left is ValueExpression.NullLiteral))
    }
}

private fun expToFastQuery(e: ComparisonExpression): FastQuery? {
    if (e.left !is ValueExpression.Identifier) return null
    if (e.right !is ValueExpression.StringLiteral) return null
    if (e.right.value.isEmpty()) return null
    return when (e.left.name) {
        "id" -> if (e.operator == CompareOperator.Equal) FastQuery.Id(e.right.value) else null
        "vid" -> if (e.operator == CompareOperator.Equal) FastQuery.Vid(e.right.value) else null
        "text" -> (e.operator as? FastQueryOperator)?.let { operator ->
            FastQuery.Text(e.right.value, operator)
        }

        else -> null
    }
}
