package li.songe.selector.unit

import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.QueryResult
import li.songe.selector.Stringify
import li.songe.selector.Transform
import kotlin.js.JsExport

@JsExport
sealed class SelectorLogicalOperator(val key: String) : Stringify, SelectorExpressionToken {
    override fun stringify() = key

    companion object {
        val allSubClasses by lazy {
            listOf(
                AndOperator, OrOperator
            ).sortedBy { -it.key.length }
        }
    }

    abstract fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
        expression: LogicalSelectorExpression,
    ): T?

    abstract fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
        expression: LogicalSelectorExpression,
    ): QueryResult<T>

    data object AndOperator : SelectorLogicalOperator("&&") {
        override fun <T> match(
            context: QueryContext<T>,
            transform: Transform<T>,
            option: MatchOption,
            expression: LogicalSelectorExpression,
        ): T? {
            val leftValue = expression.left.match(context, transform, option)
            if (leftValue == null) {
                return null
            }
            return expression.right.match(
                context,
                transform,
                option
            )
        }

        override fun <T> matchContext(
            context: QueryContext<T>,
            transform: Transform<T>,
            option: MatchOption,
            expression: LogicalSelectorExpression,
        ): QueryResult<T> {
            val leftValue = expression.left.matchContext(context, transform, option)
            if (!leftValue.matched) {
                return QueryResult.AndResult(expression, leftValue)
            }
            return QueryResult.AndResult(
                expression,
                leftValue,
                expression.right.matchContext(
                    context,
                    transform,
                    option
                )
            )
        }
    }

    data object OrOperator : SelectorLogicalOperator("||") {
        override fun <T> match(
            context: QueryContext<T>,
            transform: Transform<T>,
            option: MatchOption,
            expression: LogicalSelectorExpression,
        ): T? {
            val leftValue = expression.left.match(context, transform, option)
            if (leftValue != null) {
                return leftValue
            }
            return expression.right.match(
                context,
                transform,
                option
            )
        }

        override fun <T> matchContext(
            context: QueryContext<T>,
            transform: Transform<T>,
            option: MatchOption,
            expression: LogicalSelectorExpression,
        ): QueryResult<T> {
            val leftValue = expression.left.matchContext(context, transform, option)
            if (leftValue.context.matched) {
                return QueryResult.OrResult(expression, leftValue)
            }
            return QueryResult.OrResult(
                expression,
                leftValue,
                expression.right.matchContext(
                    context,
                    transform,
                    option
                )
            )
        }
    }
}
