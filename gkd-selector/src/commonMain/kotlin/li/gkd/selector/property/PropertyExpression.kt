package li.gkd.selector.property

import li.gkd.selector.LogicalOperator
import li.gkd.selector.MatchContext
import li.gkd.selector.NodeAdapter
import li.gkd.selector.SourceMapped

internal sealed class PropertyExpression : SourceMapped

internal sealed class ComparisonExpression(
    val left: ValueExpression,
    val operator: CompareOperator,
    val right: ValueExpression,
) : PropertyExpression() {
    abstract fun compareEvaluated(left: Any?, right: Any?): Boolean

    abstract fun <T : Any> match(
        context: MatchContext<T>,
        adapter: NodeAdapter<T>,
    ): Boolean

    class ValueComparison(
        left: ValueExpression,
        private val valueOperator: CompareOperator.ValueOperator,
        right: ValueExpression,
    ) : ComparisonExpression(left, valueOperator, right) {
        override fun compareEvaluated(left: Any?, right: Any?): Boolean =
            valueOperator.compareValue(left, right)

        override fun <T : Any> match(
            context: MatchContext<T>,
            adapter: NodeAdapter<T>,
        ): Boolean =
            valueOperator.compareValue(
                left = left.evaluate(context, adapter),
                right = right.evaluate(context, adapter),
            )
    }

    class RegexComparison(
        left: ValueExpression,
        private val regexOperator: CompareOperator.RegexOperator,
        right: ValueExpression.StringLiteral,
        private val matches: (CharSequence) -> Boolean,
    ) :
        ComparisonExpression(
            left = left,
            operator = regexOperator,
            right = right,
        ) {
        override fun compareEvaluated(left: Any?, right: Any?): Boolean =
            left is CharSequence && matches(left) == regexOperator.expectedMatch

        override fun <T : Any> match(
            context: MatchContext<T>,
            adapter: NodeAdapter<T>,
        ): Boolean {
            val value = left.evaluate(context, adapter)
            return value is CharSequence && matches(value) == regexOperator.expectedMatch
        }
    }
}

internal class LogicalExpression(
    val left: PropertyExpression,
    val operator: LogicalOperator,
    val right: PropertyExpression,
) : PropertyExpression()

internal class NotExpression(
    val expression: PropertyExpression,
) : PropertyExpression()
