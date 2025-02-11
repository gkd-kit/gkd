package li.songe.selector

import li.songe.selector.parser.SelectorParser
import li.songe.selector.unit.SelectorExpression
import kotlin.js.JsExport

@JsExport
class Selector(
    internal val expression: SelectorExpression,
) {
    override fun toString(): String {
        return expression.stringify()
    }

    fun <T> matchContext(
        node: T,
        transform: Transform<T>,
        option: MatchOption,
    ): QueryResult<T> {
        return expression.matchContext(QueryContext(node), transform, option)
    }

    fun <T> match(
        node: T,
        transform: Transform<T>,
        option: MatchOption,
    ): T? {
        return expression.match(QueryContext(node), transform, option)
    }

    val fastQueryList = expression.fastQueryList.distinct()
    val isMatchRoot = expression.isMatchRoot

    fun isSlow(matchOption: MatchOption) = expression.isSlow(matchOption)
    fun checkType(typeInfo: TypeInfo) = expression.checkType(typeInfo)

    companion object {
        fun parse(source: String) = SelectorParser(source).readSelector()
        fun parseOrNull(source: String) = try {
            SelectorParser(source).readSelector()
        } catch (_: Exception) {
            null
        }
    }
}
