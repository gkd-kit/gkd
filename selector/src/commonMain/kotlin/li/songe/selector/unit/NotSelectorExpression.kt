package li.songe.selector.unit

import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.QueryResult
import li.songe.selector.Transform
import li.songe.selector.TypeInfo
import li.songe.selector.connect.ConnectSegment
import li.songe.selector.property.BinaryExpression
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
