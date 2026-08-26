package li.gkd.selector.property

import li.gkd.selector.QueryContext
import li.gkd.selector.Stringify
import li.gkd.selector.Transform
import kotlin.js.JsExport

@JsExport
sealed class LogicalOperator(val key: String) : Stringify, ExpressionToken{
    override fun stringify() = key

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubClasses by lazy {
            listOf(
                AndOperator, OrOperator
            ).sortedBy { -it.key.length }
        }
    }

    abstract fun <T> compare(
        context: QueryContext<T>,
        transform: Transform<T>,
        left: Expression,
        right: Expression,
    ): Boolean

    data object AndOperator : LogicalOperator("&&") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            left: Expression,
            right: Expression,
        ): Boolean {
            return left.match(context, transform) && right.match(
                context,
                transform
            )
        }
    }

    data object OrOperator : LogicalOperator("||") {
        override fun <T> compare(
            context: QueryContext<T>,
            transform: Transform<T>,
            left: Expression,
            right: Expression,
        ): Boolean {
            return left.match(context, transform) || right.match(
                context,
                transform
            )
        }
    }
}

