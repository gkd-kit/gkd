package li.songe.selector.unit

import li.songe.selector.FastQuery
import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.QueryResult
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.TypeInfo
import li.songe.selector.connect.ConnectSegment
import li.songe.selector.property.BinaryExpression
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
