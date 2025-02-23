package li.songe.selector.property

import li.songe.selector.MatchOption
import li.songe.selector.QueryContext
import li.songe.selector.Stringify
import li.songe.selector.Transform
import li.songe.selector.connect.ConnectWrapper
import kotlin.js.JsExport

@JsExport
data class PropertyWrapper(
    val segment: PropertySegment,
    val to: ConnectWrapper? = null,
) : Stringify {
    override fun stringify(): String {
        return (if (to != null) {
            to.stringify() + "\u0020"
        } else {
            ""
        }) + segment.stringify()
    }

    fun <T> matchContext(
        context: QueryContext<T>,
        transform: Transform<T>,
        option: MatchOption,
    ): QueryContext<T> {
        if (!segment.match(context, transform)) {
            return context.mismatch()
        }
        if (to == null) {
            return context
        }
        return to.matchContext(context, transform, option)
    }

    val isMatchRoot = segment.units.any {
        val e = it.expression
        e is BinaryExpression && e.operator == CompareOperator.Equal && e.left.value == "parent" && e.right.value == "null"
    }

    val fastQueryList by lazy { segment.fastQueryList ?: emptyList() }

    val length: Int
        get() = if (to == null) 1 else to.to.length + 1
}
