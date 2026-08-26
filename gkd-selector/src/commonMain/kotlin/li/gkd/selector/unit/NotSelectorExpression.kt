package li.gkd.selector.unit

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOption
import li.gkd.selector.QueryContext
import li.gkd.selector.QueryResult
import li.gkd.selector.Transform
import li.gkd.selector.TypeInfo
import li.gkd.selector.connect.ConnectSegment
import li.gkd.selector.property.BinaryExpression
import kotlin.js.JsExport

@JsExport
data class NotSelectorExpression(
    val expression: SelectorExpression,
) : SelectorExpression() {
    override fun stringify(): String {
        return "!(${expression.stringify()})"
    }

    override fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): T? {
        val r = expression.match(context, transform, option)
        if (r != null) {
            return null
        }
        return context.current
    }

    override fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): QueryResult<T> {
        return QueryResult.NotResult(
            this,
            context,
            expression.matchContext(context, transform, option)
        )
    }

    override fun isSlow(
        matchOption: MatchOption
    ) = expression.isSlow(matchOption)

    override fun checkType(typeInfo: TypeInfo) = expression.checkType(typeInfo)

    override val isMatchRoot: Boolean
        get() = false

    override val fastQueryList: List<FastQuery>
        get() = emptyList()

    override val binaryExpressionList: List<BinaryExpression>
        get() = expression.binaryExpressionList

    override val connectSegmentList: List<ConnectSegment>
        get() = expression.connectSegmentList
}
