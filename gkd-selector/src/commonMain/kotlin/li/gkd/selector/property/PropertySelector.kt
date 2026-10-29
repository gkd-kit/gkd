package li.gkd.selector.property

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter
import li.gkd.selector.SelectorEvaluation
import li.gkd.selector.SelectorEvaluationKind
import li.gkd.selector.SelectorObserver
import li.gkd.selector.SourceMapped
import li.gkd.selector.syntax.SelectorPrinter

internal class PropertySelector(
    val at: Boolean,
    val name: String,
    val filters: List<PropertyExpression>,
) : SourceMapped {
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
        return matchName(context.current, adapter) && filters.all { it.evaluate(context, adapter) }
    }

    fun <T : Any> matchExplained(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
        observer: SelectorObserver<T>,
        unit: Int,
        property: Int,
    ): Boolean {
        if (!observer.enabled) return match(context, adapter)
        if (!matchAnyName) {
            val actual = adapter.getName(context.current)
            val matched =
                actual != null &&
                    (actual.contentEquals(name) ||
                        actual.length > name.length &&
                            actual[actual.length - name.length - 1] == '.' &&
                            actual.endsWith(name))
            observer.record(
                SelectorEvaluation(
                    node = context.current,
                    unit = unit,
                    property = property,
                    kind = SelectorEvaluationKind.Name,
                    expression = name,
                    matched = matched,
                    left = actual,
                    right = name,
                    operator = "class_suffix",
                ),
            )
            if (!matched) {
                if (filters.isNotEmpty() && observer.enabled)
                    observer.record(
                        SelectorEvaluation(
                            node = context.current,
                            unit = unit,
                            property = property,
                            kind = SelectorEvaluationKind.Skipped,
                            expression = "filters after name mismatch",
                            matched = null,
                        ),
                    )
                return false
            }
        }
        for ((index, filter) in filters.withIndex()) {
            val matched =
                if (!observer.enabled) filter.evaluate(context, adapter)
                else
                    filter.evaluateUsing(
                        { comparison ->
                            if (!observer.enabled)
                                return@evaluateUsing comparison.match(context, adapter)
                            val left = comparison.left.evaluate(context, adapter)
                            val right = comparison.right.evaluate(context, adapter)
                            val result = comparison.compareEvaluated(left, right)
                            if (observer.enabled)
                                observer.record(
                                    SelectorEvaluation(
                                        node = context.current,
                                        unit = unit,
                                        property = property,
                                        kind = SelectorEvaluationKind.Comparison,
                                        expression = SelectorPrinter.render(comparison),
                                        matched = result,
                                        left = left,
                                        right = right,
                                        operator = comparison.operator.key,
                                    ),
                                )
                            result
                        },
                        { expression, result, skipped ->
                            if (observer.enabled)
                                observer.record(
                                    SelectorEvaluation(
                                        node = context.current,
                                        unit = unit,
                                        property = property,
                                        kind = SelectorEvaluationKind.Logical,
                                        expression =
                                            if (expression is LogicalExpression)
                                                expression.operator.key
                                            else "!",
                                        matched = result,
                                    ),
                                )
                            if (skipped != null && observer.enabled)
                                observer.record(
                                    SelectorEvaluation(
                                        node = context.current,
                                        unit = unit,
                                        property = property,
                                        kind = SelectorEvaluationKind.Skipped,
                                        expression =
                                            "${(expression as LogicalExpression).operator.key}: right operand skipped",
                                        matched = null,
                                    ),
                                )
                        },
                    )
            if (!matched) {
                if (index < filters.lastIndex && observer.enabled)
                    observer.record(
                        SelectorEvaluation(
                            node = context.current,
                            unit = unit,
                            property = property,
                            kind = SelectorEvaluationKind.Skipped,
                            expression =
                                "filters ${index + 1}..${filters.lastIndex} after failed filter",
                            matched = null,
                        ),
                    )
                return false
            }
        }
        return true
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
        "text" ->
            (e.operator as? FastQueryOperator)?.let { operator ->
                FastQuery.Text(e.right.value, operator)
            }

        else -> null
    }
}
