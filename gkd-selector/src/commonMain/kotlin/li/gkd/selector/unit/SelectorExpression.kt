package li.gkd.selector.unit

import li.gkd.selector.FastQuery
import li.gkd.selector.MatchOption
import li.gkd.selector.QueryContext
import li.gkd.selector.QueryResult
import li.gkd.selector.Stringify
import li.gkd.selector.Transform
import li.gkd.selector.TypeInfo
import li.gkd.selector.connect.ConnectSegment
import li.gkd.selector.property.BinaryExpression
import kotlin.js.JsExport

internal sealed interface SelectorExpressionToken

@JsExport
sealed class SelectorExpression : Stringify, SelectorExpressionToken {

    abstract fun <T> match(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption
    ): T?

    abstract fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
    ): QueryResult<T>

    abstract fun isSlow(matchOption: MatchOption): Boolean
    abstract fun checkType(typeInfo: TypeInfo)
    abstract val isMatchRoot: Boolean
    abstract val fastQueryList: List<FastQuery>
    abstract val binaryExpressionList: List<BinaryExpression>
    abstract val connectSegmentList: List<ConnectSegment>
}
