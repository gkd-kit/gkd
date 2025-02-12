package li.songe.selector.unit

import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Transform
import li.songe.selector.TypeInfo
import kotlin.js.JsExport

@JsExport
data class LogicalSelectorExpression(
    val left: SelectorExpression,
    val operator: SelectorLogicalOperator,
    val right: SelectorExpression,
) : SelectorExpression() {
    override fun stringify(): String {
        val leftStr = if (
            left is UnitSelectorExpression || (left is LogicalSelectorExpression && left.operator != operator)
        ) {
            "(${left.stringify()})"
        } else {
            left.stringify()
        }
        val rightStr = if (
            right is UnitSelectorExpression || (right is LogicalSelectorExpression && right.operator != operator)
        ) {
            "(${right.stringify()})"
        } else {
            right.stringify()
        }
        return "$leftStr ${operator.stringify()} $rightStr"
    }

    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): T? {
        return operator.match(context, transform, option, this)
    }

    override fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ) = operator.matchContext(context, transform, option, this)

    override fun isSlow(
        matchOption: MatchOption
    ) = left.isSlow(matchOption) || right.isSlow(matchOption)

    override fun checkType(typeInfo: TypeInfo) {
        left.checkType(typeInfo)
        right.checkType(typeInfo)
    }

    override val isMatchRoot: Boolean
        get() = left.isMatchRoot && operator == SelectorLogicalOperator.AndOperator

    override val fastQueryList: List<FastQuery>
        get() = left.fastQueryList + right.fastQueryList
}